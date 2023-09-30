(ns spicy-github.core
  (:gen-class)
    (:require
     [ring.adapter.jetty :as jetty] 
     [spicy-github.db :as db] 
     [spicy-github.api :as app] 
     [spicy-github.model :as model]))

(defn initialize! []
    (db/register-db!)
    (model/register-models!))

(defn -main
    [& args]
    (initialize!)
    (db/migrate-db!)
    (jetty/run-jetty app/app {:port 3000}))
