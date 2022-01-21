(ns system-datahike.utils
  (:require [anomalies.core :as anom]
            [clojure.string :as s])
  (:import java.util.Base64))



(defn ^:private base64encode [^String s-in]
  (.encodeToString (Base64/getEncoder) (.getBytes s-in)))


(defn ^:private base64decode [^String s-in]
  (String. (.decode (Base64/getDecoder) s-in)))



(defn ^:private decode-cursor
  ([^String s-in]
   (decode-cursor s-in (constantly nil)))
  ([^String s-in default-fn]
   (if s-in
     (let [[tx order-by pointer] (-> s-in base64decode (s/split #"\|"))]
       {:tx (Long/parseLong tx)
        :pointer (Integer/parseInt pointer)
        :order-by (keyword order-by)})
     (default-fn))))


(defn ^:private encode-cursor [{:keys [tx order-by pointer]}]
  (-> (str tx "|" (name order-by) "|" pointer)
      base64encode))



(defn ^:private slice-phases [{:keys [first-x after
                                      last-x before]}]
  (let [{pointer-after  :pointer} (decode-cursor after)
        {pointer-before :pointer} (decode-cursor before)
        phase1 {:drop (or pointer-after 0)
                :take (if pointer-before
                        (dec (- pointer-before (or pointer-after 0)))
                        50)}
        phase2 {:take (or first-x 50)
                :drop (if last-x
                        (if (> last-x (:take phase1))
                          0
                          (- (:take phase1) last-x))
                        0)}]
    {:phase1 phase1
     :phase2 phase2}))



(defn coerce-opts
  ([opts]
   (coerce-opts opts nil))
  ([{:keys [after before order-by] :as opts}
    {:keys [default-order-by]}]
   (let [order-by' (or order-by default-order-by)
         {tx-after  :tx order-by-after  :order-by} (decode-cursor after)
         {tx-before :tx order-by-before :order-by} (decode-cursor before)]

     (when (or (and tx-after tx-before
                    (not= tx-after tx-before))
               (and order-by-after order-by-before
                    (not= order-by' order-by-after order-by-before)))
       (anom/throw-anom "Invalid `after` and/or `before` cursor."))

     (when (and order-by-after
                (not= order-by' order-by-after))
       (anom/throw-anom "Invalid `after` cursor."))

     (when (and order-by-before
                (not= order-by' order-by-before))
       (anom/throw-anom "Invalid `before` cursor."))

     (assoc opts :order-by order-by'))))


(defn slice [opts coll]
  (let [{:keys [phase1 phase2]} (slice-phases opts)]
    (->> coll
         (drop (:drop phase1))
         (take (:take phase1))
         (drop (:drop phase2))
         (take (:take phase2)))))


(defn map-cursor [{:keys [order-by] :as opts} tx coll]
  (let [{:keys [phase1 phase2]} (slice-phases opts)]
    (->> coll
         (map-indexed (fn [idx item]
                        (assoc item :cursor
                               (encode-cursor {:tx tx
                                               :order-by order-by
                                               :pointer (+ (:drop phase1)
                                                           (:drop phase2)
                                                           (inc idx))})))))))
