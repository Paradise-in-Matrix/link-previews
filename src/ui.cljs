 (ns ui
  (:require-macros [utils.macros :refer [defoverride]])
  (:require [client.state :as state]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [capacitor.browser :refer [Browser]]
            [capacitor.core :refer [Capacitor]]
            [utils.images :refer [mxc->url mxc-image]]
            [clojure.string :as str]
            [cljs.core.async :refer [go <!]]
            [cljs-workers.core :as main]
            [container.timeline.item :as item]))

(re-frame/reg-event-fx
 :media/fetch-plugin-preview
 (fn [{:keys [db]} [_ url]]
   (when-not (get-in db [:url-previews url])
     (go
       (let [res (<! (main/do-with-pool! @state/!engine-pool
                                         {:handler :get-plugin-url-preview
                                          :arguments {:url url}}))]
         (if (= (:status res) "success")
           (re-frame/dispatch [:media/url-preview-success url (:data res)])
           (re-frame/dispatch [:media/url-preview-error url])))))
   {:db (update-in db [:url-previews url] #(or % {:status :loading}))}))

(def plugin-regex #"(?i)(youtube\.com|youtu\.be|twitter\.com|x\.com|reddit\.com|instagram\.com|tiktok\.com)")


(defn extract-youtube-id [url]
  (let [match (re-find #"(?i)(?:youtube\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?|shorts)/|.*[?&]v=)|youtu\.be/)([^\"&?/\s]{11})" url)]
    (when match
      (second match))))

(defoverride link-preview-card [url hs-url]
  (let [{:keys [status data]} @(re-frame/subscribe [:media/url-preview url])]
    (cond
      (= status :loading)
      [:div.link-preview-container.is-loading
       [:div.preview-skeleton]]

      (= status :error)
      nil

      (= status :success)
      (let [{:keys [og:title og:description og:image og:site_name]} data
            img-url  (when og:image
                       (if (str/starts-with? og:image "mxc://")
                         (mxc->url og:image {:homeserver hs-url :type :thumbnail :width 400 :height 200})
                         og:image))
            hostname (try (.-hostname (js/URL. url)) (catch :default _ url))
            site     (or og:site_name hostname)
            yt-id    (extract-youtube-id url)]
        (if yt-id
          [:div.youtube-embed-card
           [:div.video-wrapper
            [:iframe {:src (str "https://www.youtube.com/embed/" yt-id)
                      :credentialless "true"
                      :sandbox "allow-scripts allow-same-origin allow-presentation allow-popups allow-popups-to-escape-sandbox"
                      :allowFullScreen "true"
                      :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"}]]
           [:div.embed-content
            (when og:title
              [:a.youtube-title-link {:href url :target "_blank" :rel "noopener noreferrer"}
               og:title])
            [:div.youtube-site-label "YouTube"]]]

          (when (or og:title og:description)
            [:a.rich-embed-card {:href url :target "_blank" :rel "noopener noreferrer"}
             [:div.embed-content
              [:div.embed-site site]
              (when og:title
                [:div.embed-title og:title])
              (when og:description
                [:div.embed-description og:description])]
             (when img-url
               [:div.embed-thumbnail
                [:img {:src img-url}]])]))))))

(defoverride message-link-preview [msg-type-tag raw-body]
  (let [first-url (when (#{"Text" "Notice" "Emote"} msg-type-tag)
                    (item/extract-first-url raw-body))]
    (when first-url
      (let [hs-url        @(re-frame/subscribe [:sdk/homeserver-url])
            policy        @(re-frame/subscribe [:settings/media-preview-policy])
            room-meta     @(re-frame/subscribe [:rooms/active-metadata])
            is-private?   (= (:join-rule room-meta) "invite")
            show-preview? (or (= policy :on)
                              (and (= policy :private) is-private?))]
        (when show-preview?
          (r/create-class
           {:component-did-mount
            (fn []
              (let [preview-state @(re-frame/subscribe [:media/url-preview first-url])]
                (when-not preview-state
                  (if (re-find plugin-regex first-url)
                    (re-frame/dispatch [:media/fetch-plugin-preview first-url])
                    (re-frame/dispatch [:media/fetch-url-preview first-url])))))
            :reagent-render
            (fn []
              [link-preview-card first-url hs-url])}))))))


