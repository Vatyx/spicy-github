(ns spicy-github.scraper
    (:gen-class)
    (:require [clojure.java.io :as io]
              [spicy-github.db :as db]
              [spicy-github.model]
              [spicy-github.util :refer :all]
              [spicy-github.adapters :as adapters]
              [clojure.stacktrace]
              [spicy-github.logging :as logging]
              [malli.dev.pretty]
              [clj-http.client :as client]
              [cheshire.core :refer :all]
              [clojure.edn :as edn]
              [hiccup.util]
              [gungnir.model]
              [gungnir.query :as q]
              [clj-time.core :as t]
              [honey.sql.helpers :as h]
              [net.cgrand.xforms :as x]
              [taoensso.timbre :as timbre]
              [throttler.core :refer [throttle-fn]])
    (:import (java.sql Date)))

(defn load-repository-query [] (load-resource "repository-query.graphql"))

(defn load-github-tokens! []
    (-> (io/resource "token.edn")
        slurp
        edn/read-string
        :github-token))

(def get-github-token!
    (let [counter (atom 0)
          tokens (load-github-tokens!)]
        (fn []
            (swap! counter inc)
            (nth tokens (mod @counter (count tokens))))))

(def request (throttle-fn client/request 9300 :hour))

(defn request-retry [payload retry-count]
    (let [request-fn #(request payload)]
        (try
            (request-fn)
            (catch Exception e
                (clojure.stacktrace/print-stack-trace e)
                (timbre/error (str e))
                (if (zero? retry-count)
                    nil
                    (request-retry payload (dec retry-count)))))))

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
    ([min-stars max-stars] (get-repository-query min-stars max-stars ""))
    ([min-stars max-stars end-cursor] (let [end-cursor-string (if (not (empty? end-cursor))
                                                                  (format "after: \"%s\"" end-cursor)
                                                                  "")]
                                          (format (load-repository-query) (str min-stars) (str max-stars) end-cursor-string))))

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

(defn paginated-graphql-iteration [min-stars max-stars]
    (iteration #(-> %
                    make-github-graphql-request
                    :body
                    parse-json
                    :data
                    :search)
               :kf #(->> %
                         :pageInfo
                         :endCursor
                         (get-repository-query min-stars max-stars))
               :vf :edges
               :initk (get-repository-query min-stars max-stars)
               :somef #(-> %
                           :edges
                           not-empty)))

(defn add-url-query [url query]
    (.toString (hiccup.util/url url query)))

(defn parse-then-persist! [parser]
    (execute #(-> %
                  parser
                  db/persist-record-exception-safe!)))

(defn get-oldest-processed-repository! []
    (-> (h/where [:< :repository/processed-at (Date. (inst-ms (-> 7 t/days t/ago)))])
        (h/order-by :repository/processed-at)
        (h/limit 1)
        (q/all! :repository)
        first))

(defn get-last-inserted-repository! []
    (-> (h/order-by [:repository/created-at :desc])
        (h/limit 1)
        (q/all! :repository)
        first))

(defn mark-repository-as-processed! [repository]
    (-> repository
        (assoc :repository/processed-at (Date. (inst-ms (t/now))))
        db/persist-record-exception-safe!))

(defn get-repository-stars [repository]
    (-> repository
        :repository/github-json-payload
        parse-json
        :stargazers_count))

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
    (some-> repo-url
            make-request
            :body
            parse-json
            adapters/parse-repository
            db/persist-record-exception-safe!))

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

        ; Only save issues with 10 or more comments
        (filter #(>= (:comments %) 10))

        ; Save the user of each issue
        (parse-then-persist! adapters/parse-user-from-issue)

        ; Convert to an issue model and persist
        (map #(-> %
                  adapters/parse-issue
                  db/persist-record-exception-safe!))

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
                  db/persist-record-exception-safe!))))

(defn process-repositories [repositories]
    (timbre/debug "Processing repositories: " (->> repositories
                                                   (map :repository/url)
                                                   (clojure.string/join " ")))
    (execute-pipeline repository-processing-pipeline-xf repositories))

(defn process-scraped-repositories []
    (loop [latest-repository (get-oldest-processed-repository!)]
        (if (nil? latest-repository)
            (do
                (timbre/debug "No new repositories to process, waiting...")
                (Thread/sleep (int (rand 5000))))
            (do
                (process-repositories [latest-repository])
                (mark-repository-as-processed! latest-repository)))
        (recur (get-oldest-processed-repository!))))

(defn scrape-repositories [min-stars max-stars]
    (timbre/debug (str "Scraping Repositories with stars between " min-stars " and " max-stars))
    (execute-pipeline repository-persisting-pipeline-xf (paginated-graphql-iteration min-stars max-stars)))

(defn scrape-repositories-with-star-step [starting-star-count ending-star-count star-step]
    (loop [max-stars starting-star-count
           min-stars (- max-stars star-step)]
        (scrape-repositories min-stars max-stars)
        (if (< max-stars ending-star-count)
            nil
            (recur (- max-stars star-step)
                   (- min-stars star-step)))))

(defn scrape-all-repositories []
    (scrape-repositories 100000 400000)
    (scrape-repositories 50000 100000)
    (scrape-repositories-with-star-step 50000 20000 2000)
    (scrape-repositories-with-star-step 10000 1000 1000))


(comment
    ; How to run scraper

    ; Get repo url
    (def repo-url "insert repo url")

    ; Persist it to db
    (persist-repo repo-url)

    ; Fetch the last 10 unprocessed repos
    (def repos-model (get-oldest-processed-repository!))

    ; Process them
    (process-repository-models repo)
    )
