(ns spicy-github.core
  (:gen-class)
    (:require [ring.adapter.jetty :as jetty]
              [spicy-github.db :as db]
              [spicy-github.scraper]
              [clojure.core.async :as a]
              [spicy-github.api]
              [spicy-github.model :as model]))

(defn -main
    [& _]
    (db/register-db!)
    (model/register-models!)

    (a/thread spicy-github.scraper/process-repositories)
    (jetty/run-jetty spicy-github.api/app {:port 3000}))