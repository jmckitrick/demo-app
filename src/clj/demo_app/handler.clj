(ns demo-app.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [demo-app.layout :refer [error-page]]
            [demo-app.routes.home :refer [home-routes]]
            [demo-app.routes.services :refer [service-routes]]
            [demo-app.routes.websockets :refer [websocket-routes]]
            [compojure.route :as route]
            [demo-app.env :refer [defaults]]
            [mount.core :as mount]
            [demo-app.middleware :as middleware]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(mount/defstate app
  :start
  (middleware/wrap-base
   (routes
    (->
     #'home-routes
     (wrap-routes middleware/wrap-csrf)
     (wrap-routes middleware/wrap-formats))

    #'websocket-routes
    #'service-routes

    (route/not-found
     (:body
      (error-page {:status 404
                   :title "page not found"}))))))
