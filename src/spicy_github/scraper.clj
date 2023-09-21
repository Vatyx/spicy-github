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
              [gungnir.model]
              [gungnir.query :as q]
              [clj-time.core :as t]
              [honey.sql.helpers :as h]
              [throttler.core :refer [throttle-fn]]))

(defn get-github-token []
    (-> (io/resource "token.edn")
        io/file
        slurp
        edn/read-string
        :github-token))

(def get-url (throttle-fn client/get 5000 :hour))

(def github-token (get-github-token))

(defn get-github-url [url]
    (get-url url {:headers {"Authorization" (str "Bearer " github-token)}}))

(defn paginated-iteration [paginated-url]
    (iteration #(get-github-url %1)
               :kf #(-> %1 :links :next :href)
               :vf #(parse-json (:body %1))
               :initk paginated-url
               :somef :body))

(defn get-last-processed-repository []
    (-> (h/where [:< :repository/processed-at (java.sql.Date. (inst-ms (t/yesterday)))])
        (h/order-by :repository/processed-at)
        (h/limit 1)
        (q/all! :repository)
        (first)))

(defn process-repositories []
    (forever
        (when-let [repo (get-last-processed-repository)]
            (println "hello")
            (println "huh"))
        (println "hello")))

(defn process-repository [repo]
    (let [issues-iteration (-> repo
                               :repository/github-json-payload
                               parse-json
                               :issues_url
                               sanitize-github-url
                               paginated-iteration)]

        (transduce (comp cat
                         (passthrough #(-> %1
                                           adapters/parse-user-from-issue
                                           db/persist-record!))
                         (map adapters/parse-issue)
                         (map db/persist-record!))
                   (constantly nil)
                   issues-iteration)
        ))

(comment
    (def repo (get-last-processed-repository))

    repo

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

    (get-github-url issues-url)

    (when-let [repo (not-empty (get-last-processed-repository))]
        (println repo)
        (println "huh"))

    )