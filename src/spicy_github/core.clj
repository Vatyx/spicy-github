(ns spicy-github.core
    (:gen-class)
    (:require
        [ring.adapter.jetty :as jetty]
        [spicy-github.env :refer [spicy-env]]
        [spicy-github.db :as db]
        [spicy-github.logging :as logging]
        [spicy-github.scraper :as scraper]
        [spicy-github.api :as app]))

(defn app-port []
    (Integer/parseInt
        (if-let [env-port (spicy-env :front-end-port)]
            env-port
            "80")))

(defn -main
    [& args]
    (logging/initialize!)
    (db/initialize!)
    (.start (Thread. scraper/scrape-all-repositories))
    (.start (Thread. scraper/process-scraped-repositories))
    (jetty/run-jetty app/app {:port (app-port)}))
