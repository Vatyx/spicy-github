(ns spicy-github.adapters
    (:gen-class)
    (:require
        [clojure.data.json :as json]))

(defn parse-user [user-json]
    {:user/id (-> user-json :id str)
     :user/avatar-url (:avatar_url user-json)
     :user/url (:url user-json)
     })

(defn parse-issue [issue-json]
    {:issue/id (-> issue-json :id str)
     :issue/url (:url issue-json)
     :issue/title (:title issue-json)
     :issue/body (:body issue-json)
     :issue/total-reactions (-> issue-json :reactions :total_count int)
     :issue/comment-count (-> issue-json :comments int)
     :issue/issue-creation-time (:created_at issue-json)
     :issue/issue-updated-time (:updated_at issue-json)
     :issue/user-id (-> issue-json :user :id str)
     :issue/github-json-payload (json/write-str issue-json)
     })

(defn parse-user-from-comment [comment] (-> comment :user parse-user))
(defn parse-user-from-issue [issue] (-> issue :user parse-user))