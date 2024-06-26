(ns spicy-github.api
    (:gen-class)
    (:require [compojure.core :refer :all]
              [compojure.route :as route]
              [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
              [ring.middleware.cors :refer [wrap-cors]]
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
        (map #(adapters/sanitize-issue-for-api % true)
             (if (nil? n)
                 (db/accumulate-until-at-least (partial db/get-n-random-issues-from-highly-rated-comments! minimum-count) minimum-count)
                 (let [wrapped-count (max minimum-count (min 50 (parse-long n)))]
                     (db/accumulate-until-at-least (partial db/get-n-random-issues-from-highly-rated-comments! wrapped-count) wrapped-count))))))

(defn get-n-latest-issues-before! [before]
    (timbre/info "Received n-latest-issues-before:" (str before))
    (generate-string
        (map #(adapters/sanitize-issue-for-api % true)
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
     (timbre/info "Received get comments for issues command with offset" offset "and issue-id" issue-id)
     (let [comment-count (db/get-comment-count-for-issue issue-id)]
         {:total-count comment-count
          :items       (map adapters/sanitize-comment-for-api-v2 (db/get-comments-for-issue issue-id offset))})))

(defn- get-ranked-issues [offset ranked-reactions]
    (timbre/info "Received ranked issues command with offset" offset "and ranked reactions" ranked-reactions)
    (generate-string {:items (map adapters/sanitize-issue-for-api-v2 (db/get-ranked-issues offset ranked-reactions))}))

(defn- get-ranked-comments [offset ranked-reactions]
    (timbre/info "Received ranked comments command with offset" offset "and ranked reactions" ranked-reactions)
    (generate-string {:items (map adapters/sanitize-comment-for-api-v2 (db/get-ranked-comments offset ranked-reactions))}))

(defn- get-comments-response-for-issues [request]
    (generate-string
        (let [body (let [params (:params request)]
                       (cond (and (:issue-id params) (:offset params)) (get-comments-for-issues (:issue-id params) (Integer/parseInt (:offset params)))
                             (:issue-id params) (get-comments-for-issues (:issue-id params))
                             :else nil))]
            (if (nil? body)
                (not-found request)
                {:status  200
                 :headers {"Content-Type" "application/json"}
                 :body    body}))))

(defroutes app-routes
           (GET "/" [] landing-page)
           (GET "/latest-issues/:before" [before] (get-n-latest-issues-before-api! before))
           (GET "/random-issues/" [] (get-n-random-issues-api! (str minimum-count)))
           (GET "/random-issues/:n" [n] (get-n-random-issues-api! n))
           (GET "/comments" request (get-comments-response-for-issues request))
           (GET "/ranked-issues/" [] (get-ranked-issues 0 []))
           (GET "/ranked-issues" request
               (let [params (:params request)]
                   (cond (and (:reaction params) (:offset params)) (get-ranked-issues (Integer/parseInt (:offset params)) [(:reaction params)])
                         (:reaction params) (get-ranked-issues 0 [(:reaction params)])
                         :else (not-found request))))
           (GET "/ranked-comments/" [] (get-ranked-comments 0 []))
           (GET "/ranked-comments" request
               (let [params (:params request)]
                   (cond (and (:reaction params) (:offset params)) (get-ranked-comments (Integer/parseInt (:offset params)) [(:reaction params)])
                         (:reaction params) (get-ranked-comments 0 [(:reaction params)])
                         :else (not-found request))))
           (route/resources "/")
           (route/not-found "Not Found"))

(defn- app-with-defaults [reload-server]
    (timbre/info (str "Loading application (reload server:" reload-server ")"))
    (wrap-cors (wrap-defaults app-routes site-defaults)
               :access-control-allow-credentials "true"
               :access-control-allow-origin [#".*"]
               :access-control-allow-headers #{"accept" "accept-encoding" "accept-language" "authorization" "content-type" "origin"}
               :access-control-allow-methods [:get]))

(defn app []
    (wrap-reload #'app-routes))

(comment

    (defn app []
     (let [reload-server (parse-boolean (load-env :reload-server "RELOAD_SERVER" :RELOAD_SERVER "false"))]
         (if (nil? reload-server)
             (app-with-defaults false)
             (if reload-server
                 (wrap-reload (app-with-defaults true))
                 (app-with-defaults false)))))
    )
