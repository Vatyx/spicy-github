(ns spicy-github.core
  (:gen-class)
    (:require [ring.adapter.jetty :as jetty]
              [spicy-github.scraper]
              [clojure.core.async :as a]
              [spicy-github.api]))

(defn -main
    [& _]
    (jetty/run-jetty spicy-github.api/app {:port 3000}))