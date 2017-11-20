(defproject fun.mike/hubcap "0.0.1"
  :description "An incomplete GitHub API client."
  :url "https://github.com/mike706574/hubcap"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/spec.alpha "0.1.143"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/data.codec "0.1.1"]
                 [clj-http "3.7.0"]]
  :profiles {:dev {:source-paths ["dev"]
                   :target-path "target/dev"
                   :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                                  [com.taoensso/timbre "4.10.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [fun.mike/latch "0.2.3"]]}}
  :repl-options {:init-ns user})
