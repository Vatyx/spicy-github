(ns spicy-github.model
  (:gen-class)
  (:require [gungnir.model])
  (:import (java.time Instant)))

(defmethod gungnir.model/before-save :get-current-time [_k _v] (Instant/now))

(def repository-model
    [:map
     [:repository/id {:primary-key true} uuid?]
     [:repository/url string?]
     [:repository/processed boolean?]
     [:repository/created-at {:auto true} inst?]
     [:repository/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def user-model
  [:map
   {:table :github-user
    :has-many {:user/comments {:model :comment :foreign-key :comment/user-id}}}
   [:user/id {:primary-key true} string?]
   [:user/avatar-url string?]
   [:user/url string?]
   [:user/created-at {:auto true} inst?]
   [:user/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def comment-model
  [:map
   {:has-one {:comment/parent {:model :comment :foreign-key :comment/parent-comment}}
    :belongs-to {
                 :comment/user {:model :user :foreign-key :comment/user-id}
                 :comment/issue {:model :issue :foreign-key :comment/issue-id}
                 }}
   [:comment/id {:primary-key true} string?]
   [:comment/parent-comment {:optional true} string?]
   [:comment/url string?]
   [:comment/body string?]
   [:comment/comment-creation-time inst?]
   [:comment/issue-id {:optional true} string?]
   [:comment/user-id {:optional true} string?]
   [:comment/comment-updated-time inst?]
   [:comment/github-json-payload string?]
   [:comment/created-at {:auto true} inst?]
   [:comment/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def issue-model
    [:map
     {:has-many {:issue/comments {:model :comment :foreign-key :comment/issue-id}}}
     [:issue/id {:primary-key true} string?]
     [:issue/url string?]
     [:issue/title string?]
     [:issue/body string?]
     [:issue/total-reactions int?]
     [:issue/comment-count int?]
     [:issue/issue-creation-date inst?]
     [:issue/github-json-payload string?]
     [:issue/created-at {:auto true} inst?]
     [:issue/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(defn register-models! []
    (gungnir.model/register! {:repository repository-model
                              :issue issue-model
                              :user user-model
                              :comment comment-model}))