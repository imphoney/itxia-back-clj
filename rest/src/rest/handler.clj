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
            [compojure.core :refer [defroutes ANY]]
            [rest.db :refer [db-connection]]
            [rest.model :refer :all]))

(defresource form-item [id]
  :allowed-methods [:get :put :delete]
  :available-media-types ["application/json"]
  :last-modified #(:created_at (:res %))
  :exists? #(get-form-by id %)
  :handle-ok #(generate-string (:res %))
  :can-put-to-missing? false
  :delete! #(delete!-form-by id %)
  )


(defresource form-collection 
  :allowed-methods [:get :post]
  :available-media-types ["application/json"]
  :exists? #(get-forms-all %)
  :post! #(insert!-form %)
  :location #(:name (:form %))
  :handle-ok #(generate-string (:res %))
  )

(defroutes app
  (ANY "/forms/:id" [id] (form-item id))
  (ANY "/forms" [] form-collection)
  )

(def handler
  (-> app
      wrap-params
      (wrap-trace :header :ui)
      ))
