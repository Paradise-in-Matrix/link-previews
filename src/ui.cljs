(defoverride profile-preview-content [{:keys [member]}]
  [:div {:style {:background "#000" :padding "16px" :border "1px solid #333"}}
   [:h3 {:style {:color "#0f0" :margin 0 :font-family "monospace"}}
    (str "> " (:display-name member))]
   [:p {:style {:color "#555" :font-size "12px" :font-family "monospace"}}
    (:user-id member)]])
