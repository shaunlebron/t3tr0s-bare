(defproject t3tr0s "0.1.0-SNAPSHOT"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]]

  :plugins [[lein-figwheel "0.4.0"]
            [cider/cider-nrepl "0.9.1"]]

  :figwheel {:nrepl-port 7888}
  
  :source-paths ["src"]

  :target-path "target/%s"
  :clean-targets ^{:protect false} [:target-path "out"]
  :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src"]
                        :figwheel true
                        :compiler {
                                   :main game.core
                                   ;; :output-to "public/game.js"
                                   ;; :output-dir "public/out"
                                   ;; :optimizations :whitespace
                                   }}
                       {:id "prod"
                        :source-paths ["src/game"]
                        :compiler {:optimizations :advanced
                                   :output-to "target/js"}
                        :externs ["marked.min.js"]}]}
  )
