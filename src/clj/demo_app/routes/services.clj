(ns demo-app.routes.services
  (:require [ring.util.http-response :refer :all]
            [compojure.api.sweet :refer :all]
            #_[schema.core :as s]
            [compojure.api.meta :refer [restructure-param]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]))

(defn access-error [_ _]
  (unauthorized {:error "unauthorized"}))

(defn wrap-restricted [handler rule]
  (restrict handler {:handler  rule
                     :on-error access-error}))

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

(def service-routes
  (api
   {:swagger {:ui "/swagger-ui"
              :spec "/swagger.json"
              :data {:info {:version "1.0.0"
                            :title "TST Agent API"
                            :description "Admin Services"}}}}

   (GET "/authenticated" []
     :auth-rules authenticated?
     :current-user user
     (ok {:user user}))
   (context "/api" []
     :tags ["thingie"]
     :coercion :spec

     (GET "/plus" []
       :return       ::total-map
       :query-params [x :- ::x {y :- ::y 1}]
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
