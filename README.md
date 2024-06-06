# spicy-github

The spice must flow.

## Dev

Install packages

    $ yarn install

To run

    $ lein run --scrape=false

To compile ClojureScript for dev

    $ npx shadow-cljs compile app

To auto-watch and re-compile Clojurescript on the fly

    $ npx shadow-cljs watch app

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