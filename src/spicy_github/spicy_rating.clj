(ns spicy-github.spicy-rating
    (:gen-class)
    (:require [clojure.java.io :as io]
              [clojure.edn :as edn]
              [spicy-github.util :refer :all]))

(defn- load-emoji-config! []
    (-> (io/resource "emoji-rating-config.edn")
        io/file
        slurp
        edn/read-string))

(def emoji-config (load-emoji-config!))
(def max-score 33)
(def comment-offset (/ 50 33))

(defn- rate-emojis [reactions-payload]
    (min max-score
         (int
             (/
                 (reduce +
                         (map #((let [maybe-bias ((key %1) emoji-config)
                                      bias (if (nil? maybe-bias) 0 maybe-bias)
                                      score (* bias (val %1))]
                                    score))
                              (seq reactions-payload)))
                 100))))

(defn- rate-total-reactions [reactions-payload]
    (let [total-reactions (:total_count reactions-payload)]
        (if
            (= 0 total-reactions)
            0
            (min max-score (int (/ total-reactions 30))))))

(defn- rate-total-comments [issue]
    (min max-score (int (/ (:issue/comment-count issue) 30))))

(defn rate-issue [issue]
    (let [reactions-json (:reactions (parse-json (:issue/github-json-payload issue)))]
        (+
            (rate-emojis reactions-json)
            (rate-total-comments issue)
            (rate-total-reactions reactions-json))))

(defn rate-comment [comment]
    (let [reactions-json (:reactions (parse-json (:comment/github-json-payload comment)))]
        (int (+
                 (* comment-offset (rate-emojis reactions-json))
                 (* comment-offset (rate-total-reactions reactions-json))))))