(ns spicy-github.core
    (:gen-class)
    (:require
        [ring.adapter.jetty :as jetty]
        [spicy-github.util :refer [load-env]]
        [spicy-github.db :as db]
        [spicy-github.logging :as logging]
        [spicy-github.scraper :as scraper]
        [spicy-github.dev :as dev]
        [spicy-github.api :as app]
        [spicy-github.spicy-rating :as spicy-rating]
        [clojure.tools.cli :as cli]
        [taoensso.timbre :as timbre]))

(def app-port
    (Integer/parseInt (load-env :front-end-port)))

(def cli-options
    [["-s" "--scrape SCRAPE" "Scrape github"
      :default false
      :parse-fn #(Boolean/parseBoolean %)]
     ["-r" "--remap REMAP" "Remap issues and comments"
      :default false
      :parse-fn #(Boolean/parseBoolean %)]])

(defn start-scraper! []
    (timbre/info "Beginning github scraping...")
    (.start (Thread. scraper/scrape-all-repositories))
    (.start (Thread. scraper/process-scraped-repositories)))

(defn start-remapper! []
    (.start (Thread. dev/remap-db!)))

(defn start-rating! []
    (.start (Thread. spicy-rating/forever-rate-issues!))
    (.start (Thread. spicy-rating/forever-rate-comments!))
    (.start (Thread. spicy-rating/forever-migrate-highly-rated-comments!)))

(defn start-web-server! []
    (timbre/info "Starting application server on port" app-port)
    (jetty/run-jetty (app/app) {:port app-port :join? false}))

(defn has-option [args option]
    (let [opts (cli/parse-opts args cli-options)]
        (-> opts
            :option
            option)))

; Exists outside of -main so it also runs when starting a REPL
(logging/initialize!)
(db/initialize!)

(defn -main [& args]
    (when (has-option args :scrape)
        (start-scraper!))

    (when (or (has-option args :remap) (dev/should-remap-db))
        (start-remapper!))

    (start-web-server!))

