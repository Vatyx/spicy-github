(ns spicy-github.core
  (:gen-class)
  (:require [ring.adapter.jetty :as jetty]
            [spicy-github.api]))

(defn -main
  [& args]
  (jetty/run-jetty spicy-github.api/app
                   {:port 3000
                    :join? true}))