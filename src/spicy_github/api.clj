(ns spicy-github.api
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.pprint]))

(defn index [request]
  (clojure.pprint/pprint request)
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body "{\"Contents\": \"Hello world\"}"})

(defroutes app-routes
           (GET "/" []  index)
           (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))