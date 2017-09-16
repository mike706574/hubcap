(ns hubcap.three
  (:require [clojure.data.codec.base64 :as base64]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]))

(def public-api-url "https://api.github.com")
(def json-content-type "application/vnd.github.v3+json")
(def raw-content-type "application/vnd.github.v3.raw")

(defn read-body
  [response]
  (let [response-content-type (some-> response
                                      (get-in [:headers "content-type"])
                                      (str/split #";")
                                      (first))]
    (if (and (= response-content-type "application/json")
             (contains? response :body)
             (not (nil? (:body response))))
      (update response :body #(json/read-str % :key-fn keyword))
      response)))

(defprotocol GitHubVersionThreeClient
  (get-user-repos [this username])
  (create-repo [this name desc])
  (delete-repo [this owner name])
  (create-file [this owner name ])
  (delete-file [this name desc])
  (get-contents [this owner name path]))

(defn request
  [{:keys [token username password]} {:keys [secure? throw-exceptions]}]
  (let [request {:headers {"Accept" json-content-type
                           "Content-Type" "application/json"}
                 :client-params {"http.useragent" "clj-http"}
                 :throw-exceptions (or throw-exceptions false)
                 :secure? (and secure? true)}]
    (if token
      (update request :headers assoc "Authorization" (str "token " token))
      (assoc request :basic-auth [username password]))))

(defn accept
  [content-type request]
  (assoc-in request [:headers "Content-Type"] content-type))

(defn write-body
  [body request]
  (assoc request :body (json/write-str body)))

(def comma-re #",")
(def link-re #"<(.*)>; rel=\"([a-z]+)\"")

(defn link
  [text]
  (->> text
       (str/trim)
       (re-matches link-re)
       (rest)
       (reverse)
       (vec)))

(defn links
  [response]
  (let [header (get-in response [:headers "Link"])
        parts (str/split header comma-re)]
    (into {} (map link parts))))

(defn decode-content [content]
  (-> content
      (.replace "\n" "")
      (.getBytes "UTF-8")
      (base64/decode)
      (String. "UTF-8")))

(defn parse-file [file]
  (-> file
      (select-keys [:name :size :path :type :content :encoding])
      (update :content decode-content)))

(defn parse-dir [dir]
  (map #(select-keys % [:name :size :path :type]) dir))

(defn parse-contents
  [path contents]
  (cond
    (map? contents) (parse-file contents)
    (sequential? contents) {:type "dir"
                            :path path
                            :content (parse-dir contents)}
    :else (throw (ex-info "Unexpected contents." contents))))

(defrecord HttpGitHubVersionThreeClient [url creds opts]
  GitHubVersionThreeClient
  (get-user-repos [this username]
    (loop [link nil
           repos []]
      (let [url (or link (str url "/users/" username "/repos?page=1"))
            {:keys [status body] :as response} (->> (request creds opts)
                                                    (client/get url)
                                                    (read-body))]
        (case status
          200 (let [repos (into repos (map :name body))]
                (if-let [link (get (links response) "next")]
                  (recur link repos)
                  {:status :ok :body repos}))
          404 {:status :not-found}
          (assoc response :status :error :status-code status)))))

  (create-repo [this name desc]
    (let [url (str url "/user/repos")
          {:keys [status body] :as response} (->> (request creds opts)
                                                  (write-body {:name name
                                                               :description desc
                                                               :private false})
                                                  (client/post url)
                                                  (read-body))]
      (if (= status 201)
        {:status :ok :body (select-keys body [:name])}
        (assoc response :status :error :status-code status))))

  (delete-repo [this owner name]
    (let [url (str url "/repos/" owner "/" name)
          {:keys [status body] :as response} (->> (request creds opts)
                                                  (client/delete url)
                                                  (read-body))]
      (case status
        204 {:status :ok}
        404 {:status :not-found}
        (assoc response :status :error :status-code status))))

  (get-contents [this owner name path]
    (let [url (str url "/repos/" owner "/" name "/contents/" path)
          {:keys [status body] :as response} (->> (request creds opts)
                                                  (accept raw-content-type)
                                                  (client/get url)
                                                  (read-body))]
      (case status
        200 {:status :ok :body (parse-contents path body)}
        404 {:status :not-found}
        (assoc response :status :error :status-code status)))))

(s/def :hubcap/url string?)

(s/def :hubcap/secure? boolean?)
(s/def :hubcap/throw-exceptions boolean?)
(s/def :hubcap/options (s/keys :opt [:hubcap/secure? :hubcap/throw-exceptions]))

(s/def :hubcap/token string?)
(s/def :hubcap/username string?)
(s/def :hubcap/password string?)
(s/def :hubcap/credentials (s/or :token (s/keys :req [:hubcap/token])
                                 :credentials (s/keys :req [:hubcap/username
                                                            :hubcap/password])))
(s/def :hubcap/config (s/and (s/keys :req [:hubcap/credentials]
                                     :opt [:hubcap/url :hubcap/options])))

(defn de-ns-keys [m]
  (let [f (fn [[k v]] (if (keyword? k) [(keyword (name k)) v] [k v]))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn read-config
  [config]
  (if-let [error (s/explain-data :hubcap/config config)]
    (throw (ex-info "Invalid hubcap config." error))
    (-> config
        (de-ns-keys)
        (set/rename-keys {:credentials :creds :options :opts})
        (update :url #(if % (str % "/api/v3") public-api-url)))))

(defn client
  [config]
  (map->HttpGitHubVersionThreeClient (read-config config)))
