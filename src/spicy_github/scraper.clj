(ns spicy-github.scraper
  (:gen-class)
    (:require [clojure.java.io :as io]
              [spicy-github.db :as db]
              [spicy-github.model :as model]
              [spicy-github.adapters :as adapters]
              [spicy-github.util :refer :all]
              [malli.dev.pretty]
              [clj-http.client :as client]
              [cheshire.core :refer :all]
              [clojure.edn :as edn]
              [gungnir.model]
              [gungnir.query :as q]
              [gungnir.changeset :as changeset]
              [throttler.core :refer [throttle-fn]]))

(defn get-github-token []
    (-> (io/resource "token.edn")
        io/file
        slurp
        edn/read-string
        :github-token))
(def get-url (throttle-fn client/get 5000 :hour))

(def github-token (get-github-token))

(defn get-github-url [url]
    (get-url url {:headers {"Authorization" (str "Bearer " github-token)}}))

(get-github-url "https://api.github.com/repos/dakrone/cheshire")

(defn process-repositories []
    (forever
         (Thread/sleep 1000)
         (println "hello")))

(defn get-repositories []
    (q/all! :repository))
