(ns vrbo-key-metrics.upload
  (:require [amazonica.aws.dynamodbv2 :as db]
            [vrbo-key-metrics.config :refer [dynamo-config
                                             with-aws-credentials]]))


(defn dynamo-upload [listings-group]
  (with-aws-credentials dynamo-config
    (db/batch-write-item :return-consumed-capacity "TOTAL"
                         :return-item-collection-metrics "SIZE"
                         :request-items
                         {"vrbo-key-metrics" listings-group})))


(defn transform-dynamo-upload [listings]
  (dynamo-upload (reduce (fn [coll m]
                           (conj coll {:put-request {:item m}}))
                         [] listings)))


(defn upload-listings [listings]
  (->> listings
       (map #(dissoc %
                     :all-azure-perc :my-azure-perc
                     :all-destin-west-perc :my-destin-west-perc
                     :all-waters-edge-perc :my-waters-edge-perc
                     :all-waterscape-perc :my-waterscape-perc))
       (partition-all 20)
       (map transform-dynamo-upload)))
