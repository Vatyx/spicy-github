(ns spicy-github.scraper
    (:gen-class)
    (:require [clojure.java.io :as io]
              [spicy-github.db :as db]
              [spicy-github.model]
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

(timbre/set-config! new-config)

(defn load-repository-query [] (load-resource "repository-query.graphql"))

(defn load-github-tokens! []
    (-> (io/resource "token.edn")
        io/file
        slurp
        edn/read-string
        :github-token))

(def get-github-token!
    (let [counter (atom 0)
          tokens (load-github-tokens!)]
        (fn []
            (swap! counter inc)
            (nth tokens (mod @counter (count tokens))))))

(def request (throttle-fn client/request 9500 :hour))

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
    (when (some? params)
        (timbre/debug params))
    (request-retry (merge
                       {:url url}
                       {:headers {"Authorization" (str "Bearer " (get-github-token!))}}
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

        (filter #(some? (get % 1)))

        (map #(-> %
                  adapters/parse-comment-with-parent
                  db/persist-record!))
        ))

(defn process-repository-models [repo-models]
    (transduce repository-processing-pipeline-xf (constantly nil) repo-models))

(defn scrape-repositories []
    (transduce repository-persisting-pipeline-xf (constantly nil) (paginated-graphql-iteration)))
