(ns vrbo-key-metrics.email
  (:require [amazonica.aws.simpleemail :as ses]
            [clj-time.local :as l]
            [clj-time.format :as f]
            [hiccup.table :as ht]
            [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css
                                 include-js]]
            [postal.core :refer [send-message]]
            [vrbo-key-metrics.config :refer [config]]))


(defn deliver-email [table]
  (let [table-html
        (html [:head
               (include-css (str "https://maxcdn.bootstrapcdn.com"
                                 "/bootstrap/3.3.7/css/bootstrap.min.css"))
               [:style ".red { color: red; }"]
               [:style ".green { color: green; }"]
               (include-js (str "https://ajax.googleapis.com"
                                "/ajax/libs/jquery/3.1.1/jquery.min.js"))
               (include-js (str "https://maxcdn.bootstrapcdn.com"
                                "/bootstrap/3.3.7/js/bootstrap.min.js"))
               [:body table]])
        file-name (format "/tmp/occupancy_report_%s.html"
                          (f/unparse (f/formatter "yyyy-MM-dd") (l/local-now)))]
    (spit file-name table-html)
    (send-message {:user (:smtp-user config)
                   :pass (:smtp-pass config)
                   :host (:smtp-host config)
                   :port 587}
                  {:from (:email config)
                   :to (:email config)
                   :subject "VRBO Occupancy Report"
                   :body [{:type "text/html"
                           :content table-html}
                          {:type :attachment
                           :content (java.io.File. file-name)}]})))


(defn generate-table [listings]
  (let [attr-fns {:table-attrs {:class "table table-striped table-bordered"}
                  :thead-attrs {:id "thead"}
                  :tbody-attrs {:id "tbody"}
                  :data-tr-attrs {:class "trattrs"}
                  :th-attrs (fn [label-key _] nil)
                  :data-td-attrs
                  (fn [label-key val] nil)
                  :data-value-transform
                  (fn [label-key val]
                    (case label-key
                      :scrape-date val
                      :from-date val
                      :to-date val
                      (case (:color val)
                        "red" [:class {:class "red"} (:value val)]
                        "green" [:class {:class "green"} (:value val)]
                        (:value val))))}]
    (ht/to-table1d
     listings
     [:scrape-date "Scrape Date" :from-date "From Date" :to-date "To Date"
      :all-destin-west-perc "All Destin West" :my-destin-west-perc "My Destin West"
      :all-waterscape-perc "All Waterscape" :my-waterscape-perc "My Waterscape"
      :all-waters-edge-perc "All Waters Edge" :my-waters-edge-perc "My Waters Edge"
      :all-azure-perc "All Azure" :my-azure-perc "My Azure"]
     attr-fns)))


(defn email-listings [listings]
  (->> listings
       generate-table
       deliver-email))
