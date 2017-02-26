(ns vrbo-key-metrics.core
  (:gen-class
   :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler])
  (:require [vrbo-key-metrics.email :refer [email-listings]]
            [vrbo-key-metrics.extract :refer [extract-s3-obj]]
            [vrbo-key-metrics.transform :refer [transform-listings]]
            [vrbo-key-metrics.upload :refer [upload-listings]]))


(defn execute []
  (let [xlsx-listings (extract-s3-obj)
        vrbo-listings (transform-listings xlsx-listings)]
    (dorun (upload-listings vrbo-listings))
    (dorun (email-listings vrbo-listings))))


(defn -handleRequest
  [this input output context]
  (time (execute)))
