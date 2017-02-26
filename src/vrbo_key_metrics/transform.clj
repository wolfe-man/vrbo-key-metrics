(ns vrbo-key-metrics.transform
  (:require [clj-time.local :as l]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [clojure.core.memoize :as memo]
            [clojure.data.json :as json]))


(defn date-yyyy-MM-dd [date-time]
  (f/unparse (f/formatter "yyyy-MM-dd") date-time))


(defmacro retry
  "Evaluates expr up to cnt + 1 times, retrying if an exception
  is thrown. If an exception is thrown on the final attempt, it
  is allowed to bubble up."
  [cnt expr]
  (letfn [(go [cnt]
              (if (zero? cnt)
                expr
                `(try ~expr
                      (catch Exception e#
                        (retry ~(dec cnt) ~expr)))))]
    (go cnt)))


(defn execute-get-json [url]
  (retry 3
         (do (Thread/sleep 1000)
             (with-open [inputstream
                         (-> url
                             java.net.URL.
                             .openConnection
                             (doto (.setRequestProperty "User-Agent"
                                                        "Mozilla/5.0 ..."))
                             .getContent)]
               (let [result (->  inputstream
                                 slurp
                                 ((partial re-find #"(?<=VRBO.indexMaplisings = )(.*)(?=;)")))]
                 (-> result
                     first
                     (json/read-str :key-fn keyword)))))))


(defn get-cnt
  ([url]
   (:hitCount (execute-get-json url)))
  ([url from-date-yyyy-MM-dd to-date-yyyy-MM-dd]
   (let [query-string (str "?from-date=" from-date-yyyy-MM-dd
                           "&to-date=" to-date-yyyy-MM-dd)]
     (-> url
         (str query-string)
         execute-get-json
         :hitCount))))


(defn increment-date [increment]
  (case increment
    "day" (t/days 1)
    "week" (t/weeks 1)
    "month" (t/months 1)))


(defn end-date-cond [from increment]
  (if (nil? increment)
    (date-yyyy-MM-dd from)
    (-> from
        (t/plus (increment-date increment))
        date-yyyy-MM-dd)))


(defn calc-perc [numerator denominator]
  (if (pos? denominator)
    (* 100.00 (/ numerator denominator))
    0.00))


(defn calc-frac-perc-string [numerator denominator perc]
  (str numerator " of " denominator "\n" (format "%.2f" perc) "%"))


(defn get-color [global-perc david-perc]
  (cond
    (> global-perc david-perc) "red"
    (< global-perc david-perc) "green"
    :else "black"))


(defn gen-key
  ([prefix resort]
   (let [key-name (str prefix " " resort)]
     (-> key-name
         (clojure.string/replace #" " "-")
         keyword)))
  ([prefix resort sufix]
   (let [key-name (str prefix " " resort " " sufix)]
     (-> key-name
         (clojure.string/replace #" " "-")
         keyword))))


(defn get-listings [{:keys [to-date from-date global-url david-url
                            resort increment listing-ids]}]
  (let [global-total-cnt (get-cnt global-url)
        david-total-cnt (get-cnt david-url)]
    (loop [from (c/to-date-time from-date)
           to (c/to-date-time to-date)
           coll []]
      (if (t/after? from to)
        coll
        (let [from-date-yyyy-MM-dd (date-yyyy-MM-dd from)
              to-date-yyyy-MM-dd (end-date-cond from increment)
              global-date-cnt (- global-total-cnt
                                 (get-cnt global-url
                                          from-date-yyyy-MM-dd
                                          to-date-yyyy-MM-dd))
              david-date-cnt (- david-total-cnt
                                (get-cnt david-url
                                         from-date-yyyy-MM-dd
                                         to-date-yyyy-MM-dd))
              global-perc (calc-perc global-date-cnt global-total-cnt)
              david-perc (calc-perc david-date-cnt david-total-cnt)
              color (get-color global-perc david-perc)
              new-map {:scrape-date (date-yyyy-MM-dd (l/local-now))
                       :from-date from-date-yyyy-MM-dd
                       :to-date to-date-yyyy-MM-dd
                       (gen-key "all" resort) global-total-cnt
                       (gen-key "all" resort "date") global-date-cnt
                       (gen-key "all" resort "perc")
                       {:value (calc-frac-perc-string global-date-cnt
                                                      global-total-cnt
                                                      global-perc)
                        :color color}
                       (gen-key "my" resort) david-total-cnt
                       (gen-key "my" resort "date") david-date-cnt
                       (gen-key "my" resort "perc")
                       {:value (calc-frac-perc-string david-date-cnt
                                                      david-total-cnt
                                                      david-perc)
                        :color color}}]
          (->> new-map
               (conj coll)
               (recur (t/plus from (increment-date increment))
                      to)))))))


(defn transform-listings [rows]
  (->> rows
       (pmap get-listings)
       flatten
       (group-by #(select-keys % [:to-date :from-date]))
       vals
       (map (partial apply merge))
       (sort-by (juxt :from-date :to-date))))
