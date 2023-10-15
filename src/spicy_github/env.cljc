(ns spicy-github.env
    (:require [environ.core :refer [env]]))

(defmacro spicy-env [kw] (env kw))