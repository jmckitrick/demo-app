(ns demo-app.db.core
  (:require
   [clj-time.jdbc]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.java.jdbc :as jdbc]
   [conman.core :as conman]
   [demo-app.config :refer [env]]
   [mount.core :refer [defstate]]
   [postgres.async :refer [open-db query! close-db!]]
   [clojure.core.async :refer [<!!]]
   [camel-snake-kebab.extras :refer [transform-keys]]
   [camel-snake-kebab.core :refer [->kebab-case-keyword]]
   [clojure.tools.logging :as log])
  (:import [java.sql
            BatchUpdateException
            PreparedStatement]))

;; (defstate ^:dynamic *db*
;;   :start (conman/connect! {:jdbc-url (env :database-url)})
;;   :stop (conman/disconnect! *db*))

;; (conman/bind-connection *db* "sql/queries.sql")

(defn result-one-snake->kebab
  [this result options]
  (->> (hugsql.adapter/result-one this result options)
       (transform-keys ->kebab-case-keyword)))

(defn result-many-snake->kebab
  [this result options]
  (->> (hugsql.adapter/result-many this result options)
       (map #(transform-keys ->kebab-case-keyword %))))

(defmethod hugsql.core/hugsql-result-fn :1 [sym]
  'demo-app.db.core/result-one-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :one [sym]
  'demo-app.db.core/result-one-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :* [sym]
  'demo-app.db.core/result-many-snake->kebab)

(defmethod hugsql.core/hugsql-result-fn :many [sym]
  'demo-app.db.core/result-many-snake->kebab)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; fake db for graphql
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defstate db
  :start (open-db {:hostname "localhost"
                   :database "cggdb"
                   :username "cgg_role"
                   :password "lacinia"
                   :port 25432})
  :stop (close-db! db))

(defn new-db
  []
  {:db {:conn db}})

(defn ^:private take!
  [ch]
  (log/info "Private take on ch" ch)
  (let [v (<!! ch)]
    (if (instance? Throwable v)
      (throw v)
      v)))

(defn find-game-by-id
  [db game-id]
  (-> (query! (:conn db)
              ["SELECT g.game_id, g.name, g.summary, g.min_players, g.max_players, g.created_at, g.updated_at,
                d.name, d.uri AS url
                FROM board_game g
                LEFT JOIN designer_to_game dg ON g.game_id = dg.game_id
                LEFT JOIN designer d ON d.designer_id = dg.designer_id
                WHERE g.game_id = $1
               " game-id])
      take!
      first))

(defn find-member-by-id
  [db member-id]
  (-> (query! (:conn db)
              ["select member_id, name AS member_name, created_at, updated_at
                from member where member_id = $1" member-id])
      take!
      first))

(defn list-designers-for-game
  "designer_id, name, uri"
  [db game-id]
  (let [game (find-game-by-id db game-id)
        designers nil]
    (->> db
         :data
         deref
         :designers
         (filter #(contains? designers (:id %))))))

(defn list-games-for-designer
  [db designer-id]
  (->> db
       :data
       deref
       :games
       (filter #(-> % :designers (contains? designer-id)))))

(defn list-ratings-for-game
  [db game-id]
  (->> db
       :data
       deref
       :ratings
       (filter #(= game-id (:game_id %)))))

(defn list-ratings-for-member
  [db member-id]
  (->> db
       :data
       deref
       :ratings
       (filter #(= member-id (:member_id %)))))

(defn ^:private apply-game-rating
  [game-ratings game-id member-id rating]
  (->> game-ratings
       (remove #(and (= game-id (:game_id %))
                     (= member-id (:member_id %))))
       (cons {:game_id game-id
              :member_id member-id
              :rating rating})))

(defn upsert-game-rating
  "Adds a new game rating, or changes the value of an existing game rating."
  [db game-id member-id rating]
  (-> db
      :data
      (swap! update :ratings apply-game-rating game-id member-id rating)))
