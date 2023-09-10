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

(def issue-model
    [:map
     [:issue/id {:primary-key true} uuid?]
     [:issue/url string?]
     [:issue/title string?]
     [:issue/body string?]
     [:issue/total-reactions int?]
     [:issue/comment-count int?]
     [:issue/issue-creation-date inst?]
     [:issue/github-json-payload string?]
     [:issue/created-at {:auto true} inst?]
     [:issue/updated-at {:auto true} inst?]])

(defn register-models! []
    (gungnir.model/register! {:repository repository-model
                              :issue issue-model}))

(register-models!)