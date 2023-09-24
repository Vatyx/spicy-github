(ns spicy-github.frontend
    (:gen-class)
    (:require
        [cheshire.core :refer :all]
        [rum.core :as rum]
        [stylefy.core :as stylefy]
        [spicy-github.db :as database]
        [nextjournal.markdown]))

(defn frontend-initialize! [] (stylefy/init))

(defn add-font-face []
    (stylefy/font-face {:font-family "open_sans"
                        :src         "url('https://github.com/sorenson/open-sans-woff/raw/master/fonts/Regular/OpenSans-Regular.woff') format('woff')"
                        :font-weight "normal"
                        :font-style  "normal"}))

(defn wrap-body [body]
    [:html
     [:head
      [:title "Most Recent Spicy Issues"]
      [:style {:id "_stylefy-server-styles_"} "_stylefy-server-styles-content_"] ; Generated CSS will be inserted here
      [:style {:id "_stylefy-constant-styles_"}]
      [:style {:id "_stylefy-styles_"}]
      [:style "a {text-decoration: none; color: #5c55fc; font-weight: bold}"]
      [:style "details summary::marker {display:none;} summary{list-style: none} details {}"]
      ; TODO: Copy this script to our codebase so we're not hotlinking
      [:script {:type "module" :src "https://md-block.verou.me/md-block.js"}]]
     [:body body]])


(defn comment-style [] {:border-radius :10px
                        :margin        :10px
                        :color         :#fff
                        :display       :flex})

(defn comment-container-style [] {:display          :flex
                                  :background-color :#333
                                  :color            :#fff
                                  :padding          "5px 15px 5px 15px"
                                  :border-radius    :20px
                                  :margin           :10px
                                  :opacity          :0.8
                                  :cursor           :auto})

(defn comment-body-style [] {:flex    :9
                             :display :inline
                             :padding "5px 20px 5px 20px"})

(defn issue-style [] {:border-radius  :20px
                      :margin-bottom  :20px
                      :display        :flex :box-sizing
                      :border-box :background-color
                      :#ccc :color    :#333
                      :flex-direction :column})

(defn issue-body-style [] {:flex    :9
                           :padding "5px 5px 20px 20px"
                           :cursor :pointer})

(defn issue-container-style [] {
                                :display :flex})

(defn issue-user-image-style [] {:background-color :#fff
                                 :border-radius    :50%
                                 :width            :100px :height
                                 :100px :flex      "0 0 100px"
                                 :padding          :10px
                                 :margin           :10px})

(defn user-image-style [] {:background-color :#fff
                           :border-radius    :50%
                           :width            :100px
                           :height           :100px
                           :flex             "0 0 100px"
                           :padding          :10px
                           :margin-top       :10px
                           :margin-bottom    :10px})

(defn get-user-html
    ([user] (get-user-html user user-image-style))
    ([user style-producer]
     [:img (merge (stylefy/use-style (style-producer)) {:src (:user/avatar-url user)})]))

(defn get-comment-html [comment]
    [:div (stylefy/use-style (comment-style))
     (-> comment :comment/user get-user-html)
     [:div (stylefy/use-style (comment-container-style))
      [:div (stylefy/use-style (comment-body-style))
       [:md-block (:comment/body comment)]]]])

(defn get-ordered-comments [comments]
    (let [ordered-by-date-comments (sort-by :comment/updated-at comments)
          root-comment (last (filter (fn [comment] (-> comment :comment/parent-comment nil?)) ordered-by-date-comments))
          comments-with-parents (filter (fn [comment] (-> comment :comment/parent-comment nil? not)) ordered-by-date-comments)
          comments-by-parent-id (into {} (map vector (map :comment/parent-comment comments-with-parents) comments-with-parents))]
        (loop [chain []]
            (if (empty? chain)
                (if (nil? root-comment)
                    chain
                    (let [matching (get comments-by-parent-id (:comment/id root-comment))]
                        (if (nil? matching)
                            chain
                            (recur (conj chain matching)))))
                (let [matching (get comments-by-parent-id (:comment/id (last chain)))]
                    (if (nil? matching)
                        chain
                        (recur (conj chain matching)))
                    )
                ))
        ))

(defn get-issue-html [issue]
    [:div (stylefy/use-style (issue-style))
     [:details
      [:summary [:div (stylefy/use-style (issue-container-style))
                 [:md-block (stylefy/use-style (issue-body-style)) (:issue/body issue)]
                 (-> (:issue/user issue) (get-user-html issue-user-image-style))]]
      (vec (conj (seq (->> (:issue/comments issue) get-ordered-comments (map get-comment-html))) :div))
      ]
     ])

(defn get-issues-html [issues]
    [:div (vec (conj (seq (map get-issue-html issues)) :div))])

(defn index []
    (stylefy/query-with-styles
        (fn []
            (add-font-face)
            (->
                (database/get-n-latest-issues!)
                get-issues-html
                wrap-body
                rum/render-static-markup))))
