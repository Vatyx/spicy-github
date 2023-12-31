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
        [spicy-github.api :as app]
        [clojure.tools.cli :as cli]
        [taoensso.timbre :as timbre]))

(defn app-port []
    (Integer/parseInt (load-env :front-end-port "PORT" :PORT "5000")))

(def cli-options
    [["-s" "--scrape SCRAPE" "Scrape github"
      :default false
      :parse-fn #(Boolean/parseBoolean %)]
     ["-r" "--remap REMAP" "Remap issues and comments"
      :default false
      :parse-fn #(Boolean/parseBoolean %)]
     ["-h" "--help"]])

(defn -main
    [& args]
    (logging/initialize!)
    (db/initialize!)
    (let [opts (cli/parse-opts args cli-options)]
        (when (-> opts :options :scrape)
            (timbre/info "Beginning github scraping...")
            (.start (Thread. scraper/scrape-all-repositories))
            (.start (Thread. scraper/process-scraped-repositories)))
        (when (or (-> opts :options :remap) (dev/should-remap-db))
            (.start (Thread. dev/remap-db!))))
    (.start (Thread. spicy-rating/forever-rate-issues!))
    (.start (Thread. spicy-rating/forever-rate-comments!))
    (.start (Thread. spicy-rating/forever-migrate-highly-rated-comments!))
    (jetty/run-jetty (app/app) {:port (app-port)}))