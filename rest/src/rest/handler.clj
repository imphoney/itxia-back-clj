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

;; configure the mysql scheme
(def db-spec
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname "//47.74.128.150:3306/db"
   :user "itxia"
   :password "itxia"})

;; use c3p0 transaction pool to connect to the db
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

;; a delay func ensures the pool is singleten
(def pooled-db (delay (pool db-spec)))

(defn db-connection [] @pooled-db)


(def fruit-table-ddl
  (jdbc/create-table-ddl :fruits
                         [[:name "varchar(32)"]
                          [:appearance "varchar(32)"]
                          [:cost :int]
                          [:grade :real]]))

(def forms-table-ddl
  (jdbc/create-table-ddl :forms
                         [[:phone "varchar(32)"]
                          [:name "varchar(32)"]
                          [:mail "varchar(32)"]
                          [:school "varchar(32)"]
                          [:model "varchar(32)"]
                          [:os "varchar(32)"]
                          [:discription "varchar(255)"]
                          [:timestamp "DATETIME"]]))


(def drop-fruit-table-ddl (jdbc/drop-table-ddl :fruits))

(jdbc/with-db-connection [db-con (db-connection)] 
  (let [tables (jdbc/query db-con ["select table_name from information_schema.tables where table_name='forms'"])]
    (cond
      (empty? tables) (jdbc/db-do-commands db-con
                                           [forms-table-ddl])
      :else (let [res (jdbc/query db-con ["select * from forms"])] 
                  (if (empty? res) (jdbc/insert! db-con :forms {:name "Pear" :appearance "green" :cost 99 :grade 93})))))
  )


(defresource fruit-item [id]
  :allowed-methods [:get :put :delete]
  :available-media-types ["application/json"]
  :last-modified (* 10000 (long  (/ (System/currentTimeMillis) 10000)))
  :exists? (fn [_] (let [res (jdbc/query (db-connection) ["select * from fruits where name = ?" id])] (if-not (empty? res) {::res (generate-string (first res))})))
  :handle-ok ::res
  :can-put-to-missing? false
  :delete! (fn [_] (jdbc/delete! (db-connection) :fruits ["name = ?" id]))
  )


(defresource fruit-collection 
  :allowed-methods [:get :post]
  :available-media-types ["application/json"]
  :exists? (fn [_] (let [res (jdbc/query (db-connection) ["select * from fruits"])] (if-not (empty? res) {::res (generate-string res)})))
  :post! (fn [ctx] (jdbc/with-db-connection [db-con (db-connection)]
                      (let [fruit (parse-string (slurp (get-in ctx [:request :body])) true)]
                        (jdbc/insert! db-con :fruits fruit)
                        {::name (:name fruit)})))
  :location ::name
  :handle-ok ::res
  )

(defroutes app
  (ANY "/fruits/:id" [id] (fruit-item id))
  (ANY "/fruits" [] fruit-collection)
  )

(def handler
  (-> app
      wrap-params
;;      (wrap-trace :header :ui)
      ))
