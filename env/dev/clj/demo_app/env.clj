(ns demo-app.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [demo-app.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[demo-app started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[demo-app has shut down successfully]=-"))
   :middleware wrap-dev})
