(ns user
  (:require [luminus-migrations.core :as migrations]
            [demo-app.config :refer [env]]
            [mount.core :as mount]
            [demo-app.figwheel :refer [start-fw stop-fw cljs]]
            [demo-app.core :refer [start-app]]))

(defn start []
  (mount/start-without #'demo-app.core/repl-server))

(defn stop []
  (mount/stop-except #'demo-app.core/repl-server))

(defn restart []
  (stop)
  (start))

(defn reset-db []
  (migrations/migrate ["reset"] {:database-url (:config-database-url env)}))

(defn migrate []
  (migrations/migrate ["migrate"] (select-keys env [:database-url])))

(defn rollback []
  (migrations/migrate ["rollback"] (select-keys env [:database-url])))

(defn create-migration [name]
  (migrations/create name (select-keys env [:database-url])))

(comment
  (start)
  (restart)
  (stop)
  (def my-db (demo-app.db.core/new-db))
  (def my-map (demo-app.routes.services/resolver-map my-db))
  ;; (:query/game-by-id :query/member-by-id :mutation/rate-game :BoardGame/designers :BoardGame/rating-summary :GameRating/game :Designer/games :Member/ratings)
  (def my-resolver (:BoardGame/designers my-map))
  (def my-designers (my-resolver nil nil {:id 1234}))
  (def my-game (demo-app.db.core/find-game-by-id (demo-app.db.core/new-db) 1234))
  )
