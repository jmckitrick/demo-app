(ns demo-app.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [goog.events :as gevents]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [demo-app.ajax :refer [load-interceptors!]]
            [demo-app.events :as events]
            [demo-app.websockets :as ws])
  (:import goog.History))

(defn nav-link [uri title page]
  [:li.nav-item
   {:class (when (= page @(rf/subscribe [:page])) "active")}
   [:a.nav-link {:href uri} title]])

(defn navbar []
  [:nav.navbar.navbar-dark.bg-primary.navbar-expand-md
   {:role "navigation"}
   [:button.navbar-toggler.hidden-sm-up
    {:type "button"
     :data-toggle "collapse"
     :data-target "#collapsing-navbar"}
    [:span.navbar-toggler-icon]]
   [:a.navbar-brand {:href "#/"} "demo-app"]
   [:div#collapsing-navbar.collapse.navbar-collapse
    [:ul.nav.navbar-nav.mr-auto
     [nav-link "#/" "Home" :home]
     [nav-link "#/about" "About" :about]
     [nav-link "#/login" "Login" :login]
     [nav-link "#/secret" "Secret" :secret]]]])

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     [:img {:src (str js/context "/img/warning_clojure.png")}]]]])

(defn button-login [username password]
  [:button.button-default
   {:on-click #(rf/dispatch [:login username password])}
   "Login"])

(defn secret-page []
  [:div.container
   [:div.row>div.col-sm-12
    [:h2.alert.alert-info "You made it to the secret page!"]]])

(defn login-page []
  (let [username (r/atom nil)
        password (r/atom nil)]
    (fn []
      [:div.container
       [:input.form-control
        {:type :text
         :placeholder "username"
         :value @username
         :on-change #(reset! username (-> % .-target .-value))}]
       [:input.form-control
        {:type :password
         :placeholder "password"
         :value @password
         :on-change #(reset! password (-> % .-target .-value))}]
       [button-login @username @password]])))

(defonce messages (r/atom []))

(defn message-list []
  [:ul
   (for [[i message] (map-indexed vector @messages)]
     ^{:key i}
     [:li message])])

(defn message-input []
  (let [value (r/atom nil)]
    (fn []
      [:input.form-control
       {:type :text
        :placeholder "type in a message and press enter"
        :value @value
        :on-change #(reset! value (-> % .-target .-value))
        :on-key-down
        #(when (= (.-keyCode %) 13)
           (ws/send-transit-msg!
            {:message @value})
           (reset! value nil))}])))

(defn button-component-1 []
  [:button
   {:on-click #(rf/dispatch [:ajax-1])}
   "Click me 1"])

(defn button-component-2 []
  [:button
   {:on-click #(rf/dispatch [:service-1])}
   "Click me 2"])

(defn do-it [e]
  (js/console.log "Got" e "for auth test 1")
  (rf/dispatch [:auth-test-1]))

(defn button-component-auth []
  [:button
   {:on-click #(do-it %)}
   "Click me AUTH"])

(defn chat-component []
  [:div
   [:div.row
    [:div.col-md-12
     [:h2 "Welcome to chat"]]]
   [:div.row
    [:div.col-sm-6
     [message-list]]]
   [:div.row
    [:div.col-sm-6
     [message-input]]]])

(defn home-page []
  [:div.container
   [:div.row>div.col-sm-12
    [:h2.alert.alert-info "Tip: try pressing CTRL+H to open re-frame tracing menu"]
    [button-component-1]
    [:div
     (pr-str @(rf/subscribe [:ajax-data]))]
    [button-component-2]
    [:div
     (pr-str @(rf/subscribe [:service-1-data]))]
    [button-component-auth]]
   #_[chat-component]
   (when-let [docs @(rf/subscribe [:docs])]
     [:div.row>div.col-sm-12
      [:div {:dangerouslySetInnerHTML
             {:__html (md->html docs)}}]])])

(defn update-messages! [{:keys [message] :as m}]
  (js/console.log "Update messages with m" m)
  (js/console.log "Update messages with message" message)
  (swap! messages #(vec (take 10 (conj % message)))))

(def pages
  {:home #'home-page
   :about #'about-page
   :login #'login-page
   :secret #'secret-page})

(defn page []
  [:div
   [navbar]
   [(pages @(rf/subscribe [:page]))]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :home]))

(secretary/defroute "/secret" []
  (rf/dispatch [:set-active-page :secret]))

(secretary/defroute "/about" []
  (rf/dispatch [:set-active-page :about]))

(secretary/defroute "/login" []
  (rf/dispatch [:set-active-page :login]))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (gevents/listen
      HistoryEventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn fetch-docs! []
  (GET "/docs" {:handler #(rf/dispatch [:set-docs %])}))

(defn mount-components []
  (rf/clear-subscription-cache!)
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  #_(ws/make-websocket! (str "ws://" (.-host js/location) "/ws") update-messages!)
  (rf/dispatch-sync [:initialize-db])
  (load-interceptors!)
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))
