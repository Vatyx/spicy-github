(ns spicy-github.core
    (:gen-class)
    (:require
        [ring.adapter.jetty :as jetty]
        [spicy-github.util :refer [load-env]]
        [spicy-github.db :as db]
        [spicy-github.logging :as logging]
        [spicy-github.scraper :as scraper]
        [spicy-github.api :as app]))

(defn app-port []
    (Integer/parseInt (load-env :front-end-port "FRONT_END_PORT" :FRONT_END_PORT "80")))

(defn -main
    [& args]
    (logging/initialize!)
    (db/initialize!)
    (.start (Thread. scraper/scrape-all-repositories))
    (.start (Thread. scraper/process-scraped-repositories))
    (jetty/run-jetty app/app {:port (app-port)}))
