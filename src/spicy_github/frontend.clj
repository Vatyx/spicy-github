(ns spicy-github.frontend
    (:gen-class)
    (:require
        [stylefy.core :as stylefy]))

(defn frontend-initialize! [] (stylefy/init))