(ns spicy-github.logging
    (:gen-class)
    (:require
        [clojure.java.io :as io]
        [clojure.string :as string]
        [taoensso.timbre :as timbre]
        [clojure.stacktrace]
        [spicy-github.util :refer [load-env]]
        [taoensso.timbre :as timbre :refer :all]
        [taoensso.timbre.appenders.community.rolling :refer :all]
        [jdk.io.FileWriter :as file-writer])
    (:import
        (java.io BufferedWriter)
        [java.text ParsePosition SimpleDateFormat]
        (java.time Instant)
        [java.util Calendar Date]))

; We COULD use timbre environment variable, but I don't want to
; since we're configuring the logging ourselves through overrides
; that are *not* the timber environment variable.
(def log-level (string/lower-case (load-env :log-level "LOG_LEVEL" :LOG_LEVEL "info")))
(def log-path "./logs/")
(def log-name "spicy-log.log")
(def log-name-count (count log-name))
(def composite-log-path (str log-path log-name))
(def date-format "yyyy-MM-dd")
(def date-format-count (count date-format))

; Most of this stuff is copy-paste from
; https://github.com/taoensso/timbre/blob/master/src/taoensso/timbre/appenders/community/rolling.clj

; Copy-pasta
(defn- rename-old-create-new-log [log old-log]
    (.renameTo log old-log)
    (.createNewFile log))

; Copy-pasta
(defn- shift-log-period [log path prev-cal]
    (let [postfix (-> date-format SimpleDateFormat. (.format (.getTime prev-cal)))
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
        (let [file-name (.getName file)]
            (when (and
                      (clojure.string/includes? file-name log-name)
                      (>= (count file-name) (+ 1 log-name-count date-format-count)))
                (let [formatted-date (subs file-name
                                           (+ 1 log-name-count)
                                           (+ 1 date-format-count log-name-count))]
                    (when (>= (count formatted-date) (count date-format))
                        (let [log-date (.parse (SimpleDateFormat. date-format) formatted-date (ParsePosition. 0))]
                            (when (and (not (nil? log-date)) (.before log-date (.getTime calendar-cutoff)))
                                (io/delete-file file)))))))))

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

(def log-file-writer (atom nil))
(def log-file (atom nil))
(def last-flush (atom nil))

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
                 (try
                     (locking lock
                         (when (nil? @log-file) (do
                                                    (reset! log-file (io/file composite-log-path))
                                                    (when (.exists @log-file)
                                                        (reset! log-file-writer (file-writer/->file-writer composite-log-path true)))))
                         (when-not (.exists @log-file)
                             (io/make-parents @log-file))
                         (if (.exists @log-file)
                             (when (<= (.lastModified @log-file) (.getTimeInMillis prev-cal))
                                 (do
                                     (maintain-logs! @log-file composite-log-path prev-cal delete-cal)
                                     (reset! log-file (io/file composite-log-path))
                                     (when @log-file-writer (.close @log-file-writer))
                                     (reset! log-file-writer (file-writer/->file-writer composite-log-path true))))
                             (do
                                 (.createNewFile @log-file)
                                 (reset! log-file-writer (file-writer/->file-writer composite-log-path true)))))
                     (let [w (new BufferedWriter @log-file-writer)]
                         (.write w (str output-str "\n"))
                         (.flush w)
                         (when (not @last-flush) (reset! last-flush (Instant/now))))
                     (let [now (Instant/now)]
                         (when (<= (+ 5000 (inst-ms @last-flush)) (inst-ms now))
                             (do
                                 (reset! last-flush now)
                                 (.flush @log-file-writer))))
                     (catch Exception e (clojure.stacktrace/print-stack-trace e))))))})

(timbre/merge-config! {:min-level  (keyword log-level)
                       :appenders  {:rotating-rotating-daily (rotating-rolling-appender)}
                       :middleware [(fn [data]
                                        (update data :vargs
                                                (partial mapv
                                                         #(clojure.string/trim-newline
                                                              (if (string? %)
                                                                  %
                                                                  (with-out-str (clojure.pprint/pprint %)))))))]})

(defn initialize! [])