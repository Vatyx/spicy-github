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
              [spicy-github.util :refer [load-env]]
              [spicy-github.env :refer [spicy-env]]
              [taoensso.timbre :as timbre])
    (:import (java.time Instant)))

(defn- landing-page [request]
    (timbre/info (str request))
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    frontend/index-html})

(defn- not-found [request]
    {:status 404})

(def minimum-count 10)

(defn get-n-random-issues [n]
    (timbre/info "Received n-random issues with n: " n)
    (generate-string
        (map adapters/sanitize-issue-for-api
             (if (nil? n)
                 (db/accumulate-until-at-least (partial db/get-n-random-issues-from-highly-rated-comments! minimum-count) minimum-count)
                 (let [wrapped-count (max minimum-count (min 50 (parse-long n)))]
                     (db/accumulate-until-at-least (partial db/get-n-random-issues-from-highly-rated-comments! wrapped-count) wrapped-count))))))

(defn get-n-latest-issues-before! [before]
    (timbre/info "Received n-latest-issues-before:" (str before))
    (generate-string
        (map adapters/sanitize-issue-for-api
             (db/get-n-latest-issues-before!
                 (if (nil? before)
                     (Instant/now)
                     (instant/read-instant-date before))))))

(defn- get-n-latest-issues-before-api! [before]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (get-n-latest-issues-before! before)})

(defn- get-n-random-issues-api! [n]
    {:status  200
     :headers {"Content-Type" "application/json"}
     :body    (get-n-random-issues n)})

(defn- get-comments-for-issues
    ([issue-id] (get-comments-for-issues issue-id 0))
    ([issue-id offset]
     (let [comment-count (db/get-comment-count-for-issue issue-id)]
         {:total-count comment-count
          :items       (db/get-comments-for-issue issue-id offset)})))

(defn- get-ranked-issues [offset ranked-reactions]
    {:items (db/get-ranked-issues (Integer/parseInt offset) ranked-reactions)})

(defn- get-ranked-comments [offset ranked-reactions]
    {:items (db/get-ranked-comments (Integer/parseInt offset) ranked-reactions)})

(defn- get-comments-response-for-issues [request]
    (let [body (let [params (:params request)]
                   (cond (and (:issue-id params) (:offset params)) (get-comments-for-issues (:issue-id params) (:offset params))
                         (:issue-id params) (get-comments-for-issues (:issue-id params))
                         :else nil))]
        {:status  (if (nil? body) 404 200)
         :headers {"Content-Type" "application/json"}
         :body    (if (nil? body) {} body)}))

(defroutes app-routes
           (GET "/" [] landing-page)
           (GET "/latest-issues/:before" [before] (get-n-latest-issues-before-api! before))
           (GET "/random-issues/" [] (get-n-random-issues-api! (str minimum-count)))
           (GET "/random-issues/:n" [n] (get-n-random-issues-api! n))
           (GET "/comments" request (get-comments-response-for-issues request))
           (GET "/ranked-issues" request
               (let [params (:params request)]
                   (cond (and (:reaction params) (:offset params)) (get-ranked-issues (:offset params) (:reaction params))
                         (:reaction params) (get-ranked-issues 0 (:reaction params))
                         :else (not-found request))))
           (GET "/ranked-comments" request
               (let [params (:params request)]
                   (cond (and (:reaction params) (:offset params)) (get-ranked-comments (:offset params) (:reaction params))
                         (:reaction params) (get-ranked-comments 0 (:reaction params))
                         :else (not-found request))))
           (route/resources "/")
           (route/not-found "Not Found"))

(defn- app-with-defaults [reload-server]
    (timbre/info (str "Loading application (reload server:" reload-server ")"))
    (wrap-defaults app-routes site-defaults))

(defn app []
    (let [reload-server (parse-boolean (load-env :reload-server "RELOAD_SERVER" :RELOAD_SERVER "false"))]
        (if (nil? reload-server)
            (app-with-defaults false)
            (if reload-server
                (wrap-reload (app-with-defaults true))
                (app-with-defaults false)))))
