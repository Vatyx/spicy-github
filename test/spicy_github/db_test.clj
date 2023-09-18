(ns spicy-github.db-test
  (:require [spicy-github.db]
            [spicy-github.model]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [gungnir.query :as query]
            [clojure.instant :as instant]
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
  (json/read-str (slurp path)))

(defn parse-timestamp [timestamp]
  (instant/read-instant-date timestamp))

(defn parse-comment [comment-and-parent]
  (let [comment (comment-and-parent 0)
        parent (comment-and-parent 1)
        issue-id (comment-and-parent 2)]
    (let [updated-comment (assoc comment "id" (str (get comment "id"))
                                         "github-json-payload" (json/write-str comment)
                                         "comment-creation-time" (parse-timestamp (get comment "created_at"))
                                         "comment-updated-time" (parse-timestamp (get comment "updated_at"))
                                         "user-id" (str (get-in comment ["user" "id"]))
                                         "issue-id" issue-id)]
      (if
        (nil? parent)
        updated-comment
        (assoc updated-comment "parent-comment" (str (get parent "id")))))))

(defn parse-user [user-json]
  (assoc user-json "id" (str (get user-json "id"))
                   "url" (get user-json "html_url")
                   "avatar-url" (get user-json "avatar_url")))

(defn parse-issue [issue-json comment]
  (dissoc
    (let [updated-issue (assoc issue-json "id" (str (get issue-json "id"))
                                          "total-reactions" (get (get issue-json "reactions") "total_count")
                                          "comment-count" (get issue-json "comments")
                                          "issue-creation-date" (parse-timestamp (get issue-json "created_at"))
                                          "issue-updated-time" (parse-timestamp (get issue-json "updated_at"))
                                          "github-json-payload" (json/write-str issue-json))]
      (if
        (nil? comment)
        updated-issue
        (assoc updated-issue "root-comment" (str (get comment "id")))))
    "comments"))

(defn parse-user-from-comment [comment] (parse-user (get comment "user")))
(defn parse-user-from-issue [issue] (parse-user (get issue "user")))

(defn create-or-update-comment! [comment-and-parent]
  (-> comment-and-parent
      parse-comment
      (changeset/cast :comment)
      changeset/create
      (persist!
        (fn [record] (query/find! :comment (get record :comment/id)))
        (fn [existing record] (changeset/create existing (changeset/cast record :comment)))
        (fn [lhs rhs]
          (=
            (get lhs :comment/body)
            (get rhs :comment/body))))))

(defn create-or-update-user! [user]
  (-> user
      (changeset/cast :user)
      changeset/create
      (persist!
        (fn [record] (query/find! :user (get record :user/id)))
        (fn [existing record] (changeset/create existing (changeset/cast record :user)))
        (fn [lhs rhs]
          (=
            (get lhs :user/avatar-url)
            (get rhs :user/avatar-url)))))
  )

(defn create-or-update-issue! [issue comment]
  (-> issue
      (parse-issue comment)
      (changeset/cast :issue)
      changeset/create
      (persist!
        (fn [record] (query/find! :issue (get record :issue/id)))
        (fn [existing record] (changeset/create existing (changeset/cast record :issue)))
        (fn [lhs rhs]
          (and (=
                 (inst-ms (get lhs :issue/issue-updated-time))
                 (inst-ms (get rhs :issue/issue-updated-time)))
               (=
                 (get lhs :issue/root-comment)
                 (get rhs :issue/root-comment))
               )))
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
        issue-id (str (get issue "id"))]
    ; Create root user
    (create-or-update-user-from-issue! issue)
    ; Then the issue
    (create-or-update-issue! issue nil)
    ; Then all users for the comments
    (run! create-or-update-user-from-comment! comments)
    ; Then the comments themselves
    (run! create-or-update-comment! (map vector comments (drop-last (conj (seq comments) nil)) (repeat issue-id)))
    ; And finally, link our issue back to the root comment
    (create-or-update-issue! issue (nth comments 0 (fn [] nil)))
    ))

(defn setup-test-env! []
  (spicy-github.db/register-db!)
  (spicy-github.model/register-models!))