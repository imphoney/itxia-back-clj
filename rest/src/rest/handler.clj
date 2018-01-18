(ns rest.handler
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:use cheshire.core)
  (:use liberator.dev)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.java.jdbc :as jdbc]
            [liberator.core :refer [resource defresource]]
            [compojure.core :refer [defroutes ANY]]))

(def db-spec
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname "//47.74.128.150:3306/db"
   :user "itxia"
   :password "itxia"})

(defn pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections (* 30 60))
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))

(def pooled-db (delay (pool db-spec)))

(defn db-connection [] @pooled-db)

(def fruit-table-ddl
  (jdbc/create-table-ddl :fruits
                         [[:name "varchar(32)"]
                          [:appearance "varchar(32)"]
                          [:cost :int]
                          [:grade :real]]))

(def drop-fruit-table-ddl (jdbc/drop-table-ddl :fruits))

(jdbc/with-db-connection [db-con (db-connection)] 
  (let [tables (jdbc/query db-con ["select table_name from information_schema.tables where table_name='fruits'"])]
    (cond
      (empty? tables) (jdbc/db-do-commands db-con
                                           [fruit-table-ddl "CREATE INDEX name_ix ON fruits ( name );"])
      :else (let [res (jdbc/query db-con ["select * from fruits"])] 
                  (if (empty? res) (jdbc/insert! db-con :fruits {:name "Pear" :appearance "green" :cost 99 :grade 93})))
    )))


(defresource fruit-item [id]
  :allowed-methods [:get :put :delete]
  :available-media-types ["application/json"]
  :exists? (fn [_] (let [res (jdbc/query (db-connection) ["select * from fruits where name = ?" id])] (if-not (empty? res) {::res (generate-string res)})))
  :handle-ok ::res
  :can-put-to-missing? false
  :delete! (fn [_] (jdbc/delete! (db-connection) :fruits ["name = ?" id]))
  )


(defresource fruit-collection
  :allowed-methods [:get :put]
  :available-media-types ["application/json"]
  )
(defroutes app
  (ANY "/fruits/:id" [id] (fruit-item id)))

(def handler
  (-> app
      wrap-params
      (wrap-trace :header :ui)))
