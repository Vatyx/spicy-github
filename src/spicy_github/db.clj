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

(defn load-resources [] (gungnir.migration/load-resources "migrations"))

(defn initialize-db! []
    (let [migrations (load-resources)]
        (gungnir.database/make-datasource! db-config)
        (gungnir.migration/rollback! migrations)
        (gungnir.migration/migrate! migrations)))

(defn migrate-db! []
  (gungnir.database/make-datasource! db-config)
  (gungnir.migration/migrate! (load-resources)))

(defn rollback-db! []
  (gungnir.database/make-datasource! db-config)
  (gungnir.migration/rollback! (load-resources)))