(ns spicy-github.api
    (:gen-class)
    (:require [compojure.core :refer :all]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
              [ring.middleware.reload :refer [wrap-reload]]
              [spicy-github.frontend :as frontend]
              [spicy-github.adapters :as adapters]
              [spicy-github.db :as db]
              [cheshire.core :refer :all]
              [clojure.instant :as instant]
              [clojure.pprint]
              [spicy-github.env :refer [spicy-env]]
              [taoensso.timbre :as timbre])
    (:import (java.util Date)))

(defn- landing-page [request]
    (timbre/info (str request))
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    frontend/index-html})

(defn get-n-latest-issues-before! [before]
    (timbre/info "Received n-latest-issues-before:" (str before))
    (generate-string
        (map adapters/sanitize-issue-for-api
             (db/get-n-latest-issues-before!
                 (if (nil? before)
                     (new Date)
                     (instant/read-instant-date before))))))

(defn- get-n-latest-issues-before-api! [before]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (get-n-latest-issues-before! before)})

(defroutes app-routes
           (GET "/" [] landing-page)
           (GET "/latest-issues/:before" [before] (get-n-latest-issues-before-api! before))
           (route/resources "/")
           (route/not-found "Not Found"))

(def app (let [reload-server (parse-boolean (spicy-env :reload-server))]
             (if (nil? reload-server)
                 (wrap-defaults app-routes site-defaults)
                 (if reload-server
                     (wrap-reload (wrap-defaults app-routes site-defaults))
                     (wrap-defaults app-routes site-defaults))
                 )))
