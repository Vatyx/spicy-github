(ns spicy-github.frontend
    (:require
        [rum.core :as rum]
        [stylefy.core :as stylefy]
        [stylefy.rum :as stylefy-rum]
        [spicy-github.api :as api]))

(defn frontend-initialize! [] (stylefy/init {:dom (stylefy-rum/init)}))

; Data
; ----

; https://docs.github.com/en/rest/reactions/reactions?apiVersion=2022-11-28
(def reaction-mapping {:+1       "\uD83D\uDC4D"
                       :-1       "\uD83D\uDC4E"
                       :laugh    "\uD83D\uDE04"
                       :confused "\uD83D\uDE15"
                       :heart    "❤\uFE0F"
                       :hooray   "\uD83C\uDF89"
                       :rocket   "\uD83D\uDE80"
                       :eyes     "\uD83D\uDC40"})

(defn- get-reaction [reaction-keyword]
    (get reaction-mapping reaction-keyword ""))

; Styles
; ------
(defn add-font-faces! []
    (stylefy/font-face {:font-family "'open_sans'"
                        :src         "url('./fonts/OpenSans-Regular.woff2') format('woff2'), url('./fonts/OpenSans-Regular.woff') format('woff'), url('./fonts/OpenSans-Regular.ttf')"
                        :font-weight "normal"
                        :font-style  "normal"})
    (stylefy/font-face {:font-family "'open_sans_light'"
                        :src         "url('./fonts/OpenSans-Light.woff2') format('woff2'), url('./fonts/OpenSans-Light.woff') format('woff'), url('./fonts/OpenSans-Light.ttf')"
                        :font-weight "normal"
                        :font-style  "normal"}))

(def comment-style {:border-radius :10px
                    :margin        :10px
                    :color         :#fff
                    :display       :flex
                    :cursor        :auto})

(def comment-container-style {:font-family      "'open_sans_light', 'open_sans', 'Courier New'"
                              :background-color :#333
                              :color            :#fff
                              :padding          "5px 5px 5px 5px"
                              :border-radius    :20px
                              :margin           :5px
                              :opacity          :0.8
                              :max-width        :820px})

(def comment-body-style {:flex    :9
                         :display :inline})

(def issue-header-style {:text-align    :center
                         :margin-top    :0px
                         :margin-bottom :0px})

(def issues-style
    {:border-radius    :20px
     :margin-bottom    :20px
     :display          :flex
     :box-sizing       :border-box
     :background-color :#ccc
     :color            :#333
     :max-width        :1000px
     :margin           :auto
     :flex-direction   :column})

(def hidden-size :15px)

(def hidden-style {:max-width        hidden-size
                   :max-height       hidden-size
                   :min-width        hidden-size
                   :min-height       hidden-size
                   :margin           :auto
                   :margin-top       :5px
                   :margin-bottom    :5px
                   :background-color :#fff
                   :border-radius    :50%
                   :cursor           :zoom-in})

(def md-block-wrapper {:max-height :1600px
                       :padding    :10px
                       :overflow   :auto})

(def issue-body-style {:flex    :9
                       :padding "5px 5px 20px 20px"})

(def issue-container-style {:display      :flex
                            :margin-left  :10px
                            :margin-right :10px})

(def issue-user-image-style {:background-color :#fff
                             :border-radius    :50%
                             ::stylefy/media   [[{:min-width :100px} {:width  :50px
                                                                      :height :50px
                                                                      :flex   "0 0 50px"}]
                                                [{:min-width :500px} {:width  :100px
                                                                      :height :100px
                                                                      :flex   "0 0 100px"}]]
                             :padding          :10px
                             :margin-bottom    :10px
                             :margin-top       :10px
                             :margin-right     :10px
                             :margin-left      :10px})

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

(def issue-title-text-style {:text-decoration :none
                             :color           :#5c55fc
                             :overflow        :auto
                             :font-weight     :bold})

(def reactions-container-style {:display         "flex !important"
                                :align-items     "center !important"
                                :flex-wrap       "wrap !important"
                                :flex-direction  "row !important"
                                :justify-content :center
                                :box-sizing      :border-box})

(def reaction-single-container-style {:height           :26px
                                      :padding          "0 4px !important"
                                      :font-size        :12px
                                      :line-height      :26px
                                      :background-color :transparent
                                      :border           "1px solid"
                                      :border-radius    :100px
                                      :margin           "0 4px !important"})

(def emoji-style {:display     :inline-block
                  :width       :16px
                  :height      :16px
                  :font-size   "1em !important"
                  :line-height :1.25})

(def emoji-count-style {:height      :24px
                        :padding     "0 4px"
                        :margin-left :2px})

; Rendering
; ---------
(defn- get-user-html
    ([user] (get-user-html user user-image-style))
    ([user style]
     [:img (merge (stylefy/use-style style) {:src (:user/avatar-url user)})]))

(def visible-comments (atom {}))
(def collapsed-comments (atom {}))
(def collapsed-comments-parents-by-id (atom {}))
(def refresh-issues-fn (atom nil))

(defn- swap-is-selected [comment-id]
    (let [existing-value (get @visible-comments comment-id false)]
        (swap! visible-comments conj {comment-id (not existing-value)})
        (when (contains? @collapsed-comments-parents-by-id comment-id)
            (let [comments (get @collapsed-comments comment-id '())]
                (run! (fn [comment] (swap! visible-comments conj {(:comment/id comment) (not (get @visible-comments (:comment/id comment) false))})) comments)))
        (when (not (nil? @refresh-issues-fn))
            (@refresh-issues-fn))))

(defn- get-reaction-html [reaction-entry]
    [:div (stylefy/use-style reaction-single-container-style)
     [:div (stylefy/use-style emoji-style) (get-reaction (get reaction-entry 0))]
     [:span (stylefy/use-style emoji-count-style) (str (get reaction-entry 1 0))]])

(defn- get-and-filter-reaction-html [reactions]
    (vec (conj
             (map get-reaction-html
                  (sort-by (fn [entry] (get entry 1)) >
                           (filter
                               #(and (not (= :total_count (get % 0))) (not (== 0 (get % 1 0))))
                               reactions))))))

(defn- get-static-comment-html [comment]
    [:div (stylefy/use-style comment-style) (-> comment :comment/user get-user-html)
     [:div (stylefy/use-style comment-container-style)
      [:div (stylefy/use-style (merge reactions-container-style {:justify-content :left}))
       (get-and-filter-reaction-html (js->clj (:comment/reaction comment)))]
      [:div (stylefy/use-style comment-body-style)
       [:div (stylefy/use-style md-block-wrapper)
        [:md-block (:comment/body comment)]]]]])

(defn- get-non-spicy-comment-html [comment comment-id]
    ; We want to toggle the parent's collapse function
    [:div (stylefy/use-style comment-style) (-> comment :comment/user get-user-html)
     [:div (stylefy/use-style (merge comment-container-style {:cursor :zoom-out} {:on-click #(swap-is-selected (get @collapsed-comments-parents-by-id comment-id comment-id))}))
      [:div (stylefy/use-style comment-body-style)
       [:div (stylefy/use-style md-block-wrapper) [:md-block (:comment/body comment)]]]]])

(defn- get-hidden-comment-html [comment-id]
    [:div (stylefy/use-style {:cursor :auto})
     [:div (stylefy/use-style hidden-style {:on-click #(swap-is-selected comment-id)})]])

(defn- is-spicy? [comment]
    (>= (get comment :comment/spicy-rating 0) api/minimum-spicy-score))

(defn- get-spicy-comment-html [comment]
    (let [is-spicy (is-spicy? comment)
          comment-id (:comment/id comment)]
        (if is-spicy
            (get-static-comment-html comment)
            (if (get @visible-comments comment-id false)
                (get-non-spicy-comment-html comment comment-id)
                (get-hidden-comment-html comment-id)))))

(defn- get-ordered-comments [comments]
    (let [root-comment (last (filter (fn [comment] (-> comment :comment/parent-comment nil?)) comments))
          comments-with-parents (filter (fn [comment] (-> comment :comment/parent-comment nil? not)) comments)
          comments-by-parent-id (into {} (map vector (map :comment/parent-comment comments-with-parents) comments-with-parents))]
        (loop [chain []]
            (if (empty? chain)
                (if (nil? root-comment)
                    chain
                    (recur (conj chain root-comment)))
                (let [matching (get comments-by-parent-id (:comment/id (last chain)))]
                    (if (nil? matching)
                        chain
                        (recur (conj chain matching))))))))

(defn- collapse-boring-comments [comments]
    (when (> (count comments) 0)
        (let [first-comment (first comments)
              comment-id (:comment/id first-comment)]
            (swap! collapsed-comments conj {comment-id (remove (fn [comment] (= comment first-comment)) comments)})
            (run! (fn [comment] (swap! collapsed-comments-parents-by-id conj {(:comment/id comment) comment-id})) comments)
            (if (get @visible-comments comment-id false)
                comments
                (repeat (min 3 (count comments)) first-comment)))))

(defn- collapse-comments [comments]
    (let [to-render (take-while (fn [comment] (is-spicy? comment)) comments)
          boring-comments (take-while (fn [comment] (not (is-spicy? comment))) (drop (count to-render) comments))
          comment-count (+ (count boring-comments) (count to-render))]
        (if (< comment-count (count comments))
            (concat to-render (collapse-boring-comments boring-comments) (collapse-comments (drop comment-count comments)))
            (concat to-render (collapse-boring-comments boring-comments)))))

(defn- get-issue-html [issue]
    (let [comments (:issue/comments issue)
          to-render-issue-comments (collapse-comments (get-ordered-comments comments))]
        [:div (if (empty? comments)
                  (stylefy/use-style issues-style)
                  (stylefy/use-style issues-style))
         [:div (stylefy/use-style (merge reactions-container-style {:padding :5px})) (get-and-filter-reaction-html (js->clj (:issue/reaction issue)))]
         [:h1 (stylefy/use-style issue-header-style)
          [:a (merge (stylefy/use-style issue-title-text-style) {:href (:issue/html-url issue)}) (:issue/title issue)]]
         [:details
          [:summary [:div (stylefy/use-style (merge issue-container-style {:cursor :pointer}))
                     [:div (stylefy/use-style md-block-wrapper) [:md-block (stylefy/use-style issue-body-style) (:issue/body issue)]]
                     (-> (:issue/user issue) (get-user-html issue-user-image-style))]]
          (vec (conj (map get-spicy-comment-html to-render-issue-comments) :div))]]))


(defn- get-issues-html [issues]
    [:div (vec (conj (map get-issue-html issues) :div))])

;; https://gist.github.com/nberger/b5e316a43ffc3b7d5e084b228bd83899

; Javascript
; ----------
(defn- get-scroll-top []
    (if (exists? (.-pageYOffset js/window))
        (.-pageYOffset js/window)
        (.-scrollTop (or (.-documentElement js/document)
                         (.-parentNode (.-body js/document))
                         (.-body js/document)))))

(defn- get-top-position [node]
    (if (not node)
        0
        (+ (.-offsetTop node) (get-top-position (.-offsetParent node)))))

(defn- safe-component-mounted? [component]
    (try (boolean (rum/dom-node component)) (catch js/Object _ false)))

(defn debounce
    "Returns a function that will call f only after threshold has passed without new calls
    to the function. Calls prep-fn on the args in a sync way, which can be used for things like
    calling .persist on the event object to be able to access the event attributes in f"
    ([threshold f] (debounce threshold f (constantly nil)))
    ([threshold f prep-fn]
     (let [t (atom nil)]
         (fn [& args]
             (when @t (js/clearTimeout @t))
             (apply prep-fn args)
             (reset! t (js/setTimeout #(do
                                           (reset! t nil)
                                           (apply f args))
                                      threshold))))))

(defn- is-spicy-enough? [issue]
    (not (empty? (filter #(is-spicy? %) (:issue/comments issue)))))

(def minimum-issues 10)

(def issues (atom []))

(def can-load-more (atom true))

(def is-loading-issues (atom false))

(def issue-initialization (atom nil))

(defn- has-enough-issues []
    (>= (count @issues) minimum-issues))

(defn- update-issues! [new-issues]
    (if (empty? new-issues)
        (reset! can-load-more false)
        (reset! issues (let [existing-ids (set (map :issue/id @issues))
                             new-ids (atom (set {}))]
                           (concat @issues (filter (fn [issue]
                                                       (and
                                                           (is-spicy-enough? issue)
                                                           (let [issue-id (:issue/id issue)
                                                                 is-new-issue (not (or (contains? existing-ids issue-id) (contains? @new-ids issue-id)))]
                                                               (swap! new-ids conj issue-id)
                                                               is-new-issue)
                                                           )) new-issues)))))
    (when (not (nil? @refresh-issues-fn)) (@refresh-issues-fn))
    (when (not (has-enough-issues)) (@issue-initialization)))

(defn- try-initialize-issues! []
    (when (not (has-enough-issues))
        (api/get-issues update-issues! is-loading-issues)))

(reset! issue-initialization try-initialize-issues!)

(defn- load-fn []
    (reset! is-loading-issues true)
    (try (api/get-issues update-issues! is-loading-issues) (catch js/Object _ (reset! is-loading-issues false)))
    (when (not (nil? @refresh-issues-fn)) (@refresh-issues-fn)))

(def listener-fn (atom nil))

(defn- detach-scroll-listener [state]
    (when @listener-fn
        (.removeEventListener js/window "scroll" @listener-fn)
        (.removeEventListener js/window "resize" @listener-fn)
        (reset! listener-fn nil)
        (reset! can-load-more true))
    state)

(defn- should-load-more? [state]
    (let [node (rum/dom-node state)
          scroll-top (get-scroll-top)
          my-top (get-top-position node)
          threshold 250]
        (if (not (nil? node))
            (< (- (+ my-top (.-offsetHeight node))
                  scroll-top
                  (.-innerHeight js/window))
               threshold)
            false)))

(defn- scroll-listener [state]
    (when (safe-component-mounted? state)
        (when (and @can-load-more (should-load-more? state) (not @is-loading-issues))
            (detach-scroll-listener state)
            (load-fn))))

(def debounced-scroll-listener (debounce 5 scroll-listener))

(defn- attach-scroll-listener [state]
    (when-not @listener-fn
        (reset! listener-fn (partial debounced-scroll-listener state))
        (.addEventListener js/window "scroll" @listener-fn)
        (.addEventListener js/window "resize" @listener-fn))
    state)

(rum/defcs issues-stateful-component
    <
    rum/reactive
    {:did-mount    (fn [state] (attach-scroll-listener state))
     :did-update   (fn [state] (attach-scroll-listener state))
     :will-unmount (fn [state] (detach-scroll-listener state))}
    [state]
    (get-issues-html @issues))

; Setup Stylefy
(frontend-initialize!)
(add-font-faces!)

; Component mounting
(defn- mount-issues-component! []
    (rum/mount (issues-stateful-component) (.getElementById js/document "issues-container")))

(mount-issues-component!)

(reset! refresh-issues-fn mount-issues-component!)

; Seed initial issues
(try-initialize-issues!)

; Maybe we got an error on first load, try again until we don't have errors
(js/setInterval
    try-initialize-issues!
    15000)