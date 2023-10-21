(ns spicy-github.db
    (:gen-class)
    (:require [clojure.java.io :as io]
              [clojure.string :as cs]
              [gungnir.changeset :as c]
              [gungnir.database]
              [gungnir.migration]
              [gungnir.transaction :as transaction]
              [gungnir.query :as q]
              [taoensso.timbre :as timbre]
              [honey.sql]
              [honeysql.core]
              [clojure.stacktrace]
              [honey.sql.helpers :as helpers]
        ; this must be here so our models get initialized
              [spicy-github.model :as model]
              [spicy-github.util :refer :all])
    (:import java.util.zip.ZipInputStream
             [org.reflections
              Reflections
              scanners.ResourcesScanner
              scanners.Scanner
              util.ClasspathHelper
              util.ConfigurationBuilder]
             (org.reflections.scanners ResourcesScanner)
             (org.reflections.util ClasspathHelper ConfigurationBuilder)))

(def default-password "")

(defn- db-server-name [] (load-env :rds-hostname "RDS_HOSTNAME" :RDS_HOSTNAME))
(defn- db-port [] (Integer/parseInt (load-env :rds-port "RDS_PORT" :RDS_PORT)))
(defn- db-name [] (load-env :rds-db-name "RDS_DB_NAME" :RDS_DB_NAME))
(defn- db-username [] (load-env :rds-username "RDS_USERNAME" :RDS_USERNAME))
(defn- db-password [] (load-env :rds-password "RDS_PASSWORD" :RDS_PASSWORD default-password))

(defn- db-config []
    {:adapter       "postgresql"
     :database-name (db-name)
     :server-name   (db-server-name)
     :username      (db-username)
     :password      (db-password)
     :port-number   (db-port)})

(defn- spicy-migrate [migrations-path]
    (-> (doto (ConfigurationBuilder.)
            (.setScanners (into-array Scanner [(ResourcesScanner.)]))
            (.setUrls (ClasspathHelper/forClassLoader (make-array ClassLoader 0))))
        ; TODO
        (slurp)
        ))

(defn register-db! [] (gungnir.database/make-datasource! (db-config)))

(defn load-resources [] (gungnir.migration/load-resources "migrations"))

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
    (timbre/debug "Attempting to persist Record: " record)
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

(def default-page-size 10)

(defn get-n-latest!
    ([table query-relations!] (get-n-latest! table query-relations! default-page-size))
    ([table query-relations! n]
     (transaction/execute!
         (fn []
             (map query-relations!
                  (->
                      (helpers/order-by [:updated-at :desc])
                      (helpers/limit n)
                      (q/all! table)))))))

(defn get-n-latest-before!
    ([table query-relations! before] (get-n-latest-before! table query-relations! default-page-size before))
    ([table query-relations! n before]
     (transaction/execute!
         (fn []
             (map query-relations!
                  (->
                      (helpers/order-by [:updated-at :desc])
                      (helpers/where [:< :updated-at before])
                      (helpers/limit n)
                      (q/all! table)))))))

(defn get-n-oldest!
    ([table query-relations! n]
     (transaction/execute!
         (fn []
             (map query-relations!
                  (->
                      (helpers/order-by :updated-at)
                      (helpers/limit n)
                      (q/all! table)))))))

(defn get-n-oldest-before!
    ([table query-relations! n before]
     (transaction/execute!
         (fn []
             (map query-relations!
                  (->
                      (helpers/order-by :updated-at)
                      (helpers/where [:< :updated-at before])
                      (helpers/limit n)
                      (q/all! table)))))))

(defn query-comment-relations! [comment]
    (q/load! comment :comment/user :comment/spicy-comment))

(defn query-issue-relations! [issue]
    (let [mapped-issue (q/load! issue :issue/user :issue/comments :issue/spicy-issue)]
        (assoc mapped-issue :issue/comments (map query-comment-relations! (:issue/comments mapped-issue)))))

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

(defn get-n-oldest-comments-before!
    ([n before] (get-n-oldest-before! :comment query-comment-relations! n before)))