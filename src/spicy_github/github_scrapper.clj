(ns spicy-github.github-scrapper
  (:require [clj-github.httpkit-client :as github-client]
            [clj-http.client :as client]
            [cheshire.core :refer :all])
  (:gen-class))

(defn get-and-parse [url]
  (parse-string (:body (client/get url))))

(comment
  (require '[clj-github.httpkit-client :as github-client])
  (def client (github-client/new-client {:app-id "spicy-github" :private-key "SHA256:qc+fDGaDm6F8/j+IiKFr/4wef9qnhUKD/g7AW8avih4=\n"}))

  (github-client/request client {:url "https://api.github.com/repositories/211666/contributors" :method :get})

  (require '[clj-http.client :as client])

  (def foo (client/get "https://api.github.com/repositories/211666/contributors"))

  (:body foo)

  (parse-string (:body foo))

  (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/16270")

  (def issues (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/16270"))

  (get issues "comments")

  (def comments (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/16270/comments"))

  comments

  (def specific-issues (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/17065/reactions"))

  (get-and-parse "https://api.github.com/repos/ziglang/zig/issues/17065/reactions")

  specific-issues
)
