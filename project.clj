(defproject spicy-github "0.1.0-SNAPSHOT"
    :description "FIXME: write description"
    :url "http://example.com/FIXME"
    :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
              :url  "https://www.eclipse.org/legal/epl-2.0/"}
    :dependencies [[org.clojure/clojure "1.11.1"]
                   [org.clojure/clojurescript "1.11.60"]
                   [org.clojure/data.json "2.4.0"]
                   [dev.nubank/clj-github "0.6.2"]
                   [clj-http "3.12.3"]
                   [cheshire "5.11.0"]
                   [ring "1.10.0"]
                   [ring-cors "0.1.13"]
                   [ring/ring-defaults "0.3.4"]
                   [metosin/malli "0.12.0"]
                   [hiccup "2.0.0-RC1"]
                   [compojure "1.7.0"]
                   [com.github.seancorfield/next.jdbc "1.3.883"]
                   [kwrooijen/gungnir "0.0.2-SNAPSHOT"]
                   [defun "0.3.1"]
                   [clj-time "0.15.2"]
                   [throttler "1.0.1"]
                   [net.cgrand/xforms "0.19.5"]
                   [honeysql "1.0.461"]
                   [rum "0.12.11"]
                   [lein-environ "1.2.0"]
                   [stylefy "3.2.0"]
                   [stylefy/rum "3.0.0"]
                   [com.taoensso/timbre "6.3.1"]
                   ;[com.fzakaria/slf4j-timbre "0.4.0"]
                   [clojure-interop/java.io "1.0.5"]
                   [cljs-http "0.1.46"]
                   [org.clojure/tools.cli "1.0.219"]]
    :repositories [["github" "https://github.com/clojure-interop/java-jdk"]]
    :main spicy-github.core
    :aot [spicy-github.core]
    :target-path "target/%s"
    :plugins [[lein-ring "0.12.6"]
              [lein-cljsbuild "1.1.8"]
              [lein-environ "1.2.0"]]
    :hooks [leiningen.cljsbuild]
    :ring {:handler spicy-github.api/app}
    :cljsbuild {:builds [{:source-paths ["front_end/src"]
                          :compiler     {:output-to     "resources/public/javascript/front_end.js"
                                         :output-dir    "resources/public/javascript/output/"
                                         :optimizations :advanced
                                         :pretty-print  false
                                         :source-map    "resources/public/javascript/front_end.js.map"}
                          :jar          true}]}
    :clean-targets ^{:protect false} [:target-path "resources/public/javascript/front_end.js" "resources/public/javascript/front_end.js.map" "resources/public/javascript/output"]
    :profiles {:dev          [:project/dev :profiles/dev]
               :run          [:project/dev :profiles/dev]
               :uberjar      {:aot      :all
                              :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
               :profiles/dev {}
               :project/dev  {:dependencies [[javax.servlet/servlet-api "2.5"]
                                             [ring/ring-mock "0.3.2"]]}}

    :aliases {"db-reset"    ["run" "-m" "spicy-github.db/reset-db!"]
              "db-migrate"  ["run" "-m" "spicy-github.db/migrate-db!"]
              "db-rollback" ["run" "-m" "spicy-github.db/rollback-db!"]
              "build"       ["do" "clean" ["uberjar"]]
              "run-clean"   ["do" "clean" ["run"]]})
