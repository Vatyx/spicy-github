(ns spicy-github.scraper
    (:gen-class)
    (:require [clojure.java.io :as io]
              [spicy-github.db :as db]
              [spicy-github.model :as model]
              [spicy-github.util :refer :all]
              [spicy-github.adapters :as adapters]
              [malli.dev.pretty]
              [clj-http.client :as client]
              [cheshire.core :refer :all]
              [clojure.edn :as edn]
              [hiccup.util]
              [gungnir.model]
              [gungnir.query :as q]
              [gungnir.transaction :as transaction]
              [clj-time.core :as t]
              [honey.sql.helpers :as h]
              [net.cgrand.xforms :as x]
              [taoensso.timbre :as timbre]
              [throttler.core :refer [throttle-fn]]))

(def new-config (assoc timbre/*config* :middleware
                                       [(fn [data]
                                            (update data :vargs (partial mapv #(if (string? %)
                                                                                   %
                                                                                   (with-out-str (clojure.pprint/pprint %))))))]))

(defn load-repository-query [] (load-resource "repository-query.graphql"))

(timbre/set-config! new-config)

(defn load-github-tokens []
    (-> (io/resource "token.edn")
        io/file
        slurp
        edn/read-string
        :github-token))

(def get-github-token
    (let [counter (atom 0)
          tokens (load-github-tokens)]
        (fn []
            (swap! counter inc)
            (nth tokens (mod @counter (count tokens))))))

(def request (throttle-fn client/request 4800 :hour))

(defn request-retry [payload retry-count]
    (let [request-fn #(request payload)]
        (if (zero? retry-count)
            (request-fn)
            (try
                (request-fn)
                (catch Exception e
                    (request-retry payload (dec retry-count))
                    )))))

(defn make-request [url & [params]]
    (timbre/debug "Making Request: " url)
    (request-retry (merge
                 {:url url}
                 {:headers {"Authorization" (str "Bearer " (get-github-token))}}
                 {:method :get}
                 params)
                   5))

(defn make-github-graphql-request [query]
    (let [body-payload "{\"query\":\n\"%s\" }"
          newline-sanitized-query (clojure.string/replace query #"\r\n|\n|\r" "")
          quote-escaped-query (clojure.string/escape newline-sanitized-query {\" "\\\""})
          formatted-body (format body-payload quote-escaped-query)]
        (make-request "https://api.github.com/graphql" {:method :post
                                                        :body   formatted-body})))

(defn get-repository-query
    ([] (format (load-repository-query) ""))
    ([end-cursor] (format (load-repository-query) (format "after: \"%s\"" end-cursor))))

(defn paginated-iteration [paginated-url]
    (iteration #(make-request %1)
               :kf #(->> %
                         :links
                         :next
                         :href)
               :vf #(->> %
                         :body
                         parse-json)
               :initk paginated-url
               :somef :body))

(defn paginated-graphql-iteration []
    (iteration #(-> %
                    make-github-graphql-request
                    :body
                    parse-json
                    :data
                    :search)
               :kf #(-> %
                        :pageInfo
                        :endCursor
                        get-repository-query)
               :vf :edges
               :initk (get-repository-query)
               :somef #(-> %
                           :edges
                           not-empty)))

(defn add-url-query [url query]
    (.toString (hiccup.util/url url query)))

(defn parse-then-persist! [parser]
    (execute #(-> %
                  parser
                  db/persist-record!)))

(defn get-last-processed-repository! []
    (-> (h/where [:< :repository/processed-at (java.sql.Date. (inst-ms (t/yesterday)))])
        (h/order-by :repository/processed-at)
        (h/limit 10)
        (q/all! :repository)))

(defn get-issues []
    (q/all! :issue))

(defn get-issues-url-from-repo-model [repo]
    (-> repo
        :repository/github-json-payload
        parse-json
        :issues_url
        sanitize-github-url))

(defn get-comments-url-from-issue [issue]
    (-> issue
        :issue/github-json-payload
        parse-json
        :comments_url))

(defn persist-repo [repo-url]
    (-> repo-url
        make-request
        :body
        parse-json
        adapters/parse-repository
        db/persist-record!))

(defn http-repo-to-api-repo [http-repo-url]
    (clojure.string/replace-first http-repo-url #"github.com" "api.github.com/repos"))

(def catcat (comp cat cat))

(def repository-persisting-pipeline-xf
    (comp
        cat
        (map :node)
        (map :url)
        (map http-repo-to-api-repo)
        (execute #(persist-repo %))))

(def repository-processing-pipeline-xf
    (comp
        (map get-issues-url-from-repo-model)

        ; Without stating "all" we will only get open issues
        (map #(add-url-query % {:state "all"}))

        ; Create a paginated iterator over all issues in this repo
        (map paginated-iteration)

        ; Fully expand the pagination (a list of lists of issues) to issues
        catcat

        ; Save the user of each issue
        (parse-then-persist! adapters/parse-user-from-issue)

        ; Convert to an issue model and persist
        (map #(-> %
                  adapters/parse-issue
                  db/persist-record!))

        ; Get the comments url so we can query it
        (map :issue/comments-url)

        ; Create a paginated iterator over all comments in this issue
        (map paginated-iteration)

        ; nil at the front of the iteration since the very first comment does not have a parent
        (map #(lazy-concat [[[nil]] %]))

        ; Fully expand the pagination (a list of lists of comments) to comments
        catcat

        ; Save the user of each comment
        (parse-then-persist! adapters/parse-user-from-comment)

        ; Group all comments as pairs with their parent comments to associate them
        (x/partition 2 1 (x/into []))

        (map #(-> %
                  adapters/parse-comment-with-parent
                  db/persist-record!))
        ))

(defn process-repository-models [repo-models]
    (transduce repository-processing-pipeline-xf (constantly nil) repo-models))

(defn scrape-repositories []
    (transduce repository-persisting-pipeline-xf (constantly nil) (paginated-graphql-iteration)))

(comment

    (scrape-repositories)

    (require '[flow-storm.api :as fs])

    (fs/local-connect)

    (get-issues)

    (def repo (get-last-processed-repository!))

    (db/register-db!)

    repo

    (def foo (into [] test-xf repo))

    foo

    (gungnir.changeset/create (first foo))

    (def com (assoc (first foo) :comment/parent-comment nil))

    (:changeset/errors (gungnir.database/insert! (gungnir.changeset/create (second foo))))

    (db/persist-record! (first foo))

    comments

    (second comments)

    (count comments)

    (transaction/execute!)

    (defn test-two-param [[foo bar]]
        (println foo bar))

    (test-two-param [1 2])

    (def foo [1, 2, 3, 4, 5])

    (partition 2 1 foo)

    (def lazyfn #(lazy-cat [nil] %))

    (def foo (lazyfn (range 10)))

    (def foo (lazy-cat [nil] (range 8)))

    (sequence (x/partition 2 1 (x/into [])) foo)

    (into [] (x/partition 2 1 (repeat nil) (x/into [])) (range 9))
    )

(comment
    (def repo (get-last-processed-repository!))

    repo

    (def my-map {:foo 1 :bar {:foo 2 :baz 3}})

    (:baz (:bar my-map))

    (-> my-map
        :bar
        :baz)

    (into [] (x/window 2 + -) (range 16))

    (into [] (map inc) (range 16))

    :foo

    repo

    (make-request "https://api.github.com/repos/dakrone/cheshire/issues" {:query-params {"state" "closed"}})

    (parse-json (:body (make-request "https://api.github.com/repositories/1516467/issues?state=all&page=2")))

    (generate-string {:query {}})

    (def viewer-ql "
{
  \"query\": \"query { viewer { login } }\"
}
    ")

    (def repository-query (load-resource "repository-query.graphql"))

    (clojure.string/replace repository-query #"\"" "\\\"")

    repository-query

    (def query-foo (clojure.string/replace (load-resource "repository-query.graphql") #"\r\n|\n|\r" ""))

    query-foo

    (parse-json (:body (make-github-graphql-request (load-resource "repository-query.graphql"))))

    (def first-query (make-request "https://api.github.com/graphql" {:method :post
                                                                     :body   (format "{\"query\":\n\"%s\" }" query-foo)}))

    (parse-json (:body first-query))

    (first (def new-repo-url "https://api.github.com/repos/district0x/graphql-query"))

    (def new-repo-url "https://api.github.com/repos/dakrone/cheshire")

    (persist-repo new-repo-url)

    (process-repository-models repo)

    (add-url-query "https://api.github.com/repos/dakrone/cheshire/issues" {:state "all"})

    (count (process-repository-models repo))

    (process-repository repo)

    (def it (process-repository repo))

    (count (lazy-concat it))

    (first (into [] cat it))

    (def cl (first (into [] (comp cat (map adapters/parse-issue) (take 1)) it)))

    (db/persist-record! cl)

    (transduce (comp
                   cat
                   (passthrough #(-> %1
                                     adapters/parse-user-from-issue
                                     db/persist-record!))
                   (map adapters/parse-issue)
                   (map db/persist-record!))
               (constantly nil)
               it)

    (transduce (comp
                   cat
                   (passthrough #(println %1))
                   (take 1))
               (constantly nil)
               it)

    (into [] (comp cat
                   (passthrough #(println %1))
                   ) [[1], [2], [3], [4]])

    (eduction)

    (partial)

    (run!)

    (eduction (map inc) [1 2 3])

    (eduction (filter even?) (map inc)
              (range 5))

    (def xf (eduction (filter even?) (map inc) (range 100)))

    (class xf)

    (defn process-with-transducers [files]
        (transduce (comp (mapcat parse-json-file-reducible)
                         (filter valid-entry?)
                         (keep transform-entry-if-relevant)
                         (partition-all 1000)
                         (map save-into-database))
                   (constantly nil)
                   nil
                   files))

    (def issues-url (let [{json-str :repository/github-json-payload} repo
                          json (parse-json json-str)
                          issues-url (-> json
                                         :issues_url
                                         sanitize-github-url)]
                        issues-url))

    (make-request issues-url)

    (when-let [repo (not-empty (get-last-processed-repository!))]
        (println repo)
        (println "huh"))

    (def comments-url (-> (get-issues)
                          first
                          ))

    comments-url

    (def comments-url "https://api.github.com/repos/devlooped/moq/issues/1374/comments")

    (def events-url "https://api.github.com/repos/devlooped/moq/issues/1374/events")

    (count (parse-json (:body (make-request comments-url))))

    (make-request "https://api.github.com/users")

    (def comments (make-request comments-url))

    comments

    )