(defproject rest "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-json "0.4.0"]
                 [org.clojure/java.jdbc "0.7.5"]
                 [c3p0/c3p0 "0.9.1.2"]
                 [cheshire "5.8.0"]]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler rest.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
