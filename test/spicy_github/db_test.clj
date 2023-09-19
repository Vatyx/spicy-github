(ns spicy-github.db-test
  (:require [spicy-github.db]
            [spicy-github.model]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [gungnir.query :as query]
            [gungnir.changeset :as changeset]))

; TODO: Fix this, it needs to have a generic query function
; that does a lookup and merge if records are found by that id
(defn persist!
  "Hack because I don't know how to decompose this yet. Modified version of gungnir.query/save!
   because our records will have primary keys already associated with them from github."
  ([changeset query-by-id! clean-record equality-check?]
   (persist! changeset gungnir.database/*datasource* query-by-id! clean-record equality-check?))
  ([{:changeset/keys [_] :as changeset} datasource query-by-id! clean-record equality-check?]
   (let [initial-results (gungnir.database/insert! changeset datasource)]
     (if (nil? (get initial-results :changeset/errors))
       changeset
       (let [diff (get changeset :changeset/diff)
             inputRecord (get changeset :changeset/result)
             existing (query-by-id! inputRecord)]
         (if (equality-check? existing inputRecord)
           existing
           (gungnir.database/update! (clean-record existing diff) datasource))
         )
       ))))

(defn parse-json [path]
  (json/read-str (slurp path) :key-fn keyword))

(defn parse-comment [comment-and-parent]
  (let [comment (comment-and-parent 0)
        parent (comment-and-parent 1)
        issue-id (comment-and-parent 2)]
    (let [updated-comment {:comment/id (-> comment :id str)
                           :comment/url (:url comment)
                           :comment/body (:body comment)
                           :comment/comment-creation-time (:created_at comment)
                           :comment/comment-updated-time (:updated_at comment)
                           :comment/issue-id issue-id
                           :comment/user-id (-> comment :user :id str)
                           :comment/github-json-payload (json/write-str comment)
                           }]
      (if
        (nil? parent)
        updated-comment
        (conj updated-comment {:comment/parent-comment (-> parent :id str)})))))

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

(defn create-or-update-comment! [comment-and-parent]
  (-> comment-and-parent
      parse-comment
      changeset/create
      (persist!
        (fn [record] (query/find! :comment (:comment/id record )))
        (fn [existing record] (changeset/create existing (changeset/cast record :comment)))
        (fn [lhs rhs]
          (=
            (if (nil? lhs) 0 (inst-ms (:comment/comment-updated-time lhs)))
            (if (nil? rhs) 0 (inst-ms (:comment/comment-updated-time rhs))))))))

(defn create-or-update-user! [user]
  (-> user
      changeset/create
      (persist!
        (fn [record] (query/find! :user (:user/id record)))
        (fn [existing record] (changeset/create existing (changeset/cast record :user)))
        (fn [lhs rhs]
          (=
            (get lhs :user/avatar-url)
            (get rhs :user/avatar-url)))))
  )

(defn create-or-update-issue! [issue]
  (-> issue
      parse-issue
      changeset/create
      (persist!
        (fn [record] (query/find! :issue (:issue/id record)))
        (fn [existing record] (changeset/create existing (changeset/cast record :issue)))
        (fn [lhs rhs]
          (=
            (if (nil? lhs) 0 (inst-ms (:issue/issue-updated-time lhs)))
            (if (nil? rhs) 0 (inst-ms (:issue/issue-updated-time rhs))))))
      ))

(defn create-or-update-user-from-comment! [comment]
  (-> comment
      parse-user-from-comment
      create-or-update-user!
      ))

(defn create-or-update-user-from-issue! [issue]
  (-> issue
      parse-user-from-issue
      create-or-update-user!
      ))

(defn seed-db! []
  (let [issue (parse-json "test/test_data/issue-16270.json")
        comments (parse-json "test/test_data/comments-16270.json")
        issue-id (-> issue :id str)]
    ; Create root user
    (create-or-update-user-from-issue! issue)
    ; Then the issue
    (create-or-update-issue! issue)
    ; Then all users for the comments
    (run! create-or-update-user-from-comment! comments)
    ; Then the comments themselves
    (run! create-or-update-comment! (map vector comments (drop-last (conj (seq comments) nil)) (repeat issue-id)))
    ))

(defn setup-test-env! []
  (spicy-github.db/register-db!)
  (spicy-github.model/register-models!))