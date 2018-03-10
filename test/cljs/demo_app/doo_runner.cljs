(ns demo-app.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [demo-app.core-test]))

(doo-tests 'demo-app.core-test)

