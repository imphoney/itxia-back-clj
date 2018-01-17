(ns rest.handler
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:use compojure.core)
  (:use cheshire.core)
  (:use ring.util.response)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [clojure.java.jdbc :as jdbc]))

(def db-spec
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname "//127.0.0.1:3306/db"
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
  (jdbc/create-table-ddl :fruit
                         [[:name "varchar(32)"]
                          [:appearance "varchar(32)"]
                          [:cost :int]
                          [:grade :real]]))

(def drop-fruit-table-ddl (jdbc/drop-table-ddl :fruit))

(jdbc/with-db-connection [db-con (db-connection)] 
  (let [
        tables (jdbc/query db-con ["Show tables"] )]
    (cond
      (empty? tables) (jdbc/db-do-commands db-con
                                           [fruit-table-ddl
				           "CREATE INDEX name_ix ON fruit ( name );"]))))

(defroutes app-routes
  (GET "/" [] "Hello World")
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)))
