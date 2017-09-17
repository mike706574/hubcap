(ns hubcap.three-test
  (:require [clojure.test :refer [deftest is testing]]
            [hubcap.three :as three]))

(def random (java.security.SecureRandom.))

(defn rand-str
  ([] (rand-str 26 32))
  ([len] (rand-str len 32))
  ([len radix] (let [s (.toString (java.math.BigInteger. (* 5 len) random) radix)]
                 (if (== (count s) len)
                   s
                   (rand-str len radix)))))

(def username "mike706574")
(def config {:hubcap/credentials {:hubcap/token (slurp "dev-resources/token.txt")}})
(def client (three/client config))

(deftest creating-and-deleting
  (let [name (rand-str)]
    (is (= {:status :ok, :body {:name name}}
           (three/create-repo (three/client config) name "Test.")))
    (is (= {:status :ok}
           (three/delete-repo (three/client config) username name)))))
