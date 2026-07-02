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
            [cljs-workers.mesh :as mesh]
            [paradise.ui.container.timeline.item :as item]))

(def platform (.getPlatform Capacitor))
(def is-ios? (= platform "ios"))

(re-frame/reg-event-fx
 :media/fetch-plugin-preview
 (fn [{:keys [db]} [_ url]]
   (when-not (get-in db [:url-previews url])
     (go
       (let [res (<! (mesh/do-with-thread! :engine-pool
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


