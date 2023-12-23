(ns spicy-github.spicy-rating
    (:gen-class)
    (:require [clojure.edn :as edn]
              [honey.sql.helpers :as helpers]
              [spicy-github.util :refer :all]
              [clojure.stacktrace]
              [cheshire.core :refer :all]
              [gungnir.transaction :as transaction]
              [spicy-github.model :as model]                ; this must be here so our models get initialized
              [spicy-github.db :as db]
              [taoensso.timbre :as timbre])
    (:import (java.time Instant)))

(defn- load-emoji-config! []
    (-> "emoji-rating-config.edn"
        load-resource
        edn/read-string))

(def emoji-config (load-emoji-config!))
(def max-score (float 33))
(def comment-offset (/ 50 33))
(def db-page-size 250)

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

(defn- calculate-total-reactions [total-reactions]
    (if
        (= 0 total-reactions)
        (float 0)
        (min max-score (float (/ total-reactions 30)))))

(defn- rate-total-reactions [reactions-payload]
    ; issues don't have total_count for some reason, so fall back to summing
    (if-let [total-reactions (:total_count reactions-payload)]
        (calculate-total-reactions total-reactions)
        (calculate-total-reactions (apply + (vals reactions-payload)))))

(defn- rate-total-comments [issue]
    (min max-score (float (/ (:issue/comment-count issue) 30))))

(defn- rate-issue [issue]
    (let [reactions-json (parse-json (:issue/reaction-json issue))]
        (float (+
                   (rate-emojis reactions-json)
                   (rate-total-comments issue)
                   (rate-total-reactions reactions-json)))))

(defn- rate-comment [comment]
    (let [reactions-json (parse-json (:comment/reaction-json comment))]
        (float (+
                   (* comment-offset (rate-emojis reactions-json))
                   (* comment-offset (rate-total-reactions reactions-json))))))

(defn sum-reactions-of-type [reactions types]
    (reduce + (vals (select-keys reactions types))))

(defn get-funny-rating [reactions]
    (sum-reactions-of-type reactions [:laugh]))

(defn get-controversial-rating [reactions]
    (sum-reactions-of-type reactions [:-1 :confused :eyes]))

(defn get-agreeable-rating [reactions]
    (sum-reactions-of-type reactions [:+1 :rocket :heart]))

(defn- map-and-rate-issue [issue]
    (let [reactions (parse-json (:issue/reaction-json issue))]
        {:spicy-issue/id                   (:issue/id issue)
         :spicy-issue/total-rating         (double (rate-issue issue))
         :spicy-issue/funny-rating         (double (get-funny-rating reactions))
         :spicy-issue/controversial-rating (double (get-controversial-rating reactions))
         :spicy-issue/agreeable-rating     (double (get-agreeable-rating reactions))}))

(defn- map-and-rate-comment [comment]
    (let [reactions (parse-json (:comment/reaction-json comment))]
        {:spicy-comment/id                   (:comment/id comment)
         :spicy-comment/total-rating         (double (rate-comment comment))
         :spicy-comment/funny-rating         (double (get-funny-rating reactions))
         :spicy-comment/controversial-rating (double (get-controversial-rating reactions))
         :spicy-comment/agreeable-rating     (double (get-agreeable-rating reactions))
         :spicy-comment/issue-id             (:comment/issue-id comment)}))

(defn- map-and-rate-spicy-comment [spicy-comment]
    {:highly-rated-comment/id           (:spicy-comment/id spicy-comment)
     :highly-rated-comment/total-rating (double (:spicy-comment/total-rating spicy-comment))
     :highly-rated-comment/issue-id     (-> spicy-comment :spicy-comment/comment :comment/issue-id)})

(defn- forever-run!
    ([get-fn! map-fn update-at-fn checkpoint-id last-processed-input]
     (let [last-processed (atom last-processed-input)]
         (try
             (loop [current-time @last-processed]
                 (db/persist-record! {:checkpoint/id checkpoint-id :checkpoint/json-payload (generate-string {:time current-time})})
                 (reset! last-processed current-time)
                 (let [records (get-fn! db-page-size current-time)
                       spicy-records (doall (map map-fn records))]
                     (try (doall (map db/persist-record! spicy-records))
                          (Thread/sleep (int (rand 5000)))
                          (catch Exception e
                              (clojure.stacktrace/print-stack-trace e)
                              (timbre/error (str e))))
                     (if (< (count records) db-page-size)
                         (recur (Instant/now))
                         (recur (update-at-fn (last records))))))
             (catch Exception e
                 (timbre/error (str e))
                 (forever-run! get-fn! map-fn update-at-fn checkpoint-id @last-processed)))))
    ([get-fn! map-fn update-at-fn checkpoint-id last-processed-input where-query delay]
     (let [last-processed (atom last-processed-input)]
         (try
             (loop [current-time @last-processed]
                 (db/persist-record! {:checkpoint/id checkpoint-id :checkpoint/json-payload (generate-string {:time current-time})})
                 (reset! last-processed current-time)
                 (let [records (get-fn! db-page-size current-time where-query)
                       spicy-records (doall (map map-fn records))]
                     (try (doall (map db/persist-record! spicy-records))
                          (Thread/sleep (int (rand delay)))
                          (catch Exception e
                              (clojure.stacktrace/print-stack-trace e)
                              (timbre/error (str e))))
                     (if (< (count records) db-page-size)
                         (recur (Instant/now))
                         (recur (update-at-fn (last records))))))
             (catch Exception e
                 (timbre/error (str e))
                 (forever-run! get-fn! map-fn update-at-fn checkpoint-id @last-processed where-query delay))))))

(defn forever-rate-issues! []
    (let [checkpoint-id "forever-rate-issues!"
          checkpoint (db/get-by-id! :checkpoint checkpoint-id)
          checkpoint-time (get :time (parse-json (get :checkpoint/json-payload checkpoint)) (Instant/now))]
        (forever-run! db/get-n-latest-issues-before! map-and-rate-issue checkpoint-id :issue/updated-at checkpoint-time)))

(def minimum-highly-rated-threshold (float 3.5))

(defn forever-rate-comments! []
    (let [checkpoint-id "forever-rate-comments!"
          checkpoint (db/get-by-id! :checkpoint checkpoint-id)
          checkpoint-time (get :time (parse-json (get :checkpoint/json-payload checkpoint)) (Instant/now))]
        (forever-run! db/get-n-latest-comments-before! map-and-rate-comment checkpoint-id :comment/updated-at checkpoint-time)))

(defn forever-migrate-highly-rated-comments! []
    (let [checkpoint-id "forever-migrate-highly-rated-comments!"
          checkpoint (db/get-by-id! :checkpoint checkpoint-id)
          checkpoint-time (get :time (parse-json (get :checkpoint/json-payload checkpoint)) (Instant/now))]
        (forever-run!
            db/get-n-latest-spicy-comments-before!
            map-and-rate-spicy-comment
            forever-migrate-highly-rated-comments!
            :spicy-comment/updated-at
            checkpoint-time
            (helpers/where [:>= :total-rating minimum-highly-rated-threshold])
            (* 30 1000))))

(def spicy-comments-xf
    (comp
        (map map-and-rate-comment)
        (execute #(db/persist-record! %))))

(defn rate-all-comments! []
    (let [checkpoint-id "rate-all-comments!"
          checkpoint (db/get-by-id! :checkpoint checkpoint-id)
          checkpoint-time (get :time (parse-json (get :checkpoint/json-payload checkpoint)) (Instant/now))]
        (loop [comments (db/get-n-oldest-comments-before! 100 checkpoint-time)]
            (if (empty? comments)
                nil
                (let [spicy-comments (doall (map map-and-rate-comment comments))]
                    (db/persist-record! {:checkpoint/id checkpoint-id :checkpoint/json-payload (generate-string {:time checkpoint-time})})
                    (doall (map db/persist-record-exception-safe! spicy-comments))
                    (recur (db/get-n-oldest-comments-before! 100 (:comment/updated-at (last comments)))))))))
