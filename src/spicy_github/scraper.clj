(ns spicy-github.scraper
  (:gen-class)
  (:require [spicy-github.db :as db]
            [spicy-github.model :as model]
            [spicy-github.adapters :as adapters]
            [spicy-github.util :refer :all]
            [malli.dev.pretty]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [gungnir.model]
            [gungnir.query :as q]
            [gungnir.changeset :as changeset]))

(defn process-repositories []
    (forever
         (Thread/sleep 1000)
         (println "hello")))

(defn get-repositories []
    (q/all! :repository))

(q/all! :repository)

(comment

(def get-url (throttle-fn client/get 50 :hour))

(defn get-and-parse [url]
  (parse-string (:body (client/get url))))

(db/initialize-db!)
(model/register-models!)

(q/save! (changeset/create {:repository/url "what" :repository/processed true}))

(comment
  (def client (github-client/new-client {:app-id "spicy-github" :private-key "SHA256:qc+fDGaDm6F8/j+IiKFr/4wef9qnhUKD/g7AW8avih4=\n"}))

  (github-client/request client {:url "https://api.github.com/repositories/211666/contributors" :method :get})

  (require '[clj-http.client :as client])

  (def foo (client/get "https://api.github.com/repos/dakrone/cheshire"))

  (:body foo)

  (def repo (parse-string (:body foo), true))

  (adapters/parse-repository repo)

  (db/persist-record! (adapters/parse-repository repo))

  (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/16270")

  (def issues (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/16270"))

  (get issues "comments")

  (def comments (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/16270/comments"))

  comments

  (def specific-issues (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/17065/reactions"))

  (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/17065/reactions")

  (db/initialize-db!)

  (-> {"url" "test" "processed" false}
      (gungnir.changeset/cast :repository)
      (gungnir.changeset/create)
      (q/save!))

  (def huh (random-uuid))
  (changeset/create {:repository/url "test"})

  (q/save! (changeset/create {:repository/url "test" :repository/processed false}))

  (try

     (catch Exception e
       (malli.dev.pretty/explain model/repository-model e)
       ))
)
)
