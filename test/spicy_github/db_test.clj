(ns spicy-github.db-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [gungnir.query :as query]
            [malli.core :as malli]
            [spicy-github.model :as model]
            [gungnir.changeset :as changeset]))

(defn parse-json [path]
  (json/read-str (slurp path)))

; https://stackoverflow.com/questions/15660066/how-to-read-json-file-into-clojure-defrecord-to-be-searched-later
(defn cast-comment [comment]
  (let [user (get comment "user")]
    (changeset/cast
      (assoc user "user-id" (str (get user "id")))
      :user)))

(defn cast-comment [comment]
  (changeset/cast
    (-> comment
        (assoc "user-id" (str (get-in comment ["user" "id"]))
               "github-json-payload" (json/write-str comment)
               "comment-creation-time" (get comment "created_at"))
        (dissoc "id" "user"))
    :comment))

(defn print-comment [comment]
  (-> comment
      (assoc "user-id" (str (get-in comment ["user" "id"]))
                     "github-json-payload" (json/write-str comment)
                     "comment-creation-time" (get comment "created_at"))
      (dissoc "id" "user")
      json/write-str
      println))

(defn print-user [comment]
  (let [user (get comment "user")]
    (->
      (assoc user "user-id" (str (get user "id")))
      (dissoc "id")
      json/write-str
      println)))

(defn create-or-update [comment cast]
  (-> comment cast gungnir.changeset/create gungnir.query/save! println))

(defn create-or-update-user [comment] (create-or-update comment cast-user))
(defn create-or-update-comment [comment] (create-or-update comment cast-comment))

(defn seed-db []
  (run! (juxt print-user) (take 1 (parse-json "test/test_data/comments-16270.json"))))