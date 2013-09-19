(ns webrepl
  (:require [cljs.core]
            [cljs.analyzer :as ana]
            [cljs.compiler :as comp]
            [cljs.reader :as reader]))

(def ^:dynamic *debug* false)
(def ^:dynamic *e nil)

(def history (atom []))
(def history-position (atom 0))

(defn prompt [] (str ana/*cljs-ns* "=> "))

(def append-dom)

(defn dom [o]
  (if (coll? o)
    (let [[tag attrs & body] o]
      (if (keyword? tag)
        (let [elem (.createElement js/document (name tag))]
          (when (map? attrs)
            (doseq [[k v] attrs]
              (when v (.setAttribute elem (name k) v))))
          [(append-dom elem (if (map? attrs) body (cons attrs body)))])
        (mapcat dom o)))
    (when o
      [(.createTextNode js/document (str o))])))

(defn append-dom [parent v]
  (doseq [i (dom v)]
    (.appendChild parent i))
  parent)

(defn repl-print [log text cls]
  (doseq [line (.split (str text) #"\n")]
    (append-dom log
      [:div {:class (str "cg "
                         (when cls
                           (str " " cls)))}
       line]))
  (set! (.-scrollTop log) (.-scrollHeight log)))

(defn- read-next-form [text]
  (binding [*ns-sym* ana/*cljs-ns*]
    (reader/read-string text)))

(defn postexpr [log text]
  (swap! history conj text)
  (reset! history-position (count @history))
  (append-dom log
    [:table
     [:tbody
      [:tr
       [:td {:class "cg"} (prompt)]
       [:td (.replace text #"\n$" "")]]]]))

(defn ep [log text]
  (try
    (let [env (assoc (ana/empty-env) :context :expr)
          form (read-next-form text)
          _ (when *debug* (println "READ:" (pr-str form)))
          body (ana/analyze env form)
          _ (when *debug* (println "ANALYZED:" (pr-str (:form body))))
          res (comp/emit-str body)
          _ (when *debug* (println "EMITTED:" (pr-str res)))
          value (js/eval res)]
      (set! *3 *2)
      (set! *2 *1)
      (set! *1 value)
      (repl-print log (pr-str value) "rtn"))
    (catch js/Error e
      (repl-print log (.-stack e) "err")
      (set! *e e))))

(defn pep [log text]
 (postexpr log text)
 (ep log text))

(defn handle-enter-key [log status1 status2 input]
  (try
    (let [form (reader/read-string (.-value input))]
      (do
        (pep log (.-value input))
        (js/setTimeout #(set! (.-value input) "") 0)
        (set! (.-visibility (.-style status1)) "visible")
        (set! (.-visibility (.-style status2)) "hidden")
        (set! (.-innerText (.getElementById js/document "ns")) (prompt))))
    (catch js/Error e
      (if (re-find #"EOF while reading" (.-message e))
        (do
          (set! (.-visibility (.-style status1)) "hidden")
          (set! (.-visibility (.-style status2)) "visible"))
        (repl-print log e "err")))))
     
(defn count-lines [text]
  (count (.split text "\n")))
     
(defn cursor-line-number [input]
  (let [pos (.-selectionEnd input)
        before (.substr (.-value input) 0 pos)]
    (dec (count-lines before))))

(defn move-cursor-to-end [input]
  (let [n (count (.-value input))]
    (set! (.-selectionStart input) n)
    (set! (.-selectionEnd input) n)))

(defn bump-history [ev input change-fn]
  (alert "bump!")
  (let [cur-hist (get @history @history-position)]
    (when (not= cur-hist (.-value input))
      (reset! history-position (count @history-position))))
  (swap! history-position change-fn)
  (set! (.-value input) (get @history @history-position))
  (.preventDefault ev)
  (move-cursor-to-end input))

(defn handle-up-key [ev input]
  (when (zero? (cursor-line-number input))
    (bump-history ev input #(max 0 (dec %)))))

(defn handle-down-key [ev input]
  (when (= (inc (cursor-line-number input))
           (count-lines (.-value input)))
    (bump-history ev input #(min (inc %) (count @history)))))

(set! (.-onload js/window) (fn []
  (let [log (.getElementById js/document "log")
        input (.getElementById js/document "input")
        status1 (.getElementById js/document "status1")
        status2 (.getElementById js/document "status2")]
    (set! *print-fn* #(repl-print log % nil))

    (println ";; ClojureScript")
    (append-dom log [:div {:class "cg"}
      ";;   - "
      [:a {:href "http://github.com/kanaka/clojurescript"}
       "http://github.com/kanaka/clojurescript"]])
    (println ";;   - A port of the ClojureScript compiler to ClojureScript")
    (pep log "(+ 1 2)")
    (pep log "(defn sqr [x] (* x x))")
    (pep log "(sqr 8)")
    (pep log "(defmacro unless [pred a b] `(if (not ~pred) ~a ~b))")
    (pep log "(unless false :yep :nope)")

    (set! (.-onkeypress input)
          (fn [ev]
            (condp == (.-keyCode (or ev event))
              13 (handle-enter-key log status1 status2 input)
              38 (handle-up-key ev input)
              40 (handle-down-key ev input)
              nil
              )))

    (.focus input))))
