(ns spicy-github.util)

(defmacro forever [& body]
    `(while true ~@body))
