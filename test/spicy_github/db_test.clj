(ns spicy-github.db-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [gungnir.query :as query]
            [gungnir.changeset :as changeset]))

(defn parse-json [path]
  (json/read-str (slurp path)))

; https://stackoverflow.com/questions/15660066/how-to-read-json-file-into-clojure-defrecord-to-be-searched-later
(defn cast-comment [comment]
  (changeset/cast :comment comment))

(defn cast-user [comment]
  (changeset/cast :user (:user comment)))

(defn seed-db []
  (run! (juxt cast-comment cast-user) (parse-json "test/test_data/comments-16270.json")))