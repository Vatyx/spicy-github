(ns spicy-github.frontend
    (:require
        [cheshire.core :refer :all]
        [spicy-github.util :as util]))

(defn- get-index-html [] (util/load-resource "public/index.html"))
(def index-html (get-index-html))