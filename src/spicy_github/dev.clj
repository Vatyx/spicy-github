(ns spicy-github.dev
    (:gen-class)
    (:require [spicy-github.db :as db]
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

(defn should-remap-db [] (parse-boolean (load-env :remap-db "REMAP_DB" :REMAP_DB "false")))

(defn- remap! [db-query! parse-fn updated-at-keyword]
    (loop [start-time (new Date)]
        (let [issue-batch (db-query! start-time)]
            (doall
                (map
                    #(db/persist-record! (parse-fn (parse-json (:github-json-payload %))))
                    issue-batch))
            (when (<= db/default-page-size (count issue-batch))
                (recur (updated-at-keyword (last issue-batch)))))))

(defn remap-issues! []
    (remap! db/get-n-latest-issues-before! adapters/parse-issue :issue/updated-at))

(defn remap-comments! []
    (remap! db/get-n-latest-comments-before! adapters/parse-comment :comment/updated-at))

(defn remap-db! []
    (remap-issues!)
    (remap-comments!))