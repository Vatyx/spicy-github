(ns spicy-github.dev
    (:gen-class)
    (:require [spicy-github.logging]
              [spicy-github.model]
              [spicy-github.db :as db]
              [spicy-github.util :refer :all]
              [spicy-github.adapters :as adapters]
              [clojure.stacktrace]
              [malli.dev.pretty]
              [hiccup.util]
              [gungnir.model]
              [taoensso.timbre :as timbre])
    (:import (java.time Instant)))

(defn should-remap-db [] (parse-boolean (load-env :remap-db "REMAP_DB" :REMAP_DB "false")))

(defn- remap! [db-query! parse-fn json-payload-keyword updated-at-keyword table-name]
    (loop [start-time (Instant/now)]
        (let [issue-batch (db-query! start-time)]
            (run!
                #(db/persist-record! (parse-fn (parse-json (json-payload-keyword %))))
                issue-batch)
            (timbre/debug (str "Processed " table-name " at " start-time))
            (if (== db/default-page-size (count issue-batch))
                (recur (updated-at-keyword (last issue-batch)))
                (timbre/info (str "Successfully remapped " table-name))))))

(defn remap-issues! []
    (remap! db/get-n-latest-issues-before! adapters/parse-issue :issue/github-json-payload :issue/updated-at "issues"))

(defn remap-comments! []
    (remap! db/get-n-latest-comments-before! adapters/parse-comment :comment/github-json-payload :comment/updated-at "comments"))

(defn remap-db! []
    (timbre/info "Remapping database...")
    (remap-issues!)
    (remap-comments!))