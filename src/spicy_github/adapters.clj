(ns spicy-github.adapters
    (:gen-class)
    (:require [cheshire.core :refer :all]))

(defn parse-repository [r]
    {:repository/id                  (-> r :id str)
     :repository/url                 (:url r)
     :repository/processed-at        (java.sql.Date. 0)
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
    {:issue/id                  (-> issue-json :id str)
     :issue/url                 (:url issue-json)
     :issue/title               (:title issue-json)
     :issue/body                (:body issue-json)
     :issue/total-reactions     (-> issue-json :reactions :total_count int)
     :issue/comment-count       (-> issue-json :comments int)
     :issue/issue-creation-time (:created_at issue-json)
     :issue/issue-updated-time  (:updated_at issue-json)
     :issue/user-id             (-> issue-json :user :id str)
     :issue/github-json-payload (generate-string issue-json)
     })

(defn parse-user-from-comment [comment] (-> comment :user parse-user))
(defn parse-user-from-issue [issue] (-> issue :user parse-user))

(comment
    (def test {:html_url                    "https://github.com/dakrone/cheshire",
               :network_count               148,
               :description                 "Clojure JSON and JSON SMILE (binary json format) encoding/decoding",
               :archived                    false,
               :open_issues_count           50,
               :watchers                    1460,
               :ssh_url                     "git@github.com:dakrone/cheshire.git",
               :hooks_url                   "https://api.github.com/repos/dakrone/cheshire/hooks",
               :archive_url                 "https://api.github.com/repos/dakrone/cheshire/{archive_format}{/ref}",
               :has_discussions             false,
               :keys_url                    "https://api.github.com/repos/dakrone/cheshire/keys{/key_id}",
               :forks_count                 148,
               :languages_url               "https://api.github.com/repos/dakrone/cheshire/languages",
               :git_url                     "git://github.com/dakrone/cheshire.git",
               :issue_comment_url           "https://api.github.com/repos/dakrone/cheshire/issues/comments{/number}",
               :git_refs_url                "https://api.github.com/repos/dakrone/cheshire/git/refs{/sha}",
               :clone_url                   "https://github.com/dakrone/cheshire.git",
               :contents_url                "https://api.github.com/repos/dakrone/cheshire/contents/{+path}",
               :has_downloads               true,
               :teams_url                   "https://api.github.com/repos/dakrone/cheshire/teams",
               :has_issues                  true,
               :disabled                    false,
               :issue_events_url            "https://api.github.com/repos/dakrone/cheshire/issues/events{/number}",
               :license                     {:key     "mit",
                                             :name    "MIT License",
                                             :spdx_id "MIT",
                                             :url     "https://api.github.com/licenses/mit",
                                             :node_id "MDc6TGljZW5zZTEz"},
               :private                     false,
               :watchers_count              1460,
               :collaborators_url           "https://api.github.com/repos/dakrone/cheshire/collaborators{/collaborator}",
               :homepage                    "https://github.com/dakrone/cheshire",
               :git_commits_url             "https://api.github.com/repos/dakrone/cheshire/git/commits{/sha}",
               :name                        "cheshire",
               :temp_clone_token            nil,
               :releases_url                "https://api.github.com/repos/dakrone/cheshire/releases{/id}",
               :milestones_url              "https://api.github.com/repos/dakrone/cheshire/milestones{/number}",
               :svn_url                     "https://github.com/dakrone/cheshire",
               :node_id                     "MDEwOlJlcG9zaXRvcnkxNTE2NDY3",
               :merges_url                  "https://api.github.com/repos/dakrone/cheshire/merges",
               :compare_url                 "https://api.github.com/repos/dakrone/cheshire/compare/{base}...{head}",
               :web_commit_signoff_required false,
               :stargazers_count            1460,
               :tags_url                    "https://api.github.com/repos/dakrone/cheshire/tags",
               :statuses_url                "https://api.github.com/repos/dakrone/cheshire/statuses/{sha}",
               :notifications_url           "https://api.github.com/repos/dakrone/cheshire/notifications{?since,all,participating}",
               :open_issues                 50,
               :has_wiki                    true,
               :size                        1033,
               :assignees_url               "https://api.github.com/repos/dakrone/cheshire/assignees{/user}",
               :commits_url                 "https://api.github.com/repos/dakrone/cheshire/commits{/sha}",
               :labels_url                  "https://api.github.com/repos/dakrone/cheshire/labels{/name}",
               :forks_url                   "https://api.github.com/repos/dakrone/cheshire/forks",
               :contributors_url            "https://api.github.com/repos/dakrone/cheshire/contributors",
               :topics                      [],
               :updated_at                  "2023-09-19T00:34:54Z",
               :pulls_url                   "https://api.github.com/repos/dakrone/cheshire/pulls{/number}",
               :subscribers_count           31,
               :has_pages                   false,
               :default_branch              "master",
               :language                    "Clojure",
               :comments_url                "https://api.github.com/repos/dakrone/cheshire/comments{/number}",
               :id                          1516467,
               :stargazers_url              "https://api.github.com/repos/dakrone/cheshire/stargazers",
               :is_template                 false,
               :issues_url                  "https://api.github.com/repos/dakrone/cheshire/issues{/number}",
               :trees_url                   "https://api.github.com/repos/dakrone/cheshire/git/trees{/sha}",
               :events_url                  "https://api.github.com/repos/dakrone/cheshire/events",
               :branches_url                "https://api.github.com/repos/dakrone/cheshire/branches{/branch}",
               :url                         "https://api.github.com/repos/dakrone/cheshire",
               :downloads_url               "https://api.github.com/repos/dakrone/cheshire/downloads",
               :forks                       148,
               :subscribers_url             "https://api.github.com/repos/dakrone/cheshire/subscribers",
               :full_name                   "dakrone/cheshire",
               :blobs_url                   "https://api.github.com/repos/dakrone/cheshire/git/blobs{/sha}",
               :subscription_url            "https://api.github.com/repos/dakrone/cheshire/subscription",
               :fork                        false,
               :deployments_url             "https://api.github.com/repos/dakrone/cheshire/deployments",
               :has_projects                true,
               :allow_forking               true,
               :pushed_at                   "2023-09-19T19:23:38Z",
               :visibility                  "public",
               :owner                       {:html_url            "https://github.com/dakrone",
                                             :gravatar_id         "",
                                             :followers_url       "https://api.github.com/users/dakrone/followers",
                                             :subscriptions_url   "https://api.github.com/users/dakrone/subscriptions",
                                             :site_admin          false,
                                             :following_url       "https://api.github.com/users/dakrone/following{/other_user}",
                                             :node_id             "MDQ6VXNlcjE5MDYw",
                                             :type                "User",
                                             :received_events_url "https://api.github.com/users/dakrone/received_events",
                                             :login               "dakrone",
                                             :organizations_url   "https://api.github.com/users/dakrone/orgs",
                                             :id                  19060,
                                             :events_url          "https://api.github.com/users/dakrone/events{/privacy}",
                                             :url                 "https://api.github.com/users/dakrone",
                                             :repos_url           "https://api.github.com/users/dakrone/repos",
                                             :starred_url         "https://api.github.com/users/dakrone/starred{/owner}{/repo}",
                                             :gists_url           "https://api.github.com/users/dakrone/gists{/gist_id}",
                                             :avatar_url          "https://avatars.githubusercontent.com/u/19060?v=4"},
               :git_tags_url                "https://api.github.com/repos/dakrone/cheshire/git/tags{/sha}",
               :created_at                  "2011-03-23T14:11:48Z",
               :mirror_url                  nil})

    (:html_url test)

    (parse-repository test)
    (gungnir.changeset/create (parse-repository test))

    )