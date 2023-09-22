(defproject spicy-github "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/data.json "2.4.0"]
                 [dev.nubank/clj-github "0.6.2"]
                 [clj-http "3.12.3"]
                 [cheshire "5.11.0"]
                 [ring "1.10.0"]
                 [ring/ring-defaults "0.3.4"]
                 [metosin/malli "0.12.0"]
                 [hiccup "2.0.0-RC1"]
                 [compojure "1.7.0"]
                 [com.github.seancorfield/next.jdbc "1.3.883"]
                 [postgresql "9.3-1102.jdbc41"]
                 [kwrooijen/gungnir "0.0.2-SNAPSHOT"]
                 [defun "0.3.1"]
                 [clj-time "0.15.2"]
                 [throttler "1.0.1"]
                 [net.cgrand/xforms "0.19.5"]
                 [honeysql "1.0.461"]]
  :main ^:skip-aot spicy-github.core
  :target-path "target/%s"
  :plugins [[lein-ring "0.12.6"]]
  :ring {:handler spicy-github.api/app}
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.2"]]}}
  :aliases {
            "db-initialize" ["run" "-m" "spicy-github.db/initialize-db!"]
            "db-migrate" ["run" "-m" "spicy-github.db/migrate-db!"]
            "db-rollback" ["run" "-m" "spicy-github.db/rollback-db!"]
            })
