(ns spicy-github.logging
    (:gen-class)
    (:require
        [clojure.java.io :as io]
        [taoensso.timbre :as timbre]
        [spicy-github.env :refer [spicy-env]]
        [taoensso.timbre :as timbre :refer :all]
        [taoensso.timbre.appenders.community.rolling :refer :all])
    (:import
        (java.io IOException)
        [java.text ParsePosition SimpleDateFormat]
        [java.util Calendar]))

; We COULD use timbre environment variable, but I don't want to
; since we're configuring the logging ourselves through overrides
; that are *not* the timber environment variable.
(def log-level (spicy-env :log-level))
(def log-path "./logs/")
(def log-name "spicy-log.log")
(def composite-log-path (str log-path log-name))
(def date-format "yyyyMMdd")

; Most of this stuff is copy-paste from
; https://github.com/taoensso/timbre/blob/master/src/taoensso/timbre/appenders/community/rolling.clj

; Copy-pasta
(defn- rename-old-create-new-log [log old-log]
    (.renameTo log old-log)
    (.createNewFile log))

; Copy-pasta
(defn- shift-log-period [log path prev-cal]
    (let [postfix (-> "yyyyMMdd" SimpleDateFormat. (.format (.getTime prev-cal)))
          old-path (format "%s.%s" path postfix)
          old-log (io/file old-path)]
        (if (.exists old-log)
            (loop [index 0]
                (let [index-path (format "%s.%d" old-path index)
                      index-log (io/file index-path)]
                    (if (.exists index-log)
                        (recur (+ index 1))
                        (rename-old-create-new-log log index-log))))
            (rename-old-create-new-log log old-log))))

(defn- delete-log-if-old! [file calendar-cutoff]
    (when (.exists file)
        (let [log-year-month-day (take-last (count date-format) (.getName file))
              log-date (.parse (SimpleDateFormat. date-format) (ParsePosition. 0) log-year-month-day)]
            (when (not (nil? log-date))
                (when (.before log-date (.getTime calendar-cutoff))
                    (io/delete-file file))))))

(defn- clean-old-logs! [calendar-cutoff]
    (run!
        #(delete-log-if-old! %1 calendar-cutoff)
        (file-seq (clojure.java.io/file log-path))))

(defn- maintain-logs! [log path prev-cal calendar-cutoff]
    (shift-log-period log path prev-cal)
    (clean-old-logs! calendar-cutoff))

; Copy-pasta
(defn- log-cal [date] (let [now (Calendar/getInstance)] (.setTime now date) now))

; Copy-pasta
(defn- prev-period-end-cal [date pattern look-back]
    (let [cal (log-cal date)
          offset (case pattern
                     :daily 1
                     :weekly (.get cal Calendar/DAY_OF_WEEK)
                     :monthly (.get cal Calendar/DAY_OF_MONTH)
                     0)]
        (.add cal Calendar/DAY_OF_MONTH (* -1 offset look-back))
        (.set cal Calendar/HOUR_OF_DAY 23)
        (.set cal Calendar/MINUTE 59)
        (.set cal Calendar/SECOND 59)
        (.set cal Calendar/MILLISECOND 999)
        cal))

; Copy-pasta
(defn- rotating-rolling-appender
    "Returns a Rolling file appender. Opts:
      :pattern - frequency of rotation, e/o {:daily :weekly :monthly}.
      :num-logs - how many logs to keep."
    [& [{:keys [pattern num-logs]
         :or   {pattern  :daily
                num-logs 7}}]]
    {:enabled? true
     :fn
     (let [lock (Object.)]
         (fn [data]
             (let [{:keys [instant output_]} data
                   output-str (clojure.string/trim-newline (force output_))
                   prev-cal (prev-period-end-cal instant pattern 1)
                   delete-cal (prev-period-end-cal instant pattern num-logs)]
                 (when-let [log (io/file composite-log-path)]
                     (try
                         (locking lock
                             (when-not (.exists log)
                                 (io/make-parents log))
                             (if (.exists log)
                                 (if (<= (.lastModified log) (.getTimeInMillis prev-cal))
                                     (maintain-logs! log composite-log-path prev-cal delete-cal))
                                 (.createNewFile log)))
                         (spit composite-log-path (with-out-str output-str) :append true)
                         (catch IOException _))))))})

(timbre/merge-config! {:min-level  (keyword log-level)
                       :appenders  {:rotating-rotating-daily (rotating-rolling-appender)}
                       :middleware [(fn [data]
                                        (update data :vargs (partial mapv
                                                                     #(clojure.string/trim-newline
                                                                          (if (string? %)
                                                                              %
                                                                              (with-out-str (clojure.pprint/pprint %)))))))]})
