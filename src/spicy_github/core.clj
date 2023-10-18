(ns spicy-github.core
  (:gen-class)
    (:require
     [ring.adapter.jetty :as jetty]
        ; Required import to force db and model registration
     [spicy-github.db :as db]
     [spicy-github.logging :as logging]
     [spicy-github.scraper :as scraper]
     [spicy-github.api :as app]
     [spicy-github.spicy-rating :as rating]))

(defn -main
    [& args]
    (.start (Thread. scraper/scrape-all-repositories))
    (.start (Thread. scraper/process-scraped-repositories))
    (jetty/run-jetty app/app {:port 3000}))
