(defproject spicy-github "0.1.0-SNAPSHOT"
    :description "FIXME: write description"
    :url "http://example.com/FIXME"
    :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
              :url  "https://www.eclipse.org/legal/epl-2.0/"}
    :dependencies [;; [org.clojure/clojure "1.11.1"] need to remove the official compiler for ClojureStorm to work
                   [org.clojure/clojurescript "1.11.60"]
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
                   ;; This conflicts with [org.postgresql/postgresql "42.5.1"] being dragged by
                   ;; kwrooijen/gungnir
                   ;; Both artifacts contains the same classes inside. The bug was exposed by
                   ;; the clojure exclusion making lein change the order of the classpath and
                   ;; making this driver loaded, which doesn't work because of some auth reasons.
                   ;; This bug was also preventing the project from runnin with deps.edn (even without the debugger), since it
                   ;; chooses a different classpath order than lein.
                   ;; [postgresql "9.3-1102.jdbc41"]
                   [kwrooijen/gungnir "0.0.2-SNAPSHOT"]
                   [defun "0.3.1"]
                   [clj-time "0.15.2"]
                   [throttler "1.0.1"]
                   [net.cgrand/xforms "0.19.5"]
                   [honeysql "1.0.461"]
                   [rum "0.12.11"]
                   [environ "1.2.0"]
                   [stylefy "3.2.0"]
                   [stylefy/rum "3.0.0"]
                   [com.taoensso/timbre "6.3.1"]
                   ;[com.fzakaria/slf4j-timbre "0.4.0"]
                   [cljs-http "0.1.46"]]
    :main ^:skip-aot spicy-github.core
    ;; for using the debugger (and for other things) you need to be careful
    ;; with AOT, since the classes are compiled only once and instead of .clj
    ;; being loaded and instrumented Clojure will just load the .class files.
    ;; :aot [spicy-github.core]
    :target-path "target/%s"
    :plugins [[lein-ring "0.12.6"]
              [lein-cljsbuild "1.1.8"]
              [lein-environ "1.2.0"]]
    :hooks [leiningen.cljsbuild]
    :ring {:handler spicy-github.api/app}
    :cljsbuild {:builds [{:source-paths ["front_end/src"]
                          :compiler     {:output-to     "resources/public/javascript/front_end.js"
                                         :optimizations :advanced
                                         :pretty-print  false}
                          :jar          true}]}
    :profiles {:dev          [:project/dev :profiles/dev]
               :uberjar      {:aot      :all
                              :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
               :profiles/dev {}
               :project/dev  {:dependencies [[javax.servlet/servlet-api "2.5"]
                                             [ring/ring-mock "0.3.2"]

                                             ;; IntelliJ for some reason doesn't add it, running with lein repl works fine, but we can force it here
                                             [org.nrepl/incomplete "0.1.0"]

                                             [com.github.jpmonettas/clojure "1.11.1-11"]
                                             [com.github.jpmonettas/flow-storm-dbg "3.7.5"]]
                              :exclusions [org.clojure/clojure] ;; for disabling the official compiler

                              :jvm-opts ["-Dclojure.storm.instrumentEnable=true"
                                         "-Dclojure.storm.instrumentOnlyPrefixes=spicy-github"
                                         "-Dflowstorm.startRecording=false"]
                              :env {:rds-hostname "localhost"
                                    :rds-port "5432"
                                    :rds-db-name "postgres"
                                    :rds-username "postgres"
                                    :rds-password "mysecretpassword"
                                    :reload-server false}}}
    :aliases {"db-initialize" ["run" "-m" "spicy-github.db/initialize-db!"]
              "db-migrate"    ["run" "-m" "spicy-github.db/migrate-db!"]
              "db-rollback"   ["run" "-m" "spicy-github.db/rollback-db!"]})
