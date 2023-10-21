(ns spicy-github.api
    (:require-macros [cljs.core.async.macros :refer [go]]
                     [spicy-github.env :refer [spicy-env]])
    (:require [cljs-http.client :as http]
              [cljs.core.async :refer [<!]]))

(def spicy-endpoint (str (spicy-env :spicy-endpoint) ":" (spicy-env :front-end-port) "/"))

(defn- get-n-issues-before-endpoint
    ([] (get-n-issues-before-endpoint (.now js/Date)))
    ([before] (str spicy-endpoint "latest-issues/" (.toISOString (js/Date. before)))))

(defn- get-n-issues-before-get-response [endpoint response-fn is-loading-issues]
    (go (let [response (<! (http/get endpoint {}))]
            (reset! is-loading-issues false)
            (when (== 200 (:status response))
                (response-fn (js->clj (:body response) :keywordize-keys true))))))

(defn get-n-issues-before
    ([response-fn is-loading-issues] (get-n-issues-before-get-response (get-n-issues-before-endpoint) response-fn is-loading-issues))
    ([response-fn is-loading-issues before] (get-n-issues-before-get-response (get-n-issues-before-endpoint before) response-fn is-loading-issues)))

(defn get-n-issues-before-from-issues [response-fn issues is-loading-issues]
    (if (empty? issues)
        (get-n-issues-before response-fn is-loading-issues)
        (get-n-issues-before response-fn is-loading-issues (js/Date. (:issue/updated-at (last issues))))))