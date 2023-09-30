(ns spicy-github.api
    (:gen-class)
    (:require [compojure.core :refer :all]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
              [ring.middleware.reload :refer [wrap-reload]]
              [spicy-github.frontend :as frontend]
              [spicy-github.db :as db]
              [cheshire.core :refer :all]
              [clojure.pprint]))

(defn index-route [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     ; TODO: Change this to serve up our new frontend
     :body    (frontend/index)})

(defn get-n-latest-issues-after [after]
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (generate-string (db/get-n-latest-issues! after))})

(defroutes app-routes
           (GET "/" [] index-route)
           (GET "/latest-issues-after/:after" [after] #(get-n-latest-issues-after after))
           (route/resources "/")
           (route/not-found "Not Found"))

(def app (wrap-reload (wrap-defaults app-routes site-defaults)))