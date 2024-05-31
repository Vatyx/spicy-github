(ns spicy-github.db
    (:gen-class)
    (:require [clojure.edn :as edn]
              [clojure.string :as cs]
              [gungnir.changeset :as c]
              [gungnir.database]
              [gungnir.model]
              [gungnir.migration]
              [gungnir.transaction :as transaction]
              [gungnir.query :as q]
              [taoensso.timbre :as timbre]
              [honey.sql :as sql]
              [honey.sql.helpers :as helpers]
              [clojure.stacktrace]
              [cheshire.core :refer :all]
              [next.jdbc :as jdbc]
        ; this must be here so our models get initialized
              [spicy-github.model :as model]
              [spicy-github.util :refer :all])
    (:import (java.time Instant)))

; https://stackoverflow.com/a/46859915/1917135
(extend-protocol cheshire.generate/JSONable
    Instant
    (to-json [dt gen]
        (cheshire.generate/write-string gen (str dt))))

(def default-page-size 25)

(defn- db-server-name [] (load-env :rds-hostname "RDS_HOSTNAME" :RDS_HOSTNAME))
(defn- db-port [] (Integer/parseInt (load-env :rds-port "RDS_PORT" :RDS_PORT)))
(defn- db-name [] (load-env :rds-db-name "RDS_DB_NAME" :RDS_DB_NAME))
(defn- db-username [] (load-env :rds-username "RDS_USERNAME" :RDS_USERNAME))
(defn- db-password [] (load-env :rds-password "RDS_PASSWORD" :RDS_PASSWORD))

(defn- db-config []
    {:adapter       "postgresql"
     :database-name (db-name)
     :server-name   (db-server-name)
     :username      (db-username)
     :password      (db-password)
     :port-number   (db-port)})

(defn- log-and-return-migration [migration]
    (timbre/info (str "Loading migration " migration))
    migration)

; https://stackoverflow.com/questions/46488466/clojure-list-subfolders-in-resources-in-uberjar
(defn- spicy-load-resources [migrations-path]
    (try
        (let [migrations (doall
                             (map log-and-return-migration
                                  (gungnir.migration/load-resources migrations-path)))]
            (timbre/info (str "Loaded " (count migrations) " resources."))
            migrations)
        (catch Exception _
            (map log-and-return-migration
                 (map #(assoc (edn/read-string (load-resource %)) :id (subs % (+ 1 (cs/last-index-of % "/")) (cs/last-index-of % ".edn")))
                      (let [loaded-resource-names (list-zip-contents migrations-path)]
                          (timbre/info (str "Loaded " (count loaded-resource-names) " resources: " (cs/join ", " loaded-resource-names)))
                          (->> loaded-resource-names
                               (filter (fn [path] (cs/ends-with? path ".edn")))
                               (sort))))))))

(defn register-db! [] (gungnir.database/make-datasource! (db-config)))

(defn- load-resources [] (spicy-load-resources "migrations"))

(defn reset-db! []
    (let [migrations (load-resources)]
        (register-db!)
        (gungnir.migration/rollback! migrations)
        (gungnir.migration/migrate! migrations)))

(defn migrate-db! []
    (register-db!)
    (gungnir.migration/migrate! (load-resources)))

(defn rollback-db! []
    (register-db!)
    (gungnir.migration/rollback! (load-resources)))

(defn initialize! []
    (register-db!)
    (migrate-db!))

(sql/register-clause!
    :tablesample
    (fn [clause x]
        (into [(str (sql/sql-kw clause) " " x)]))
    :where)

(defn persist!
    ([changeset query-by-id! clean-record equality-check?]
     (persist! changeset gungnir.database/*datasource* query-by-id! clean-record equality-check?))
    ([{:changeset/keys [_] :as changeset} datasource query-by-id! clean-record equality-check?]
     (let [input-record (:changeset/result changeset)
           diff (:changeset/diff changeset)]
         (let [existing (query-by-id! input-record)]
             (if (some? existing)
                 (if (equality-check? existing input-record)
                     existing
                     (let [updated-record (gungnir.database/update! (clean-record existing diff) datasource)
                           update-errors (:changeset/errors updated-record)]
                         (if (some? update-errors)
                             input-record
                             updated-record)))
                 (let [_ (gungnir.database/insert! changeset datasource)]
                     input-record))))))

(defn persist-record! [record]
    (timbre/debug "Attempting to persist Record:" record)
    (let [changeset (c/create record)
          model-key (:changeset/model changeset)
          id-key (namespace-key model-key :id)]
        (persist!
            changeset
            (fn [record] (q/find! model-key (id-key record)))
            (fn [existing record] (c/create existing (c/cast record model-key)))
            model-equality?)))

(defn persist-record-exception-safe! [record]
    (try
        (persist-record! record)
        (catch Exception e
            (clojure.stacktrace/print-stack-trace e)
            (timbre/error (str e))
            record)))


(defn get-by-id! [table id] (q/find! table id))

(defn delete! [record] (q/delete! record))

(defn get-n-latest!
    ([table query-relations!] (get-n-latest! table query-relations! default-page-size))
    ([table query-relations! n]
     (transaction/execute!
         (fn []
             (doall (map query-relations!
                         (-> (helpers/order-by [:updated-at :desc])
                             (helpers/limit n)
                             (q/all! table))))))))

; https://stackoverflow.com/questions/5297396/quick-random-row-selection-in-postgres
(defn get-n-random!
    ([table query-relations!] (get-n-random! table query-relations! {}))
    ([table query-relations! query-map] (get-n-random! table query-relations! query-map default-page-size))
    ([table query-relations! query-map n]
     (doall (map query-relations!
                 (-> (merge {:select [:*] :limit n :tablesample "system(5)"} query-map)
                     (q/all! table))))))

(defn get-n-latest-before!
    ([table query-relations! before] (get-n-latest-before! table query-relations! default-page-size before))
    ([table query-relations! n before]
     (doall (map query-relations!
                 (-> (helpers/where [:< :updated-at before])
                     (helpers/order-by [:updated-at :desc])
                     (helpers/limit n)
                     (q/all! table)))))
    ([table query-relations! n before where-clause]
     (doall (map query-relations!
                 (-> where-clause
                     (helpers/where [:< :updated-at before])
                     (helpers/order-by [:updated-at :desc])
                     (helpers/limit n)
                     (q/all! table))))))



(defn get-n-oldest!
    ([table query-relations! n]
     (doall (map query-relations!
                 (-> (helpers/order-by :updated-at)
                     (helpers/limit n)
                     (q/all! table))))))

(defn get-n-oldest-before!
    ([table query-relations! n before]
     (doall (map query-relations!
                 (-> (helpers/where [:< :updated-at before])
                     (helpers/order-by :updated-at)
                     (helpers/limit n)
                     (q/all! table))))))

(defn query-comment-relations! [comment]
    (q/load! comment :comment/user))

(defn query-spicy-comment-relations! [spicy-comment]
    (q/load! spicy-comment :spicy-comment/comment))

(defn query-spicy-issue-relations! [spicy-issue]
    (q/load! spicy-issue :spicy-issue/issue))

(defn query-issue-relations! [issue]
    (let [mapped-issue (q/load! issue :issue/user :issue/comments :issue/spicy-issue :issue/spicy-comments)]
        (assoc mapped-issue :issue/comments (map query-comment-relations! (:issue/comments mapped-issue)))))

(defn map-spicy-issue-to-issue [spicy-issue]
    (query-issue-relations! (:spicy-issue/issue spicy-issue)))

(defn map-spicy-comment-to-comment [spicy-comment]
    (query-comment-relations! (:spicy-comment/comment spicy-comment)))

(defn get-n-latest-issues!
    ([] (get-n-latest! :issue query-issue-relations!))
    ([n] (get-n-latest! :issue query-issue-relations! n)))

(defn get-n-latest-issues-before!
    ([before] (get-n-latest-before! :issue query-issue-relations! before))
    ([n before] (get-n-latest-before! :issue query-issue-relations! n before)))

(defn get-n-latest-comments!
    ([] (get-n-latest! :comment query-comment-relations!))
    ([n] (get-n-latest! :comment query-comment-relations! n)))

(defn get-n-latest-comments-before!
    ([before] (get-n-latest-before! :comment query-comment-relations! before))
    ([n before] (get-n-latest-before! :comment query-comment-relations! n before)))

(defn get-n-latest-spicy-comments-before!
    ([before] (get-n-latest-before! :spicy-comment query-spicy-comment-relations! before))
    ([n before] (get-n-latest-before! :spicy-comment query-spicy-comment-relations! n before))
    ([n before where-query] (get-n-latest-before! :spicy-comment query-spicy-comment-relations! n before where-query)))

(defn get-n-oldest-comments-before!
    ([n before] (get-n-oldest-before! :comment query-comment-relations! n before)))

(defn get-n-random-comments!
    ([] (get-n-random-comments! default-page-size))
    ([n] (get-n-random! :comment query-comment-relations! {} n)))

(defn get-n-random-comments-above-threshold!
    ([threshold] (get-n-random-comments-above-threshold! threshold default-page-size))
    ([threshold n] (map map-spicy-comment-to-comment (get-n-random! :spicy-comment query-spicy-comment-relations! {:where [:> :total_rating threshold]} n))))

(defn get-n-random-issues!
    ([] (get-n-random-issues! default-page-size))
    ([n] (get-n-random! :issue query-issue-relations! {} n)))

(defn get-n-random-issues-above-threshold!
    ([threshold] (get-n-random-issues-above-threshold! threshold default-page-size))
    ([threshold n] (map map-spicy-issue-to-issue (get-n-random! :spicy-issue query-spicy-issue-relations! {:where [:> :total_rating threshold]} n))))

(defn get-n-random-issues-from-highly-rated-comments!
    ([n] (map query-issue-relations!
              (map (fn [highly-rated-comment]
                       (get-by-id! :issue (:highly-rated-comment/issue-id highly-rated-comment)))
                   (get-n-random! :highly-rated-comment (fn [_] _) {} n)))))

(defn accumulate-until-at-least [retrieval-fn n]
    (loop [result []]
        (if (>= (count result) n)
            result
            (recur (concat result (retrieval-fn))))))

; New APIs
; --------

(defn get-comments-for-issue [issue-id offset]
    (-> (helpers/select :*)
        (helpers/from :comment)
        (helpers/where [:= :comment/issue-id issue-id])
        (helpers/order-by :comment/id)
        (helpers/offset offset)
        (helpers/limit default-page-size)
        (q/all!)))

(defn get-comment-count-for-issue [issue-id]
    (-> (jdbc/execute!
            gungnir.database/*datasource*
            (-> (helpers/select :%count.*)
                (helpers/from :comment)
                (helpers/where [:= :comment/issue-id issue-id])
                (sql/format)))
        (first)
        (:count)))
