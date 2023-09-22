(ns spicy-github.api
    (:gen-class)
    (:require [compojure.core :refer :all]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
              [spicy-github.frontend :as frontend]
              [clojure.pprint]))

(defn index-route [request]
    (clojure.pprint/pprint request)
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (frontend/index)})

(defroutes app-routes
           (GET "/" [] index-route)
           (route/not-found "Not Found"))

(def app (wrap-defaults app-routes site-defaults))