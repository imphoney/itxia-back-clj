(ns rest.model
  (:use cheshire.core)
  (:require [clojure.java.jdbc :as jdbc]
            [rest.db :refer [db-connection]]))
  

(def query
  (partial jdbc/query (db-connection)))

(def delete!
  (partial jdbc/delete! (db-connection)))

(def insert!
  (partial jdbc/insert! (db-connection)))

(defn get-form-by [id _]
  (let [res (query ["select * from forms where id = ?" id])] 
    (if-not (empty? res) 
      {:res (first res)})))

(defn delete!-form-by [id _]
  (delete! :forms ["id = ?" id]))

(defn get-forms-all [_]
  (let [res (query ["select * from forms"])] 
    (if-not (empty? res) 
      {:res res})))

(defn insert!-form [ctx]
  (let [form (parse-string (slurp (get-in ctx [:request :body])) true)]
    (insert! :forms form)
    {:form form}))