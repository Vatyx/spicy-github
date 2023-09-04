(ns spicy-github.model
  (:gen-class))

(defrecord SpicyIssue [id title body total-reactions created-at comment-count github-json-payload])
