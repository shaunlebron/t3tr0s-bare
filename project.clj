(defproject t3tr0s "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]
  :plugins [[lein-figwheel "0.4.0"]]
  :figwheel {:nrepl-port 7888}
  :source-paths ["src"]
  :clean-targets ^{:protect false} [:target-path "resources/public/out" "resources/public/game.js"]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :figwheel true
                        :compiler {:main game.core
                                   :asset-path "out"
                                   :output-to "resources/public/game.js"
                                   :output-dir "resources/public/out"}}]})
