(ns spicy-github.core
  (:gen-class)
    (:require 
     [clojure.core.async :as async] 
     [ring.adapter.jetty :as jetty] 
     [spicy-github.db :as db] 
     [spicy-github.api :as app] 
     [spicy-github.model :as model] 
     [spicy-github.frontend :as frontend] 
     [spicy-github.scraper :as scraper]))

(defn -main
    [& _]
    (db/register-db!)
    (model/register-models!)
    (db/migrate-db!)
    (frontend/frontend-initialize!)
    ;(async/thread scraper/process)
    (jetty/run-jetty app/app {:port 3000}))
