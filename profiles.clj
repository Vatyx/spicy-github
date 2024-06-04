{:dev     {:env {:spicy-endpoint "http://localhost:3000/",
                 :reload-server "true",
                 :rds-hostname "localhost",
                 :rds-port "5432",
                 :rds-db-name "spicy-github",
                 :rds-username "postgres",
                 :rds-password "",
                 :log-level "debug"}}
 :uberjar {:env {:spicy-endpoint "https://gitfeed.app"
                 :front-end-port "80"}}
 :jar     {:env {:spicy-endpoint "https://gitfeed.app"
                 :front-end-port "80"}}}