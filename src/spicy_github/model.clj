(ns spicy-github.model
  (:gen-class)
    (:require [gungnir.model]))

(defrecord SpicyIssue [id title body total-reactions created-at comment-count github-json-payload])

(def repository-model
    [:map
     [:repository/id {:primary-key true} uuid?]
     [:repository/url string?]
     [:repository/processed boolean?]
     [:repository/created-at {:auto true} inst?]
     [:repository/updated-at {:auto true} inst?]])

(defn register-models! []
    (gungnir.model/register! {:repository repository-model}))

(register-models!)