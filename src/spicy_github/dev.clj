(ns spicy-github.dev
    (:gen-class)
    (:require [spicy-github.logging]
              [spicy-github.model]
              [spicy-github.db :as db]
              [spicy-github.util :refer :all]
              [spicy-github.adapters :as adapters]
              [cheshire.core :refer :all]
              [clojure.stacktrace]
              [malli.dev.pretty]
              [hiccup.util]
              [gungnir.model]
              [taoensso.timbre :as timbre])
    (:import (java.time Instant)))

(defn should-remap-db [] (parse-boolean (load-env :remap-db "REMAP_DB" :REMAP_DB "false")))

(defn- remap! [db-query! parse-fn json-payload-keyword updated-at-keyword table-name]
    (let [record-count 1000
          checkpoint-id (str "remap-" table-name "!")
          checkpoint (db/get-by-id! :checkpoint checkpoint-id)
          checkpoint-time (spicy-github.adapters/checkpoint-get-time checkpoint (Instant/now))]
        (loop [current-time checkpoint-time]
            (let [issue-batch (db-query! record-count current-time)
                  current-checkpoint (spicy-github.adapters/checkpoint-create checkpoint-id current-time)]
                (db/persist-record! current-checkpoint)
                (run!
                    #(db/persist-record! (parse-fn (parse-json (json-payload-keyword %))))
                    issue-batch)
                (timbre/debug (str "Processed " table-name " at " current-time))
                (Thread/sleep (int (rand 5000)))
                (if (<= record-count (count issue-batch))
                    (recur (updated-at-keyword (first (sort-by updated-at-keyword issue-batch))))
                    (do
                        (db/delete! current-checkpoint)
                        (timbre/info (str "Successfully remapped " table-name))))))))

(defn remap-issues! []
    (remap! db/get-n-latest-issues-before! adapters/parse-issue :issue/github-json-payload :issue/updated-at "issues"))

(defn remap-comments! []
    (remap! db/get-n-latest-comments-before! adapters/parse-comment :comment/github-json-payload :comment/updated-at "comments"))

(defn remap-db! []
    (timbre/info "Remapping database...")
    (remap-comments!)
    (remap-issues!))