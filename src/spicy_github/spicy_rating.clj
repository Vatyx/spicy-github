(ns spicy-github.spicy-rating
    (:gen-class)
    (:require [clojure.instant :as instant]
              [clojure.java.io :as io]
              [clojure.edn :as edn]
              [spicy-github.util :refer :all]
              [spicy-github.model :as model] ; this must be here so our models get initialized
              [spicy-github.db :as db])
    (:import (java.util Date)
             (gungnir.database RelationAtom)))

(defn- load-emoji-config! []
    (-> "emoji-rating-config.edn"
        load-resource
        edn/read-string))

(def emoji-config (load-emoji-config!))
(def max-score (float 33))
(def comment-offset (/ 50 33))
(def db-page-size 100)

(defn- rate-emojis [reactions-payload]
    (min max-score
         (int
             (/
                 (reduce +
                         (map (fn [[emoji total]]
                                  (let [bias ((keyword emoji) emoji-config 0)
                                        score (if (= 0 bias) 0 (* bias total))]
                                      score))
                              (seq reactions-payload)))
                 100))))

(defn- rate-total-reactions [reactions-payload]
    (let [total-reactions (:total_count reactions-payload)]
        (if
            (= 0 total-reactions)
            (float 0)
            (min max-score (float (/ total-reactions 30))))))

(defn- rate-total-comments [issue]
    (min max-score (float (/ (:issue/comment-count issue) 30))))

(defn- rate-issue [issue]
    (let [reactions-json (:reactions (parse-json (:issue/github-json-payload issue)))]
        (float (+
            (rate-emojis reactions-json)
            (rate-total-comments issue)
            (rate-total-reactions reactions-json)))))

(defn- rate-comment [comment]
    (let [reactions-json (:reactions (parse-json (:comment/github-json-payload comment)))]
        (float (+
                 (* comment-offset (rate-emojis reactions-json))
                 (* comment-offset (rate-total-reactions reactions-json))))))

(defn- map-and-rate-issue [issue]
    {:spicy-issue/id     (:issue/id issue)
     :spicy-issue/rating (rate-issue issue)})

(defn- map-and-rate-comment [comment]
    {:spicy-comment/id     (:comment/id comment)
     :spicy-comment/rating (rate-comment comment)})

(defn- forever-rate! [get-fn! map-fn update-at-fn]
    (loop [current-time (new Date)]
        (let [records (get-fn! db-page-size current-time)
              spicy-records (doall (map map-fn records))]
            (doall (map db/persist-record! spicy-records))
            (Thread/sleep (int (rand 5000)))
            (if (< (count records) db/default-page-size)
                (recur (new Date))
                (recur (update-at-fn (last records)))))))

(defn forever-rate-issues! []
    (forever-rate! db/get-n-latest-issues-before! map-and-rate-issue :issue/updated-at))

(defn forever-rate-comments! []
    (forever-rate! db/get-n-latest-comments-before! map-and-rate-comment :comment/updated-at))