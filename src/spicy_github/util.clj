(ns spicy-github.util
    (:gen-class)
    (:require [cheshire.core :as json]
              [clojure.java.io :as io]
              [taoensso.timbre :as timbre])
    (:import (gungnir.database RelationAtom)))

(defmacro forever [& body]
    `(while true ~@body))

(defn load-resource [resource-name]
    (-> (io/resource resource-name)
        io/file
        slurp))

(defn namespace-key [namespace key]
    (keyword (name namespace) (name key)))

(defn unqualified-keyword [k]
    (-> k name keyword))

(defn model-equality? [lhs rhs]
    (let [ignored-keys #{:created-at :updated-at}]
        (empty? (filter (fn [[k v]]
                            (let [ignored-key? (contains? ignored-keys (unqualified-keyword k))
                                  ignored-type? (instance? RelationAtom v)
                                  equals-rhs? (= v (k rhs))]
                                (not (or ignored-key? equals-rhs? ignored-type?))))
                        lhs))))
(defn parse-json [json-str]
    (json/parse-string json-str true))

(defn sanitize-github-url [url]
    (clojure.string/replace url "{/number}" ""))

(defn get-id-as-string [json]
    (-> json
        :id
        str))

(defn lazy-concat
    "A concat version that is completely lazy and
    does not require to use apply."
    [colls]
    (lazy-seq
        (when-first [c colls]
            (lazy-cat c (lazy-concat (rest colls))))))

(defn execute [f]
    "Executes f on the provided items but does not modify it"
    (fn [xf]
        (fn
            ([] (xf))
            ([result] (xf result))
            ([result input]
             (f input)
             (xf result input)))))

(defn execute-log [name]
    (execute #(timbre/debug name %)))
