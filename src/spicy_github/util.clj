(ns spicy-github.util
    (:gen-class))

(defmacro forever [& body]
    `(while true ~@body))

(defn namespace-key [namespace key]
    (keyword (name namespace) (name key)))

(defn unqualified-keyword [k]
    (-> k name keyword))

(defn model-equality? [lhs rhs]
    (let [ignored-keys #{:created-at :updated-at}]
        (empty? (filter (fn [[k v]]
                            (let [ignored-key? (contains? ignored-keys (unqualified-keyword k))
                                  equals-rhs? (= v (k rhs))]
                                (not (or ignored-key? equals-rhs?))))
                        lhs))))
