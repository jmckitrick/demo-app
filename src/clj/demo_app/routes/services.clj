(ns demo-app.routes.services
  (:require [ring.util.http-response :refer :all]
            [clojure.tools.logging :as log]
            [compojure.api.sweet :refer :all]
            [demo-app.middleware :as middleware]
            [compojure.api.meta :refer [restructure-param]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [buddy.sign.jwt :as jwt]
            [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]))

(defn access-error [_ _]
  (unauthorized {:error "unauthorized"}))

(defn wrap-restricted [handler rule]
  (restrict handler {:handler  rule
                     :on-error access-error}))

(defn check-auth? [req]
  (log/debug "Check auth on req")
  (log/debug ":session ?" (:session req))
  (log/debug ":identity ?" (:identity req))
  (authenticated? req))

(defn check-auth-2? [req]
  (log/debug "Check auth TOKEN on req")
  (log/debug ":headers ?" (:headers req))
  (log/debug ":session ?" (:session req))
  (log/debug ":identity ?" (:identity req))
  (authenticated? req))

(defmethod restructure-param :auth-rules
  [_ rule acc]
  (update-in acc [:middleware] conj [wrap-restricted rule]))

(defmethod restructure-param :current-user
  [_ binding acc]
  (update-in acc [:letks] into [binding `(:identity ~'+compojure-api-request+)]))

(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::z spec/number?)
(s/def ::total spec/int?)
(s/def ::total-map (s/keys :req-un [::total]))

(s/def ::username spec/string?)
(s/def ::password spec/string?)
(s/def ::token spec/string?)
(s/def ::token-map (s/keys :req-un [::token]))

(def service-routes
  (api
   {:swagger {:ui "/swagger-ui"
              :spec "/swagger.json"
              :data {:info {:version "1.0.0"
                            :title "TST Agent API"
                            :description "Admin Services"}
                     :tags [{:name "thingie" :description "Requires a header token for authorization"}
                            {:name "thingie2" :description "Requires a header token for authorization"}]}}}

   (GET "/authenticated" []
     :middleware [middleware/wrap-session-auth]
     :auth-rules check-auth? ;;authenticated?
     :current-user user
     (ok {:user user}))

   (POST "/login" req
     :coercion :spec
     :summary "login and start a session"
     :body-params [username :- ::username
                   password :- ::password]
     (log/debug "Got params" (:params req))
     (log/debug "Got body params" (:body-params req))
     (log/debug "Got route params" (:route-params req))
     (log/debug "Got form params" (:form-params req))
     (log/debug "Got content type" (:content-type req))
     (log/debug "Got headers" (:headers req))
     (log/debug "Got session" (:session req))
     (let [{:keys [session]} req]
       (-> (ok)
           (assoc-in [:session :identity] {:user "jcm-session-identity"}))))

   (GET "/logout" req
     :summary "clear session and/or tokens and logout"
     (-> (ok)
         (assoc :session nil)))

   (context "/api-tokens" []
     :coercion :spec

     (POST "/login" req
     :summary "login and return a token"
     :body-params [username :- ::username
                   password :- ::password]
     :return ::token-map
     (let [{:keys [session]} req
           token  (jwt/sign {:user 999} middleware/secret)]
       (ok {:token token})))

     (GET "/foo" req
       :header-params [authorization :- ::token]
       :middleware [middleware/wrap-token-auth]
       :auth-rules check-auth-2? #_authenticated?
       :current-user user
       :summary      "foo"
       (comment
         (log/debug "Got auth:" authorization)
         (log/debug "For req keys:" (keys req))
         (log/debug "For req identity?" (:identity req))
         (log/debug "For user?" user))
       (ok {:data 88})))

   (context "/api" []
     :tags ["thingie"]
     :coercion :spec

     (GET "/plus" []
       :return       ::total-map
       :query-params [x :- ::x
                      {y :- ::y 1}]
       :summary      "x+y with query-parameters. y defaults to 1."
       (ok {:total (+ x y)}))

     (POST "/minus" []
       :return      ::total
       :body-params [x :- ::x y :- ::y]
       :summary     "x-y with body-parameters."

       (ok (- x y)))

     (GET "/times/:x/:y" []
       :return      ::total
       :path-params [x :- ::x y :- ::y]
       :summary     "x*y with path-parameters"
       (ok (* x y)))

     (POST "/divide" []
       :return      ::z
       :form-params [x :- ::x y :- ::y]
       :summary     "x/y with form-parameters"
       (ok (/ x y)))

     (GET "/power" []
       :return      ::total
       :header-params [x :- ::x y :- ::y]
       :summary     "x^y with header-parameters"
       (ok (long (Math/pow x y))))

     (context "/data-plus" []
       (resource
        {:get
         {:summary "data-driven plus with spec"
          :parameters {:query-params (s/keys :req-un [::x ::y])}
          :responses {200 {:schema ::total-map}}
          :handler (fn [{{:keys [x y]} :query-params}]
                     (ok {:total (+ x y)}))}})))))
