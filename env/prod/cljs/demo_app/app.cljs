(ns demo-app.app
  (:require [demo-app.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
