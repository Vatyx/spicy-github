(ns spicy-github.adapters
    (:gen-class)
    (:require [cheshire.core :refer :all]
              [honey.sql.helpers :as h]
              [spicy-github.util :refer :all]
              [gungnir.query :as q])
    (:import (java.sql Date)))

(defn get-issue-id-for-issue-url [issue-url]
    (-> (h/where [:= :issue/url issue-url])
        (h/limit 1)
        (q/all! :issue)
        first
        :issue/id))

(defn get-repository-id-for-repository-url [repository-url]
    (-> (h/where [:= :repository/url repository-url])
        (h/limit 1)
        (q/all! :repository)
        first
        :repository/id))

(defn parse-repository [r]
    {:repository/id                  (-> r :id str)
     :repository/url                 (:url r)
     :repository/issues-url          (-> r :issues_url sanitize-github-url)
     :repository/processed-at        (Date. 0)
     :repository/github-json-payload (generate-string r)
     })

(defn parse-user [user-json]
    {:user/id         (-> user-json :id str)
     :user/avatar-url (:avatar_url user-json)
     :user/url        (:url user-json)
     })

(defn parse-user-from [json]
    (-> json
        :user
        parse-user))

(defn parse-issue [issue-json]
    {:issue/id                  (get-id-as-string issue-json)
     :issue/url                 (:url issue-json)
     :issue/comments-url        (-> issue-json :comments_url sanitize-github-url)
     :issue/repository-id       (-> issue-json :repository_url get-repository-id-for-repository-url)
     :issue/title               (:title issue-json)
     :issue/body                (->> issue-json :body (str ""))
     :issue/total-reactions     (-> issue-json :reactions :total_count int)
     :issue/comment-count       (-> issue-json :comments int)
     :issue/issue-creation-time (:created_at issue-json)
     :issue/issue-updated-time  (:updated_at issue-json)
     :issue/user-id             (-> issue-json :user :id str)
     :issue/github-json-payload (generate-string issue-json)
     })

(defn parse-comment [comment-json]
    {:comment/id                    (get-id-as-string comment-json)
     :comment/url                   (:url comment-json)
     :comment/body                  (->> comment-json :body (str ""))
     :comment/comment-creation-time (:created_at comment-json)
     :comment/comment-updated-time  (:updated_at comment-json)
     :comment/issue-id              (-> comment-json :issue_url get-issue-id-for-issue-url)
     :comment/user-id               (-> comment-json :user :id str)
     :comment/github-json-payload   (generate-string comment-json)
     })

(defn parse-comment-with-parent [[parent-json comment-json]]
    (let [comment-record (parse-comment comment-json)
          parent-id (get-id-as-string parent-json)]
        (if (not (empty? parent-id))
            (assoc comment-record :comment/parent-comment parent-id)
            comment-record)))

(defn parse-user-from-comment [comment] (-> comment :user parse-user))
(defn parse-user-from-issue [issue] (-> issue :user parse-user))
