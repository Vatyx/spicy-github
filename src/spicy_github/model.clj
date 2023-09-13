(ns spicy-github.model
  (:gen-class)
    (:require [gungnir.model]))

(def repository-model
    [:map
     [:repository/id {:primary-key true} uuid?]
     [:repository/url string?]
     [:repository/processed boolean?]
     [:repository/created-at {:auto true} inst?]
     [:repository/updated-at {:auto true} inst?]])

(def user-model
  [:map
   {:table :github-user
    :has-many {:user/comments {:model :comment :foreign-key :comment/user-id}}}
   [:user/id {:primary-key true} uuid?]
   [:user/user-id string?]
   [:user/avatar-url string?]
   [:user/created-at {:auto true} inst?]
   [:user/updated-at {:auto true} inst?]])

(def comment-model
  [:map
   {:has-one {:comment/parent {:model :comment :foreign-key :comment/parent-comment}}
    :belongs-to {:comment/user {:model :user :foreign-key :comment/user-id}}}
   [:comment/id {:primary-key true} uuid?]
   [:comment/url string?]
   [:comment/body string?]
   [:comment/comment-creation-time inst?]
   [:comment/github-json-payload string?]
   [:comment/issue-id uuid?]
   [:comment/created-at {:auto true} inst?]
   [:comment/updated-at {:auto true} inst?]])

(def issue-model
    [:map
     {:has-many {:issue/comments {:model :comment :foreign-key :comment/issue-id}}}
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
                              :issue issue-model
                              :user user-model
                              :comment comment-model}))