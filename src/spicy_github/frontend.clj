(ns spicy-github.frontend
    (:gen-class)
    (:require
        [cheshire.core :refer :all]
        [rum.core :as rum]
        [stylefy.core :as stylefy]
        [spicy-github.db :as database]
        [nextjournal.markdown]))

(defn frontend-initialize! [] (stylefy/init))

(defn add-font-faces []
    (stylefy/font-face {:font-family "'open_sans'"
                        :src         "url('./fonts/OpenSans-Regular.woff2') format('woff2'), url('./fonts/OpenSans-Regular.woff') format('woff'), url('./fonts/OpenSans-Regular.ttf')"
                        :font-weight "normal"
                        :font-style  "normal"})
    (stylefy/font-face {:font-family "'open_sans_light'"
                        :src         "url('./fonts/OpenSans-Light.woff2') format('woff2'), url('./fonts/OpenSans-Light.woff') format('woff'), url('./fonts/OpenSans-Light.ttf')"
                        :font-weight "normal"
                        :font-style  "normal"})
    )

(def body-style {:font-family "'open_sans', 'Courier New'"
                 })

(defn wrap-body [body]
    [:html
     [:head
      [:title "Most Recent Spicy GitHub Issues"]
      [:style {:id "_stylefy-server-styles_"} "_stylefy-server-styles-content_"] ; Generated CSS will be inserted here
      [:style {:id "_stylefy-constant-styles_"}]
      [:style {:id "_stylefy-styles_"}]
      [:style (stylefy/tag "details summary a" {:text-decoration :none
                                                :color           :#5c55fc
                                                :font-weight     :bold})]
      [:style (stylefy/tag "a" {:text-decoration :none
                                :color           :#5cffcc
                                :font-weight     :normal
                                :font-family     "'open_sans'"})]
      [:style (stylefy/tag "details summary::marker" {:display :none})]
      [:style (stylefy/tag "summary" {:list-style :none})]
      [:script {:type "module" :src "./javascript/md-block.js"}]
      [:style (stylefy/tag "p img " {:max-width :100%})]
      ]
     [:body (stylefy/use-style body-style) body]])

(def comment-style {:border-radius :10px
                    :margin        :10px
                    :color         :#fff
                    :display       :flex})

(def comment-container-style {:font-family      "'open_sans_light', 'open_sans', 'Courier New'"
                              :display          :flex
                              :background-color :#333
                              :color            :#fff
                              :padding          "5px 15px 5px 15px"
                              :border-radius    :20px
                              :margin           :5px
                              :opacity          :0.8
                              :cursor           :auto})

(def comment-body-style {:flex    :9
                         :display :inline
                         :padding "0px 5px 0px 5px"})

(def issue-header-style {
                         :text-align :center
                         })

(def issue-style {:border-radius    :20px
                  :margin-bottom    :20px
                  :display          :flex
                  :box-sizing       :border-box
                  :background-color :#ccc
                  :color            :#333
                  :flex-direction   :column})

(def issue-body-style {:flex    :9
                       :padding "5px 5px 20px 20px"
                       :cursor  :pointer})

(def issue-container-style {:display :flex})

(def issue-user-image-style {:background-color :#fff
                             :border-radius    :50%
                             ::stylefy/media   [[{:min-width :100px} {:width  :50px
                                                                      :height :50px
                                                                      :flex   "0 0 50px"}]
                                                [{:min-width :500px} {:width  :100px
                                                                      :height :100px
                                                                      :flex   "0 0 100px"}]]
                             :padding          :10px
                             :margin           :10px})

(def user-image-style {:background-color :#fff
                       :border-radius    :50%
                       ::stylefy/media   [[{:min-width :100px} {:width  :50px
                                                                :height :50px
                                                                :flex   "0 0 50px"}]
                                          [{:min-width :500px} {:width  :100px
                                                                :height :100px
                                                                :flex   "0 0 100px"}]]
                       :padding          :10px
                       :margin-top       :10px
                       :margin-bottom    :10px})

(def issue-title-text-style {
                             :text-decoration :none
                             :color           :#5c55fc
                             :font-weight     :bold
                             })

(defn get-user-html
    ([user] (get-user-html user user-image-style))
    ([user style]
     [:img (merge (stylefy/use-style style) {:src (:user/avatar-url user)})]))

(defn get-comment-html [comment]
    [:div (stylefy/use-style comment-style)
     (-> comment :comment/user get-user-html)
     [:div (stylefy/use-style comment-container-style)
      [:div (stylefy/use-style comment-body-style)
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
    [:div (stylefy/use-style issue-style)
     [:h1 (stylefy/use-style issue-header-style)
      [:a (merge (stylefy/use-style issue-title-text-style) {:href (:issue/url issue)}) (:issue/title issue)]]
     [:details
      [:summary [:div (stylefy/use-style issue-container-style)
                 [:md-block (stylefy/use-style issue-body-style) (:issue/body issue)]
                 (-> (:issue/user issue) (get-user-html issue-user-image-style))]]
      (vec (conj (->> (:issue/comments issue) get-ordered-comments (map get-comment-html)) :div))
      ]
     ])

(defn get-issues-html [issues]
    [:div (vec (conj (map get-issue-html issues) :div))])

(defn index []
    (stylefy/query-with-styles
        (fn []
            (add-font-faces)
            (->
                (database/get-n-latest-issues!)
                get-issues-html
                wrap-body
                rum/render-static-markup))))
