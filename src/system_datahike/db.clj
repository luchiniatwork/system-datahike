(ns system-datahike.db
  (:require [clojure.core.async :refer [<! >! go go-loop chan close!]]
            [clojure.java.io :as io]
            [datahike.api :as d]
            [hodur-datomic-schema.core :as hodur-datomic]
            [hodur-engine.core :as hodur]
            [integrant.core :as ig]
            [taoensso.timbre :refer [debug info warn error fatal report]]))


(defn ^:private schema-initializer
  [resource schema-fn]
  (-> resource
      io/resource
      hodur/init-path
      schema-fn))


(defmethod ig/init-key ::schema [_ {:keys [resource]}]
  (info {:msg "Initializing Hodur's DB (Datomic) Schema"
         :resource resource})
  (schema-initializer resource hodur-datomic/schema))


(defn ^:private create-db* [cfg]
  (when (not (d/database-exists? cfg))
    (d/create-database cfg)))


(defn ^:private init-db* [{:keys [cfg schema seed]}]
  (let [conn (d/connect cfg)]
    (d/transact conn schema)
    (when seed
      (d/transact conn seed))
    conn))


(defmethod ig/init-key ::conn [_ {:keys [cfg schema seed] :as opts}]
  (info {:msg "Initializing DB (Datahike)"
         :schema schema
         :cfg cfg
         :seed seed})
  (create-db* cfg)
  (init-db* opts))


(defmethod ig/halt-key! ::conn [_ conn]
  (info {:msg "Halting DB (Datahike)"})
  (d/release conn))
