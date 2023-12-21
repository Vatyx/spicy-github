(ns spicy-github.model
    (:gen-class)
    (:require [gungnir.model]
              [spicy-github.util :refer :all])
    (:import (java.time Instant)))

(defmethod gungnir.model/before-save :get-current-time [_k _v] (Instant/now))
(defmethod gungnir.model/before-save :sanitize-github-url [_k v] (sanitize-github-url v))

(def repository-model
    [:map
     {:has-many {:repository/issues {:model :issue :foreign-key :issue/repository-id}}}
     [:repository/id {:primary-key true} string?]
     [:repository/url string?]
     [:repository/issues-url {:before-save [:sanitize-github-url]} string?]
     [:repository/stars int?]
     [:repository/processed-at inst?]
     [:repository/github-json-payload string?]
     [:repository/created-at {:auto true} inst?]
     [:repository/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def user-model
    [:map
     {:table    :github-user
      :has-many {:user/comments {:model :comment :foreign-key :comment/user-id}}}
     [:user/id {:primary-key true} string?]
     [:user/avatar-url string?]
     [:user/url string?]
     [:user/created-at {:auto true} inst?]
     [:user/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def comment-model
    [:map
     {:has-one    {:comment/parent               {:model :comment :foreign-key :comment/parent-comment}
                   :comment/spicy-comment        {:model :spicy-comment :foreign-key :spicy-comment/id}
                   :comment/highly-rated-comment {:model :highly-rated-comment :foreign-key :highly-rated-comment/id}}
      :belongs-to {:comment/user  {:model :user :foreign-key :comment/user-id}
                   :comment/issue {:model :issue :foreign-key :comment/issue-id}}}
     [:comment/id {:primary-key true} string?]
     [:comment/parent-comment {:optional true} string?]
     [:comment/url string?]
     [:comment/html-url {:optional true} string?]
     [:comment/body string?]
     [:comment/total-reactions int?]
     [:comment/comment-creation-time inst?]
     [:comment/comment-updated-time {:optional true} inst?]
     [:comment/issue-id {:optional true} string?]
     [:comment/user-id {:optional true} string?]
     [:comment/reaction-json string?]
     [:comment/github-json-payload string?]
     [:comment/created-at {:auto true} inst?]
     [:comment/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def issue-model
    [:map
     {:has-one    {:issue/spicy-issue {:model :spicy-issue :foreign-key :spicy-issue/id}}
      :has-many   {:issue/comments              {:model :comment :foreign-key :comment/issue-id}
                   :issue/spicy-comments        {:model :spicy-comment :foreign-key :spicy-comment/issue-id}
                   :issue/highly-rated-comments {:model :highly-rated-comment :foreign-key :highly-rated-comment/issue-id}}
      :belongs-to {:issue/user       {:model :user :foreign-key :issue/user-id}
                   :issue/repository {:model :repository :foreign-key :issue/repository-id}}}
     [:issue/id {:primary-key true} string?]
     [:issue/url string?]
     [:issue/html-url {:optional true} string?]
     [:issue/title string?]
     [:issue/body string?]
     [:issue/comments-url {:before-save [:sanitize-github-url]} string?]
     [:issue/total-reactions int?]
     [:issue/comment-count int?]
     [:issue/issue-creation-time inst?]
     [:issue/issue-updated-time {:optional true} inst?]
     [:issue/user-id {:optional true} string?]
     [:issue/repository-id {:optional true} string?]
     [:issue/reaction-json string?]
     [:issue/github-json-payload string?]
     [:issue/created-at {:auto true} inst?]
     [:issue/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def spicy-issue-model
    [:map
     {:belongs-to {:spicy-issue/issue {:model :issue :foreign-key :spicy-issue/id}}}
     [:spicy-issue/id {:primary-key true} string?]
     [:spicy-issue/total-rating float?]
     [:spicy-issue/controversial-rating float?]
     [:spicy-issue/funny-rating float?]
     [:spicy-issue/agreeable-rating float?]
     [:spicy-issue/created-at {:auto true} inst?]
     [:spicy-issue/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def spicy-comment-model
    [:map
     {:belongs-to {:spicy-comment/comment {:model :comment :foreign-key :spicy-comment/id}
                   :spicy-comment/issue   {:model :issue :foreign-key :spicy-comment/issue-id}}}
     [:spicy-comment/id {:primary-key true} string?]
     [:spicy-comment/total-rating float?]
     [:spicy-comment/controversial-rating float?]
     [:spicy-comment/funny-rating float?]
     [:spicy-comment/agreeable-rating float?]
     [:spicy-comment/issue-id {:optional true} string?]
     [:spicy-comment/created-at {:auto true} inst?]
     [:spicy-comment/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(def highly-rated-comment-model
    [:map
     {:belongs-to {:highly-rated-comment/comment {:model :comment :foreign-key :highly-rated-comment/id}
                   :highly-rated-comment/issue   {:model :issue :foreign-key :highly-rated-comment/issue-id}}}
     [:highly-rated-comment/id {:primary-key true} string?]
     [:highly-rated-comment/total-rating float?]
     [:highly-rated-comment/created-at {:auto true} inst?]
     [:highly-rated-comment/issue-id {:optional true} string?]
     [:highly-rated-comment/updated-at {:before-save [:get-current-time] :optional true} inst?]])

(defn register-models! []
    (gungnir.model/register! {:repository           repository-model
                              :issue                issue-model
                              :user                 user-model
                              :comment              comment-model
                              :spicy-comment        spicy-comment-model
                              :spicy-issue          spicy-issue-model
                              :highly-rated-comment highly-rated-comment-model}))

(register-models!)