(ns rest.db
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:require [clojure.java.jdbc :as jdbc]))

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

;; a delay func ensures the pool is singleten
(def pooled-db (delay (pool db-spec)))

(defn db-connection [] @pooled-db)

(def create-forms-ddl
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

(def drop-forms-ddl
  (jdbc/drop-table-ddl :forms))

(def example-user
  {:phone "15850551102"
   :name "chenhao" 
   :mail "imphoney@163.com" 
   :school "gulou" 
   :model "dell" 
   :os "win10/x86" 
   :discription "I got sth wrong"})

(defn init []
  (jdbc/with-db-connection [db-con (db-connection)] 
    (let [tables (jdbc/query db-con ["select table_name from information_schema.tables where table_name='forms'"])]
      (cond
        (empty? tables) (do (jdbc/db-do-commands db-con [create-forms-ddl]) (jdbc/insert! db-con :forms example-user))
        :else (let [res (jdbc/query db-con ["select * from forms"])] 
                    (if (empty? res) (jdbc/insert! db-con :forms example-user))))
      (println "init the db..."))))

(defn destroy []
  (jdbc/with-db-connection [db-con (db-connection)] 
    (let [tables (jdbc/query db-con ["select table_name from information_schema.tables where table_name='forms'"])]
      (if-not (empty? tables)
        (jdbc/db-do-commands db-con [drop-forms-ddl]))
      (println "drop the db...")))
  )