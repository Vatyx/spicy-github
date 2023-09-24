(ns spicy-github.api
    (:gen-class)
    (:require [compojure.core :refer :all]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
              [ring.middleware.reload :refer [wrap-reload]]
              [spicy-github.frontend :as frontend]
              [clojure.pprint]))

(defn index-route [request]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (frontend/index)})

(defroutes app-routes
           (GET "/" [] index-route)
           (route/resources "/")
           (route/not-found "Not Found"))

(def app (wrap-reload (wrap-defaults app-routes site-defaults)))