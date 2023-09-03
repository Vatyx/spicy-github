(ns spicy-github.github-scrapper
  (:require [clj-github.httpkit-client :as github-client]
            [cheshire.core :refer :all])
  (:gen-class))

(comment

  (require '[clj-github.httpkit-client :as github-client])
  (def client (github-client/new-client {:app-id "spicy-github" :private-key "SHA256:qc+fDGaDm6F8/j+IiKFr/4wef9qnhUKD/g7AW8avih4=\n"}))

  (github-client/request client {:url "https://api.github.com/repositories/211666/contributors" :method :get})

  (require '[clj-http.client :as client])

  (def foo (client/get "https://api.github.com/repositories/211666/contributors"))

  (:body foo)

  (parse-string (:body foo))
)
