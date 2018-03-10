(ns demo-app.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[demo-app started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[demo-app has shut down successfully]=-"))
   :middleware identity})
