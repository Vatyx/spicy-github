(ns spicy-github.adapters
    (:gen-class)
    (:require [cheshire.core :refer :all]
              [honey.sql.helpers :as h]
              [spicy-github.util :refer :all]
              [gungnir.query :as q]))

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
     :repository/stars               (:stargazers_count r)
     :repository/processed-at        (java.sql.Date. 0)
     :repository/github-json-payload (generate-string r)})

(defn parse-user [user-json]
    {:user/id         (-> user-json :id str)
     :user/avatar-url (:avatar_url user-json)
     :user/url        (:url user-json)})

(defn parse-user-from [json]
    (-> json
        :user
        parse-user))

(defn parse-reactions-from-json [json]
    (-> json
        :reactions
        (dissoc :url :total_count)
        generate-string))

(defn parse-issue [issue-json]
    {:issue/id                  (get-id-as-string issue-json)
     :issue/url                 (:url issue-json)
     :issue/html-url            (:html_url issue-json)
     :issue/comments-url        (-> issue-json :comments_url sanitize-github-url)
     :issue/repository-id       (-> issue-json :repository_url get-repository-id-for-repository-url)
     :issue/title               (:title issue-json)
     :issue/body                (->> issue-json :body (str ""))
     :issue/total-reactions     (-> issue-json :reactions :total_count int)
     :issue/comment-count       (-> issue-json :comments int)
     :issue/issue-creation-time (:created_at issue-json)
     :issue/issue-updated-time  (:updated_at issue-json)
     :issue/user-id             (-> issue-json :user :id str)
     :issue/reaction-json       (parse-reactions-from-json issue-json)
     :issue/github-json-payload (generate-string issue-json)})

(defn parse-comment [comment-json]
    {:comment/id                    (get-id-as-string comment-json)
     :comment/url                   (:url comment-json)
     :comment/html-url              (:html_url comment-json)
     :comment/body                  (->> comment-json :body (str ""))
     :comment/comment-creation-time (:created_at comment-json)
     :comment/comment-updated-time  (:updated_at comment-json)
     :comment/issue-id              (-> comment-json :issue_url get-issue-id-for-issue-url)
     :comment/user-id               (-> comment-json :user :id str)
     :comment/total-reactions       (-> comment-json :reactions :total_count int)
     :comment/reaction-json         (parse-reactions-from-json comment-json)
     :comment/github-json-payload   (generate-string comment-json)})

(defn parse-comment-with-parent [[parent-json comment-json]]
    (let [comment-record (parse-comment comment-json)
          parent-id (get-id-as-string parent-json)]
        (if (not (empty? parent-id))
            (assoc comment-record :comment/parent-comment parent-id)
            comment-record)))

(defn parse-user-from-comment [comment]
    (when (some? comment)
        (-> comment :user parse-user)))

(defn parse-user-from-issue [issue] (-> issue :user parse-user))

(defn sanitize-user-for-api [user]
    {:user/id         (:user/id user)
     :user/avatar-url (:user/avatar-url user)})

(defn sanitize-comment-for-api [comment]
    (let [parent-comment-id (:comment/parent-comment comment)
          mapped-comment {:comment/id           (:comment/id comment)
                          :comment/body         (:comment/body comment)
                          :comment/user         (sanitize-user-for-api (:comment/user comment))
                          :comment/spicy-rating (if
                                                    (nil? (:comment/spicy-comment comment))
                                                    0
                                                    (-> comment :comment/spicy-comment :spicy-comment/rating))}]
        (if (nil? parent-comment-id)
            mapped-comment
            (conj mapped-comment {:comment/parent-comment parent-comment-id}))))

(defn sanitize-issue-for-api [issue]
    {:issue/id           (:issue/id issue)
     :issue/html-url     (:issue/html-url issue)
     :issue/title        (:issue/title issue)
     :issue/body         (:issue/body issue)
     :issue/user         (sanitize-user-for-api (:issue/user issue))
     :issue/comments     (map sanitize-comment-for-api (:issue/comments issue))
     :issue/updated-at   (:issue/updated-at issue)
     :issue/spicy-rating (if
                             (nil? (:issue/spicy-issue issue))
                             0
                             (-> issue :issue/spicy-issue :spicy-issue/rating))})

(comment

    (:html_url test)

    (def huh "  {\n    \"url\": \"https://api.github.com/repos/ziglang/zig/issues/comments/1613916070\",\n    \"html_url\": \"https://github.com/ziglang/zig/issues/16270#issuecomment-1613916070\",\n    \"issue_url\": \"https://api.github.com/repos/ziglang/zig/issues/16270\",\n    \"id\": 1613916070,\n    \"node_id\": \"ic_kwdoamarms5gmmem\",\n    \"user\": {\n      \"login\": \"jarred-sumner\",\n      \"id\": 709451,\n      \"node_id\": \"mdq6vxnlcjcwotq1mq==\",\n      \"avatar_url\": \"https://avatars.githubusercontent.com/u/709451?v=4\",\n      \"gravatar_id\": \"\",\n      \"url\": \"https://api.github.com/users/jarred-sumner\",\n      \"html_url\": \"https://github.com/jarred-sumner\",\n      \"followers_url\": \"https://api.github.com/users/jarred-sumner/followers\",\n      \"following_url\": \"https://api.github.com/users/jarred-sumner/following{/other_user}\",\n      \"gists_url\": \"https://api.github.com/users/jarred-sumner/gists{/gist_id}\",\n      \"starred_url\": \"https://api.github.com/users/jarred-sumner/starred{/owner}{/repo}\",\n      \"subscriptions_url\": \"https://api.github.com/users/jarred-sumner/subscriptions\",\n      \"organizations_url\": \"https://api.github.com/users/jarred-sumner/orgs\",\n      \"repos_url\": \"https://api.github.com/users/jarred-sumner/repos\",\n      \"events_url\": \"https://api.github.com/users/jarred-sumner/events{/privacy}\",\n      \"received_events_url\": \"https://api.github.com/users/jarred-sumner/received_events\",\n      \"type\": \"user\",\n      \"site_admin\": false\n    },\n    \"created_at\": \"2023-06-29t23:26:17z\",\n    \"updated_at\": \"2023-06-29t23:26:17z\",\n    \"author_association\": \"contributor\",\n    \"body\": \"> in the near term, the machine code generated by zig will become less competitive. long-term, it may catch up or even surpass llvm and gcc.\\r\\n\\r\\nimo, this is the biggest question. one of the most compelling reasons to use zig is runtime performance of software written in zig. without llvm's optimization passes, what will that look like?  \",\n    \"reactions\": {\n      \"url\": \"https://api.github.com/repos/ziglang/zig/issues/comments/1613916070/reactions\",\n      \"total_count\": 136,\n      \"+1\": 70,\n      \"-1\": 0,\n      \"laugh\": 0,\n      \"hooray\": 0,\n      \"confused\": 0,\n      \"heart\": 57,\n      \"rocket\": 0,\n      \"eyes\": 9\n    },\n    \"performed_via_github_app\": null\n  }\n")

    (parse-comment (spicy-github.util/parse-json huh))

    (get-issue-id-for-issue-url "https://api.github.com/repos/dakrone/cheshire/issues/200")

    (parse-repository test)
    (gungnir.changeset/create (parse-repository test))

    )
