(ns demo-app.routes.services
  (:require [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [com.walmartlabs.lacinia :as lacinia]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [ring.util.http-response :refer :all]
            [ring.util.http-status :as status]
            [clojure.tools.logging :as log]
            [mount.core :refer [defstate]]
            [compojure.api.sweet :refer :all]
            [demo-app.db.core :as db]
            [demo-app.middleware :as middleware]
            [compojure.api.meta :refer [restructure-param]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [buddy.sign.jwt :as jwt]
            [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [spec-tools.core :as score]))

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
;;(s/def ::stuff (score/spec {:spec (s/keys :req-un [::x ::y]) :description "Stuff!!" :name "Name!!"}))
(s/def ::total spec/int?)
(s/def ::total-map (s/keys :req-un [::total]))

(s/def ::username spec/string?)
(s/def ::password spec/string?)
(s/def ::token spec/string?)
(s/def ::token-map (s/keys :req-un [::token]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; graphql
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-hero [context args value]
  (let [data  [{:id 1000
               :name "Luke"
               :home_planet "Tatooine"
               :appears_in ["NEWHOPE" "EMPIRE" "JEDI"]}
              {:id 2000
               :name "Lando Calrissian"
               :home_planet "Socorro"
               :appears_in ["EMPIRE" "JEDI"]}]]
    (first data)))

(defn game-by-id
  [db]
  (fn [_ args _]
    (db/find-game-by-id db (:id args))))

(defn all-games
  [db]
  (fn [_ _ _]
    (db/find-all-games db)))

(defn member-by-id
  [db]
  (fn [_ args _]
    (db/find-member-by-id db (:id args))))

(defn rate-game
  [db]
  (fn [_ args _]
    (let [{game-id :game_id
           member-id :member_id
           rating :rating} args
          game (db/find-game-by-id db game-id)
          member (db/find-member-by-id db member-id)]
      (cond
        (nil? game)
        (resolve-as nil {:message "Game not found."
                         :status 404})

        (nil? member)
        (resolve-as nil {:message "Member not found."
                         :status 404})

        (not (<= 1 rating 5))
        (resolve-as nil {:message "Rating must be between 1 and 5."
                         :status 400})

        :else
        (do
          (db/upsert-game-rating db game-id member-id rating)
          game)))))


(defn board-game-designers
  [db]
  (fn [context args board-game]
    #_(log/info "----> context" context)
    #_(log/info "----> args" args)
    #_(log/info "----> board game" board-game)
    (db/list-designers-for-game db (:game_id board-game))))

(defn designer-games
  [db]
  (fn [_ _ designer]
    (db/list-games-for-designer db (:id designer))))

(defn rating-summary
  [db]
  (fn [_ _ board-game]
    (let [ratings (map :rating (db/list-ratings-for-game db (:id board-game)))
          n (count ratings)]
      {:count n
       :average (if (zero? n)
                  0
                  (/ (apply + ratings)
                     (float n)))})))

(defn member-ratings
  [db]
  (fn [_ _ member]
    (db/list-ratings-for-member db (:id member))))

(defn game-rating->game
  [db]
  (fn [_ _ game-rating]
    (db/find-game-by-id db (:game_id game-rating))))

(defn resolver-map
  [component]
  (let [db (:db component)]
    {:query/game-by-id (game-by-id db)
     :query/member-by-id (member-by-id db)
     :query/all-games (all-games db)
     :mutation/rate-game (rate-game db)
     :BoardGame/designers (board-game-designers db)
     :BoardGame/rating-summary (rating-summary db)
     :GameRating/game (game-rating->game db)
     :Designer/games (designer-games db)
     :Member/ratings (member-ratings db)}))

(defstate compiled-schema
  :start
  (-> #_"graphql/schema.edn"
      "graphql/cgg-schema.edn"
      io/resource
      slurp
      edn/read-string
      #_(attach-resolvers {:get-hero get-hero
                         :get-droid (constantly {})})
      (attach-resolvers (resolver-map (db/new-db)))
      schema/compile))

(defn format-params [query]
  (let [parsed (json/parse-string query)] ;;-> placeholder - need to ensure query meets graphql syntax
     (str "query { hero(id: \"1000\") { name appears_in }}")))

(defn execute-request [query]
    (let [vars nil
          context nil]
    (-> (lacinia/execute compiled-schema query vars context)
        (json/generate-string))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; api definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def service-routes
  (api
   {:swagger {:ui "/swagger-ui"
              :spec "/swagger.json"
              :data {:info {:version "1.0.0"
                            :title "TST Agent API"
                            :description "Admin Services"}
                     :tags [{:name "demo" :description "Built-in demo of simple endpoints with coercion"}
                            {:name "tokens" :description "Endpoints with tokens"}
                            {:name "sessions" :description "Endpoints with sessions"}]}}}

   (GET "/authenticated" []
     :summary "return user if session authenticated"
     :tags ["sessions"]
     :middleware [middleware/wrap-session-auth]
     :auth-rules check-auth? ;;authenticated?
     :current-user user
     (ok {:user user}))

   (POST "/login" req
     :summary "login and start a session"
     :tags ["sessions"]
     :coercion :spec
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
     :summary "clear session"
     :tags ["sessions"]
     (-> (ok)
         (assoc :session nil)))

   (context "/api-tokens" []
     :tags ["tokens"]
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
       :summary      "Get user data"
       (comment
         (log/debug "Got auth:" authorization)
         (log/debug "For req keys:" (keys req))
         (log/debug "For req identity?" (:identity req))
         (log/debug "For user?" user))
       (ok {:data 88})))

   (context "/api" []
     :tags ["demo"]
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
        {:description "Addition resource"
         :get
         {:summary "data-driven plus with spec"
          :parameters {:query-params (s/keys :req-un [::x ::y])}
          :responses {status/ok {:schema ::total-map :description "Response class description here"}
                      status/internal-server-error {:schema string? :description "Error message"}}
          :handler (fn [{{:keys [x y]} :query-params}]
                     (ok {:total (+ x y)}))}})))

   (context "/api-graphql" []
     :tags ["thingie"]

     (GET "/" []
      :tags ["graphql"]
      :query-params [query :- String, {variable :- String nil}]
      :summary "GraphQL over REST. query: GraphQL query e.g {hero{name appears_in}}. variable: defaults to nil."
      (let [result (lacinia/execute compiled-schema query variable nil)]
        (if (-> result :errors seq)
          (bad-request result)
          (ok result))) )

    (POST "/" [:as {body :body}]
      :tags ["graphql"]
      :summary "entry point for GraphiQL queries"
      (ok (execute-request (slurp body)))))))
