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
 :ajax-1
 (fn [{:keys [db]} _]
   (js/console.log "Get ajax")
   {:db (assoc db :spinner true)
    :http-xhrio {:method :get
                 :uri "http://ip.jsontest.com/"
                 :timeout 8000
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:good-ajax]
                 :on-failure [:bad-ajax]}}))

(reg-event-db
 :good-ajax
 (fn [db [_ data]]
   (js/console.log "Got good ajax")
   (assoc db :ajax-data data :spinner false)))

(reg-event-db
 :bad-ajax
 (fn [db [_ data]]
   (js/console.log "Got bad ajax" data)
   (assoc db :spinner false)))

(reg-event-fx
 :service-1
 (fn [{:keys [db]} _]
   (js/console.log "Get ajax")
   {:db (assoc db :spinner true)
    :http-xhrio {:method :get
                 :uri "/api/plus"
                 :params {:x 1 :y 2}
                 :timeout 8000
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:good-service-1]
                 :on-failure [:bad-ajax]}}))

(reg-event-db
 :good-service-1
 (fn [db [_ data]]
   (js/console.log "Got good service-1")
   (js/console.log "Service 1:" (string? data))
   (assoc db :service-1-data data :spinner false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SESSIONS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :login
 (fn [{:keys [db]} [_ username password]]
   (js/console.log "Login with" username password)
   {:http-xhrio {:uri "/login"
                 :method :post
                 :params {:username username :password password}
                 :format (ajax/json-request-format {:keywords? true})
                 :response-format (ajax/text-response-format)
                 :on-success [:login-success]
                 :on-failure [:login-failure]}}))

(reg-event-db
 :login-success
 (fn [db [_ data]]
   (js/console.log "Login success" data)
   (assoc db :logged-in true)))

(reg-event-db
 :login-failure
 (fn [db [_ data]]
   (js/console.log "Login failed!" data)
   (dissoc db :logged-in)))

(reg-event-fx
 :logout
 (fn [{:keys [db]}]
   (js/console.log "Logout")
   {:http-xhrio {:uri "/logout"
                 :method :get
                 :response-format (ajax/text-response-format)
                 :on-success [:logout-success]
                 :on-failure [:logout-failure]}}))

(reg-event-db
 :logout-success
 (fn [db [_ data]]
   (js/console.log "Logout success" data)
   (dissoc db :logged-in :auth-test-data)))

(reg-event-db
 :logout-failure
 (fn [db [_ data]]
   (js/console.log "Logout failed!" data)
   (dissoc db :logged-in :auth-test-data)))

(reg-event-fx
 :auth-test-1
 (fn [{:keys [db]}]
   {:http-xhrio {:uri "/authenticated"
                 :method :get
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:auth-test-success]
                 :on-failure [:auth-test-failure]}}))

(reg-event-db
 :auth-test-success
 (fn [db [_ data]]
   (js/console.log "Auth by session success" data)
   (-> db
       (dissoc :auth-failed)
       (assoc :auth-test-data data))))

(reg-event-db
 :auth-test-failure
 (fn [db [_ data]]
   (js/console.log "Auth by session fail" data)
   (-> db
       (assoc :auth-failed (= 401 (:status data)))
       (dissoc :auth-test-data))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TOKENS
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-fx
 :login-token
 (fn [{:keys [db]} [_ username password]]
   (js/console.log "Login with" username password)
   {:http-xhrio {:uri "/api-tokens/login"
                 :method :post
                 :params {:username username :password password}
                 :format (ajax/json-request-format {:keywords? true})
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:login-success-token]
                 :on-failure [:login-failure-token]}}))

(reg-event-db
 :login-success-token
 (fn [db [_ data]]
   (js/console.log "Login by token success" data)
   (assoc db :token (:token data))))

(reg-event-db
 :login-failure-token
 (fn [db [_ data]]
   (js/console.log "Login by token failed!" data)
   (dissoc db :token)))

#_
(reg-event-fx
 :logout
 (fn [{:keys [db]}]
   (js/console.log "Logout")
   {:http-xhrio {:uri "/logout"
                 :method :get
                 :response-format (ajax/text-response-format)
                 :on-success [:logout-success]
                 :on-failure [:logout-failure]}}))
#_
(reg-event-db
 :logout-success
 (fn [db [_ data]]
   (js/console.log "Logout success" data)
   (dissoc db :logged-in :auth-test-data :token)))
#_
(reg-event-db
 :logout-failure
 (fn [db [_ data]]
   (js/console.log "Logout failed!" data)
   (dissoc db :logged-in :auth-test-data :token)))

(reg-event-fx
 :auth-test-2
 (fn [{:keys [db]} [_ with-token-header?]]
   {:http-xhrio {:uri "/api-tokens/foo"
                 :method :get
                 :headers {:authorization
                           (when with-token-header?
                             (str "Bearer " (:token db)))}
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:auth-test-token-success]
                 :on-failure [:auth-test-token-failure]}}))

(reg-event-db
 :auth-test-token-success
 (fn [db [_ data]]
   (js/console.log "Auth by token success" data)
   (-> db
       (dissoc :auth-failed)
       (assoc :auth-test-token-data data))))

(reg-event-db
 :auth-test-token-failure
 (fn [db [_ data]]
   (js/console.log "Auth by token fail" data)
   (-> db
       (assoc :auth-failed (= 401 (:status data)))
       (dissoc :auth-test-token-data))))

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
 :ajax-data
 (fn [db]
   (:ajax-data db)))

(reg-sub
 :service-1-data
 (fn [db]
   (:service-1-data db)))

(reg-sub
 :logged-in
 (fn [db]
   (js/console.log "Are we logged-in sub?" (:logged-in db))
   (:logged-in db)))

(reg-sub
 :auth-test-data
 (fn [db]
   (:auth-test-data db)))

(reg-sub
 :auth-test-token-data
 (fn [db]
   (:auth-test-token-data db)))

(reg-sub
 :auth-failed
 (fn [db]
   (:auth-failed db)))

(reg-sub
 :token
 (fn [db]
   (:token db)))
#_
(reg-sub
 :db
 (fn [db]
   db))
