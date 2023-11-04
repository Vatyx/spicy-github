(ns spicy-github.core
    (:gen-class)
    (:require
        [ring.adapter.jetty :as jetty]
        [spicy-github.util :refer [load-env]]
        [spicy-github.db :as db]
        [spicy-github.logging :as logging]
        [spicy-github.scraper :as scraper]
        [spicy-github.spicy-rating :as spicy-rating]
        [spicy-github.dev :as dev]
        [spicy-github.api :as app]))

(defn app-port []
    (Integer/parseInt (load-env :front-end-port "PORT" :PORT "5000")))

(defn -main
    [& args]
    (logging/initialize!)
    (db/initialize!)
    ;(.start (Thread. scraper/scrape-all-repositories))
    ;(.start (Thread. scraper/process-scraped-repositories))
    (.start (Thread. spicy-rating/forever-rate-issues!))
    (.start (Thread. spicy-rating/forever-rate-comments!))
    ;(when (dev/should-remap-db) (.start (Thread. dev/remap-db!)))
    (jetty/run-jetty (app/app) {:port (app-port)}))