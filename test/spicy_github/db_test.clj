(ns spicy-github.db-test
  (:require [spicy-github.db]
            [spicy-github.model]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [gungnir.query :as query]
            [clojure.data :as data]
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

(defn parse-comment [comment-and-parent]
  (let [comment (comment-and-parent 0)
        parent (comment-and-parent 1)]
    (let [updated-comment (assoc comment "id" (str (get comment "id"))
                   "github-json-payload" (json/write-str comment)
                   "comment-creation-time" (get comment "created_at")
                   "comment-updated-time" (get comment "updated_at")
                   "user-id" (str (get-in comment ["user" "id"])))]
      (if (nil? parent) updated-comment  (assoc updated-comment "parent-comment" (str (get parent "id")))))))

(defn parse-user-from-comment [comment]
  (let [user (get comment "user")]
    (assoc user "id" (str (get user "id"))
                "url" (get user "html_url")
                "avatar-url" (get user "avatar_url"))))

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

(defn create-or-update-user! [comment]
  (-> comment
      parse-user-from-comment
      (changeset/cast :user)
      changeset/create
      (persist!
        (fn [record] (query/find! :user (get record :user/id)))
        (fn [existing record] (changeset/create existing (changeset/cast record :user)))
        (fn [lhs rhs]
          (=
            (get lhs :user/avatar-url)
            (get rhs :user/avatar-url))))))

(defn seed-db! []
  (let [comments (parse-json "test/test_data/comments-16270.json")]
    (run! create-or-update-user! comments)
    (run! create-or-update-comment! (map vector comments (drop-last (conj (seq comments) nil))))))

(defn setup-test-env! []
  (spicy-github.db/register-db!)
  (spicy-github.model/register-models!))