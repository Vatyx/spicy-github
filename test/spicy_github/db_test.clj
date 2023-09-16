(ns spicy-github.db-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [gungnir.query :as query]
            [gungnir.changeset :as changeset]))

(defn persist!
  "Hack because I don't know how to decompose this yet. Modified version of "
  ([changeset]
   (gungnir.query/save! changeset gungnir.database/*datasource*))
  ([{:changeset/keys [result] :as changeset} datasource]
   (let [initial-results (if (some? (gungnir.record/primary-key-value result))
                           (gungnir.database/insert! changeset datasource)
                           (gungnir.database/insert! changeset datasource))]
     (if (nil? (get initial-results :changeset/errors))
       initial-results
       (-> initial-results
           (assoc :changeset/errors nil)
           (gungnir.database/update! datasource))
       ))))

(defn parse-json [path]
  (json/read-str (slurp path)))

(defn parse-comment [comment]
  (assoc comment "id" (str (get comment "id"))
                 "github-json-payload" (json/write-str comment)
                 "comment-creation-time" (get comment "created_at")
                 "comment-updated-time" (get comment "updated_at")
                 "user-id" (str (get-in comment ["user" "id"]))))

(defn parse-user-from-comment [comment]
  (let [user (get comment "user")]
    (assoc user "id" (str (get user "id"))
                "url" (get user "html_url")
                "avatar-url" (get user "avatar_url"))))

; https://stackoverflow.com/questions/15660066/how-to-read-json-file-into-clojure-defrecord-to-be-searched-later
(defn cast-user [comment]
  (changeset/cast (parse-user-from-comment comment) :user))

(defn cast-comment [comment]
  (changeset/cast (parse-comment comment) :comment))

(defn print-comment [comment]
  (-> comment parse-comment (changeset/cast :comment) changeset/create println))

(defn print-user [comment]
  (-> comment parse-user-from-comment (changeset/cast :user) changeset/create persist!))

(defn create-or-update! [comment cast]
  (-> comment cast changeset/create query/save!))

(defn create-or-update-user! [comment] (create-or-update! comment cast-user))
(defn create-or-update-comment! [comment] (create-or-update! comment cast-comment))

(defn seed-db! []
  (run! (juxt print-user) (take 1 (parse-json "test/test_data/comments-16270.json"))))