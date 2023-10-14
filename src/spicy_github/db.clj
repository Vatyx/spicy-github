(ns spicy-github.db
  (:gen-class)
    (:require [gungnir.changeset :as c]
              [gungnir.database]
              [gungnir.migration]
              [gungnir.query :as q]
              [taoensso.timbre :as timbre]
              [spicy-github.util :refer :all]))

(def db-config
  {:adapter "postgresql"
   :database-name "spicy-github"
   :server-name "localhost"
   :username "postgres"
   :password ""})

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
             (timbre/spy :debug "Successfully inserted: " (:changeset/result changeset))
             (let [diff (get changeset :changeset/diff)
                   inputRecord (get changeset :changeset/result)
                   existing (query-by-id! inputRecord)]
                 (if (equality-check? existing inputRecord)
                     existing
                     (gungnir.database/update! (clean-record existing diff) datasource))
                 )
             ))))


(defn persist-record! [record]
    (timbre/debug "Persisting Record: " record)
    (let [changeset (c/create record)
          model-key (:changeset/model changeset)
          id-key (namespace-key model-key :id)]
        (persist!
            changeset
            (fn [record] (q/find! model-key (id-key record)))
            (fn [existing record] (c/create existing (c/cast record model-key)))
            model-equality?)))

(register-db!)