(ns spicy-github.api
    (:require-macros [cljs.core.async.macros :refer [go]]
                     [spicy-github.env :refer [cljs-env]])
    (:require [cljs-http.client :as http]
              [cljs.core.async :refer [<!]]))

(def spicy-endpoint (cljs-env :spicy-endpoint))

(defn- get-n-issues-before-endpoint
    ([] (get-n-issues-before-endpoint (.now js/Date)))
    ([before] (str spicy-endpoint "latest-issues/" (.toISOString (js/Date. before)))))

(defn- get-n-issues-before-get-response [endpoint response-fn]
    (go (let [response (<! (http/get endpoint {}))]
            (when (== 200 (:status response))
                (response-fn (js->clj (:body response) :keywordize-keys true))))))

(defn get-n-issues-before
    ([response-fn] (get-n-issues-before-get-response (get-n-issues-before-endpoint) response-fn))
    ([response-fn before] (get-n-issues-before-get-response (get-n-issues-before-endpoint before) response-fn)))

(defn get-n-issues-before-from-issues
    ([response-fn issues] (if (empty issues)
                              (get-n-issues-before response-fn)
                              (get-n-issues-before (:updated-at (issues (- (count issues) 1))) response-fn))))