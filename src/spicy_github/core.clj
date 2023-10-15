(ns spicy-github.core
  (:gen-class)
    (:require
     [ring.adapter.jetty :as jetty] 
     [spicy-github.db :as db] 
     [spicy-github.api :as app] 
     [spicy-github.model :as model]))

(defn -main
    [& args]
    (db/migrate-db!)
    (jetty/run-jetty app/app {:port 3000}))
