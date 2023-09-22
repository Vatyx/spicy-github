(ns spicy-github.frontend
    (:gen-class)
    (:require
        [cheshire.core :refer :all]
        [rum.core :as rum]
        [stylefy.core :as stylefy]
        [spicy-github.db :as database]))

(defn frontend-initialize! [] (stylefy/init))

(defn style-index [issues]
    [:div {} (generate-string issues)])

(defn index []
    (rum/render-html (style-index (database/get-n-latest-issues!)) ))
