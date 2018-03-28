(ns demo-app.routes.home
  (:require [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth :refer [authenticated?]]
            [demo-app.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render "home.html"))

(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?}))

(defroutes other-routes
  (GET "/auth3" []
    (-> (response/ok "1")
        (response/header "Content-Type" "text/plain; charset=utf-8"))))

(defroutes home-routes
  (GET "/" []
    (home-page))
  (GET "/graphiql" []
    (layout/render "graphiql.html"))
  (GET "/docs" []
    (-> (response/ok (-> "docs/docs.md" io/resource slurp))
        (response/header "Content-Type" "text/plain; charset=utf-8"))))
