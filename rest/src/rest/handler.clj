(ns rest.handler
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:use compojure.core)
  (:use cheshire.core)
  (:use ring.util.response)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            '[clojure.java.jdbc :as sql]))

(def db-spec 
  {:classname "com.mysql.jdbc.Driver"
   :subprotocol "mysql"
   :subname "//localhost:3306/db"
   :user "itxia"
   :password "itxia"})

(defn pool [spec]
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

(sql/with-connection (db-connection)
  (sql/drop-table :users)
  (sql/create-table :users [:id :integer "PRIMARY KEY" "AUTO_INCREMENT"]
                           [:fname "varchar(25)"]
                           [:lname "varchar(25)"]))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn get-user [id]
  (sql/with-connection (db-connection)
    (sql/with-query-results results
      ["select * from users where id = ?" id]
      (cond 
        (empty? results) {:status 404}
        :else (response (first results))))))

(defn create-new-user [body]
  (let [id (uuid)]
    (sql/with-connection (db-connection)
      (let [user (assoc body "id" id)]
        (sql/insert-record :users user)))
    (get-user id)))


(defroutes app-routes
  (GET "/" [] "Hello World")
  (POST "/" {body :body} (create-new-user body))
  (route/not-found "Not Found"))

(def app
  (-> (handler/api app-routes)
      (middleware/wrap-json-body)
      (middleware/wrap-json-response)))
