(ns spicy-github.frontend
    (:gen-class)
    (:require
        [cheshire.core :refer :all]
        [rum.core :as rum]
        [stylefy.core :as stylefy]
        [spicy-github.db :as database]))

(defn frontend-initialize! [] (stylefy/init))

(defn add-font-face []
    (stylefy/font-face {:font-family "open_sans"
                        :src         "url('resources/fonts/OpenSans-Regular-webfont.woff') format('woff')"
                        :font-weight "normal"
                        :font-style  "normal"}))

(defn wrap-body [body]
    [:html
     [:head
      [:title "Most Recent Spicy Issues"]
      [:style {:id "_stylefy-server-styles_"} "_stylefy-server-styles-content_"] ; Generated CSS will be inserted here
      [:style {:id "_stylefy-constant-styles_"}]
      [:style {:id "_stylefy-styles_"}]
      ; TODO: Copy this script to our codebase so we're not hotlinking
      [:script {:type "module" :src "https://md-block.verou.me/md-block.js"}]]
     [:body body]])

(def comment-style {

                    })

(def issue-style {

                  })

(def user-style {

                 })

(defn get-comment-html [comment]
    [[]
     [:md-block (stylefy/use-style comment-style) (:comment/body comment)]])

(defn get-user-html [user]
    [
     [:div (stylefy/use-style user)]
     [:img {:src (:user/avatar-url user)}]])

(defn get-issue-html [issue]
    [
     (-> (:issue/user issue) get-user-html)
     [:md-block (stylefy/use-style issue-style) (:issue/body issue)]
     (->> (:issue/comments issue) (map get-comment-html))])

(defn get-issues-html [issues]
    (vec (map get-issue-html issues)))

(defn index []
    (stylefy/query-with-styles
        (fn []
            (add-font-face)
            (->
                database/get-n-latest-issues!
                get-issues-html
                wrap-body
                rum/render-static-markup))))
