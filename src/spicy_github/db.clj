(ns spicy-github.db
  (:gen-class)
  (:require [gungnir.database]
            [gungnir.migration]))

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