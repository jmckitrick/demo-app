(ns ^:figwheel-no-load demo-app.app
  (:require [demo-app.core :as core]
            [devtools.core :as devtools]))

(enable-console-print!)

(devtools/install!)

(core/init!)
