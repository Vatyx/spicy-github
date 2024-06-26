(ns front-end.api
    (:require-macros [cljs.core.async.macros :refer [go]]
                     [spicy-github.env :refer [spicy-env]])
    (:require [cljs-http.client :as http]
              [cljs.core.async :refer [<!]]))

(def issue-count 5)
(def minimum-spicy-score 5)

(def spicy-endpoint (str (spicy-env :spicy-endpoint)
                         (let [port (spicy-env :front-end-port)]
                             (if (= port "80")
                                 ""
                                 (str ":" port)))
                         "/"))

(defn spicy-random-endpoint
    ([] (spicy-random-endpoint issue-count))
    ([n] (str spicy-endpoint "random-issues/" n)))

(defn- get-n-issues-before-endpoint
    ([] (get-n-issues-before-endpoint (.now js/Date)))
    ([before] (str spicy-endpoint "latest-issues/" (.toISOString (js/Date. before)))))

(defn- get-issues-before-get-response [endpoint response-fn is-loading-issues]
    (go (let [response (<! (http/get endpoint {}))]
            (reset! is-loading-issues false)
            (when (== 200 (:status response))
                (response-fn (js->clj (:body response) :keywordize-keys true))))))

(defn- get-n-issues-before
    ([response-fn is-loading-issues] (get-issues-before-get-response (get-n-issues-before-endpoint) response-fn is-loading-issues))
    ([response-fn is-loading-issues before] (get-issues-before-get-response (get-n-issues-before-endpoint before) response-fn is-loading-issues)))

(defn- get-n-random-issues [response-fn is-loading-issues]
    (get-issues-before-get-response (spicy-random-endpoint) response-fn is-loading-issues))

(defn get-issues [response-fn is-loading-issues]
    (get-n-random-issues response-fn is-loading-issues))