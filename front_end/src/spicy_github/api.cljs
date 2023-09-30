(ns spicy-github.api
    (:require  [environ.core :refer [env]]))

(def spicy-endpoint (env :spicy-endpoint))

(println (str "Hello world! Spicy Endpoint: '" spicy-endpoint "'"))

(defn get-n-latest-issues! [after]
    )