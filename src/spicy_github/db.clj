(ns spicy-github.db
    (:gen-class)
    (:require [gungnir.changeset :as c]
              [gungnir.database]
              [gungnir.migration]
              [gungnir.transaction :as transaction]
              [gungnir.query :as q]
              [spicy-github.util :refer :all]
              [honey.sql]
              [honeysql.core]
              [honey.sql.helpers :as helpers]))

(def db-config
    {:adapter       "postgresql"
     :database-name "spicy-github"
     :server-name   "localhost"
     :username      "postgres"
     :password      ""})

(defn register-db! [] (gungnir.database/make-datasource! db-config))

(defn load-resources [] (gungnir.migration/load-resources "migrations"))

(defn initialize-db! []
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
+
; TODO: Fix this, it needs to have a generic query function
; that does a lookup and merge if records are found by that id
(defn persist!
    "Hack because I don't know how to decompose this yet. Modified version of gungnir.query/save!
     because our records will have primary keys already associated with them from github."
    ([changeset query-by-id! clean-record equality-check?]
     (persist! changeset gungnir.database/*datasource* query-by-id! clean-record equality-check?))
    ([{:changeset/keys [_] :as changeset} datasource query-by-id! clean-record equality-check?]
     (let [initial-results (gungnir.database/insert! changeset datasource)]
         (if (nil? (get initial-results :changeset/errors))
             changeset
             (let [diff (get changeset :changeset/diff)
                   inputRecord (get changeset :changeset/result)
                   existing (query-by-id! inputRecord)]
                 (if (equality-check? existing inputRecord)
                     existing
                     (gungnir.database/update! (clean-record existing diff) datasource))
                 )
             ))))

(defn persist-record! [record]
    (let [changeset (c/create record)
          model-key (:changeset/model changeset)
          id-key (namespace-key model-key :id)]
        (persist!
            changeset
            (fn [record] (q/find! model-key (id-key record)))
            (fn [existing record] (c/create existing (c/cast record model-key)))
            model-equality?)))

(defn get-n-latest!
    ([table query-relations!] (get-n-latest! table query-relations! 5))
    ([table query-relations! n]
     (transaction/execute!
         (fn []
             (map query-relations!
                  (->
                      (helpers/order-by [:updated-at :desc])
                      (helpers/limit n)
                      (q/all! table)))))))


(defn query-comment-relations! [comment]
    (q/load! comment :comment/user))

(defn query-issue-relations! [issue]
    (let [mapped-issue (q/load! issue :issue/user :issue/comments)]
        (assoc mapped-issue :issue/comments (map query-comment-relations! (:issue/comments mapped-issue)))))

(defn get-n-latest-issues!
    ([] (get-n-latest! :issue query-issue-relations!))
    ([n] (get-n-latest! :issue query-issue-relations! n)))

(defn get-n-latest-comments!
    ([] (get-n-latest! :comment query-comment-relations!))
    ([n] (get-n-latest! :comment query-comment-relations! n)))