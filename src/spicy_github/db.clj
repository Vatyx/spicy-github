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

(defn initialize-db! []
    (let [migrations (gungnir.migration/load-resources "migrations")]
        (gungnir.database/make-datasource! db-config)
        (gungnir.migration/rollback! migrations)
        (gungnir.migration/migrate! migrations)))

(comment
    (gungnir.migration/rollback! migrations)
    )

(comment
  (initialize-db!)
  )

