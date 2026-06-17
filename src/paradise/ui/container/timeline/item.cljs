(ns paradise.ui.container.timeline.item
  (:require-macros [paradise.shared.utils.macros :refer [defoverride]])
  (:require [paradise.shared.client.state :as state]
            [re-frame.core :as re-frame]
            [reagent.core :as r]
            [capacitor.browser :refer [Browser]]
            [capacitor.core :refer [Capacitor]]
            [paradise.shared.utils.helpers]
            [paradise.media.component :refer [mxc->url]]
            [clojure.string :as str]
            [cljs.core.async :refer [go <!]]
            [cljs-workers.core :as main]
            [paradise.ui.container.timeline.item :as item]))

(def platform (.getPlatform Capacitor))
(def is-ios? (= platform "ios"))

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
  (let [yt-id       (extract-youtube-id url)
        is-short?   (and yt-id (str/includes? url "/shorts/"))

        preview     @(re-frame/subscribe [:media/url-preview url])
        status      (:status preview)
        data        (:data preview)
        is-playing? @(re-frame/subscribe [:media/playing-inline? url])]

    (if yt-id
      (let [{:keys [og:title og:image]} data
            img-url (when og:image
                      (if (str/starts-with? og:image "mxc://")
                        (mxc->url og:image {:homeserver hs-url :type :thumbnail :width 400 :height 200})
                        og:image))]
        [:div.youtube-embed-card
         {:style {:max-width (if is-short? "320px" "520px")}}

         (if is-playing?
           [:div.video-wrapper
            {:style {:position "relative"
                     :aspect-ratio (if is-short? "9/16" "16/9")
                     :overflow "hidden"
                     :border-radius "8px"
                     :background-color "#000"}}
            [:iframe {:src (str "https://www.youtube-nocookie.com/embed/" yt-id "?autoplay=1&playsinline=1")
                      :referrerPolicy "strict-origin-when-cross-origin"
                      :credentialless "true"
                      :style {:width "100%" :height "100%" :border "none"}
                      :allowFullScreen true
                      :allow "accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"}]]
           [:div.video-wrapper
            {:style    {:position "relative"
                        :cursor "pointer"
                        :aspect-ratio (if is-short? "9/16" "16/9")
                        :overflow "hidden"
                        :border-radius "8px"
                        :background-color "#000"}
             :on-click #(if is-ios?
                          (-> Browser (.open (clj->js {:url (str "https://www.youtube.com/watch?v=" yt-id)
                                                       :presentationStyle "popover"})))
                          (re-frame/dispatch [:media/play-inline url]))}
            [:img {:src (or img-url (str "https://img.youtube.com/vi/" yt-id "/hqdefault.jpg"))
                   :style {:width "100%" :height "100%" :object-fit "cover"}}]
            [:div.play-button-overlay
             {:style {:position "absolute" :top "50%" :left "50%"
                      :transform "translate(-50%, -50%)"
                      :background "rgba(0,0,0,0.7)" :border-radius "50%"
                      :width "60px" :height "60px" :display "flex"
                      :align-items "center" :justify-content "center"}}
             [:svg {:width "30" :height "30" :viewBox "0 0 24 24" :fill "white"}
              [:path {:d "M8 5v14l11-7z"}]]]])

         [:div.embed-content
          (if og:title
            [:a.youtube-title-link {:href url :target "_blank" :rel "noopener noreferrer"} og:title]
            (when (= status :loading)
              [:div {:style {:height "16px" :width "60%" :background "var(--bg-secondary, rgba(128,128,128,0.2))" :border-radius "4px"}}]))
          [:div.youtube-site-label (if is-short? "YouTube Shorts" "YouTube")]]])

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
              site     (or og:site_name hostname)]
          (when (or og:title og:description)
            [:div.rich-embed-card
             [:div.embed-content
              [:div.embed-site site]

              (if og:title
                [:a.embed-title-link {:href url :target "_blank" :rel "noopener noreferrer"} og:title]
                [:a.embed-title-link {:href url :target "_blank" :rel "noopener noreferrer"} url])

              (when og:description
                [:div.embed-description og:description])]

             (when img-url
               [:div.embed-thumbnail
                {:style {:cursor "zoom-in" :display "block"}
                 :on-click (fn [e]
                             (.preventDefault e)
                             (.stopPropagation e)
                             (re-frame/dispatch [:ui/open-modal :image-lightbox
                                                 {:url img-url
                                                  :backdrop-props {:class "lightbox-backdrop"}
                                                  :window-props   {:style {:background "transparent"
                                                                           :box-shadow "none"}}}]))}
                [:img {:src img-url
                       :style {:width "100%" :height "100%" :object-fit "cover"}}]])]))))))

(defoverride message-text [{:keys [body html]}]
  (if (seq html)
    [:span.body "BIG SOUP"]
    [:span.body "BIG SOUP"]))



(defn extract-first-url [text]
  (when text
    (when-let [match (re-find #"https?://[^\s\"'<>]+" text)]
      (str/replace match #"[.,:;!?]$" ""))))

(defoverride message-link-preview [msg-type-tag raw-body]
  (let [first-url (when (#{"Text" "Notice" "Emote"} msg-type-tag)
                    (extract-first-url raw-body))]
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


