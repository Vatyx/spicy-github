(ns spicy-github.core
  (:gen-class)
    (:require
     [ring.adapter.jetty :as jetty] 
     [spicy-github.db :as db] 
     [spicy-github.api :as app]
     [spicy-github.spicy-rating :as rating]))

(defn -main
    [& args]
    (db/migrate-db!)
    (.start (Thread. rating/forever-rate-issues!))
    (.start (Thread. rating/forever-rate-comments!))
    (jetty/run-jetty app/app {:port 3000}))
