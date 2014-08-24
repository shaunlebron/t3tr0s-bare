(defproject t3tr0s "0.1.0-SNAPSHOT"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2311"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.0-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.0.3"]]

  :source-paths ["src"]

  :cljsbuild { 
    :builds {
      :game {
        :source-paths ["src/game"]
        :compiler {
          :output-to "public/game.js"
          :output-dir "public/out"
          :optimizations :whitespace}}
     }}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :injections [(require 'weasel.repl.websocket)
               (def brepl #(cemerick.piggieback/cljs-repl :repl-env (weasel.repl.websocket/repl-env)))]

  )
