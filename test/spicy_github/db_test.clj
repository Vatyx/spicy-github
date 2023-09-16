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
  (changeset/cast model/comment-model (assoc (update-in comment [:id] str) :user_id :id)))

(defn cast-user [comment]
  (changeset/cast
    model/user-model
    (->
      comment
      (get "user")
      (assoc "id" (:id str))
      (assoc :user-id "id")
      )))

(defn explain-comment [comment]
  ; TODO
  (malli/explain model/comment-model (assoc (assoc comment "user-id" (str (get (get comment "user") "id"))) "github-json-payload" json/write-str)))

(defn explain-user [comment]
  (let [user (get comment "user")]
    (malli/explain model/user-model (assoc user "user-id"
                                                (str (get user "id"))))
    ))

(defn print-user [comment]
  (->
    comment
    (get "user")
    (assoc "id" "id" str)
    (assoc "user-id" "id")
    println))

(defn print-user-2 [comment]
  (let [user (get comment "user")]
    (malli/explain model/user-model (assoc user "user-id"
                (str (get user "id"))))
    ))

(defn seed-db []
  (run! (juxt explain-user explain-comment) (parse-json "test/test_data/comments-16270.json")))