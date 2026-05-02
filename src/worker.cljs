(ns worker
  (:require [clojure.string :as str]
            [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [cljs-workers.worker :as worker]
            [utils.net :as net]))

(worker/register :get-plugin-url-preview
  (fn [{:keys [url]}]
    (go
      (try
        (let [fetch-data
              (cond
                (re-find #"(?i)(youtube\.com|youtu\.be)" url)
                (let [noembed-url (str "https://noembed.com/embed?url=" (js/encodeURIComponent url))
                      resp        (<p! (net/fetch noembed-url))]
                  (when (.-ok resp)
                    (let [data (js->clj (<p! (.json resp)) :keywordize-keys true)]
                      (when-not (:error data)
                        {:og:title       (:title data)
                         :og:description (:author_name data)
                         :og:image       (:thumbnail_url data)
                         :og:site_name   "YouTube"}))))

                (re-find #"(?i)(twitter\.com|x\.com)" url)
                (let [vx-url (-> url
                                 (str/replace #"(?i)https://(www\.)?(twitter\.com|x\.com)" "https://api.vxtwitter.com")
                                 (str/split #"\?") first)
                      resp   (<p! (net/fetch vx-url))]
                  (when (.-ok resp)
                    (let [data (js->clj (<p! (.json resp)) :keywordize-keys true)]
                      {:og:title       (str (:user_name data) " (@" (:user_screen_name data) ")")
                       :og:description (:text data)
                       :og:image       (first (:mediaURLs data))
                       :og:site_name   "X / Twitter"})))

                (re-find #"(?i)reddit\.com" url)
                (let [reddit-oembed (str "https://www.reddit.com/oembed?url=" (js/encodeURIComponent url))
                      proxy-url     (str "https://corsproxy.io/?" (js/encodeURIComponent reddit-oembed))
                      resp          (<p! (net/fetch proxy-url))]
                  (when (.-ok resp)
                    (let [data (js->clj (<p! (.json resp)) :keywordize-keys true)]
                      {:og:title       (:title data)
                       :og:description (or (:author_name data) "Reddit Post")
                       :og:image       (:thumbnail_url data)
                       :og:site_name   "Reddit"})))

                :else nil)]

          (if fetch-data
            {:status "success" :data fetch-data}
            {:status "error" :msg "API returned empty."}))
        (catch :default e
          (js/console.error "Plugin Fetcher Panic:" e)
          {:status "error" :msg (str e)})))))
