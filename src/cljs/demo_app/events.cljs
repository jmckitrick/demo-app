(ns demo-app.events
  (:require [ajax.core :as ajax]
            [demo-app.db :as db]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-sub]]
            [day8.re-frame.http-fx]))

;;dispatchers

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  :set-active-page
  (fn [db [_ page]]
    (js/console.log "Active page is" page)
    (assoc db :page page)))

(reg-event-db
  :set-docs
  (fn [db [_ docs]]
    (assoc db :docs docs)))

(reg-event-fx
 ::ajax-1
 (fn [{:keys [db]} _]
   (js/console.log "Get ajax")
   {:db (assoc db :spinner true)
    :http-xhrio {:method :get
                 :uri "http://ip.jsontest.com/"
                 :timeout 8000
                 :response-format (ajax/json-response-format)
                 :on-success [:events/good-ajax]
                 :on-failure [:events/bad-ajax]}}))

(reg-event-db
 :events/good-ajax
 (fn [db [_ data]]
   (js/console.log "Got good ajax")
   (assoc db :ajax-data data :spinner false)))

(reg-event-db
 :events/bad-ajax
 (fn [db [_ data]]
   (js/console.log "Got bad ajax" data)
   (assoc db :spinner false)))

(reg-event-fx
 ::service-1
 (fn [{:keys [db]} _]
   (js/console.log "Get ajax")
   {:db (assoc db :spinner true)
    :http-xhrio {:method :get
                 :uri "/api/plus"
                 :params {:x 1 :y 2}
                 :timeout 8000
                 :response-format (ajax/json-response-format)
                 ;;:response-format (ajax/transit-response-format)
                 :on-success [:events/good-service-1]
                 :on-failure [:events/bad-ajax]}}))

(reg-event-db
 :events/good-service-1
 (fn [db [_ data]]
   (js/console.log "Got good service-1")
   (assoc db :service-1-data data :spinner false)))

(reg-event-fx
 ::login
 (fn [{:keys [db]} [_ username password]]
   (js/console.log "Login with" username password)
   (let [form-data (js/FormData.)]
     (.append form-data "username" username)
     (.append form-data "password" password)
     #_(.append form-data {:username username :password password})
     {:http-xhrio {:uri "/login"
                   :method :post
                   :timeout 10000
                   ;;:body {:username username :password password}
                   ;;:body form-data #_{:username username :password password}
                   :params {:username username :password password}
                   ;;:params form-data
                   :format (ajax/json-request-format {:keywords? true})
                   :response-format (ajax/text-response-format)
                   :on-success [:events/login-success]
                   :on-failure [:events/login-failure]}})))

(reg-event-db
 :events/login-success
 (fn [db [_ data]]
   (js/console.log "Login success" data)
   (assoc db :token data)))

(reg-event-db
 :events/login-failure
 (fn [db [_ data]]
   (js/console.log "Login failed!" data)
   (dissoc db :token)))

;;subscriptions

(reg-sub
  :page
  (fn [db _]
    (:page db)))

(reg-sub
  :docs
  (fn [db _]
    (:docs db)))

(reg-sub
 :subs/ajax-data
 (fn [db]
   (:ajax-data db)))

(reg-sub
 :subs/service-1-data
 (fn [db]
   (:service-1-data db)))
