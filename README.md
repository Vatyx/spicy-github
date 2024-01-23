# spicy-github

The spice must flow.

## Dev

To run

    $ lein run --scrape=false

To compile ClojureScript for dev

    $ lein cljsbuild once

To auto-watch and re-compile Clojurescript on the fly

    $ lein cljsbuild auto

To initialize the db
    
    $ lein db-initialize

To roll-forward the db

    $ lein db-migrate

To roll-back the db

    $ lein db-rollback

## Building

To build the application

    $ lein clean
    $ lein uberjar