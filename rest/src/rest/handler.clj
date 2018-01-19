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


(def forms-table-ddl
  (jdbc/create-table-ddl :forms
                         [[:id :int "PRIMARY KEY" "NOT NULL" "AUTO_INCREMENT"]
                          [:phone "varchar(32)" "NOT NULL"]
                          [:name "varchar(32)" "NOT NULL"]
                          [:mail "varchar(32)" "NOT NULL"]
                          [:school "varchar(32)" "NOT NULL"]
                          [:model "varchar(32)" "NOT NULL"]
                          [:os "varchar(32)" "NOT NULL"]
                          [:discription "varchar(255)" "NOT NULL"]
                          [:created_at "TIMESTAMP" "NOT NULL" "DEFAULT CURRENT_TIMESTAMP" "ON UPDATE CURRENT_TIMESTAMP"]]))

(def timestamp-as-seconds #(* 1000 (long  (/ (System/currentTimeMillis) 1000))))

(def example-user
  {:phone "15850551102"
   :name "chenhao" 
   :mail "imphoney@163.com" 
   :school "gulou" 
   :model "dell" 
   :os "win10/x86" 
   :discription "I got sth wrong"})

(jdbc/with-db-connection [db-con (db-connection)] 
  (let [tables (jdbc/query db-con ["select table_name from information_schema.tables where table_name='forms'"])]
    (cond
      (empty? tables) (do (jdbc/db-do-commands db-con [forms-table-ddl]) (jdbc/insert! db-con :forms example-user))
      :else (let [res (jdbc/query db-con ["select * from forms"])] 
                  (if (empty? res) (jdbc/insert! db-con :forms example-user)))))
  )


(defresource form-item [id]
  :allowed-methods [:get :put :delete]
  :available-media-types ["application/json"]
  :last-modified #(:created_at (::res %))
  :exists? (fn [_] (let [res (jdbc/query (db-connection) ["select * from forms where id = ?" id])] (if-not (empty? res) {::res (first res)})))
  :handle-ok #(generate-string (::res %))
  :can-put-to-missing? false
  :delete! (fn [_] (jdbc/delete! (db-connection) :forms ["name = ?" id]))
  )


(defresource form-collection 
  :allowed-methods [:get :post]
  :available-media-types ["application/json"]
  :exists? (fn [_] (let [res (jdbc/query (db-connection) ["select * from forms"])] (if-not (empty? res) {::res (generate-string res)})))
  :post! (fn [ctx] (jdbc/with-db-transaction [db-con (db-connection)]
                      (let [form (parse-string (slurp (get-in ctx [:request :body])) true)]
                        (jdbc/insert! db-con :forms form)
                        {::name (:name form)})))
  :location ::name
  :handle-ok ::res
  )

(defroutes app
  (ANY "/forms/:id" [id] (form-item id))
  (ANY "/forms" [] form-collection)
  )

(def handler
  (-> app
      wrap-params
;;      (wrap-trace :header :ui)
      ))
