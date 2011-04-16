(ns org.micromanager.browser.core
  (:import [javax.swing JButton JFrame JLabel JPanel JScrollPane 
                        JTable JTextField RowFilter SpringLayout SwingUtilities]
           [javax.swing.table AbstractTableModel TableRowSorter]
           [javax.swing.event DocumentListener]
           [java.io BufferedReader File FileReader]
           [java.util Vector]
           [java.awt Color Dimension Font Insets]
           [java.awt.event KeyAdapter MouseAdapter]
           [com.swtdesigner SwingResourceManager])
  (:use [org.micromanager.browser.utils
            :only (constrain-to-parent attach-action-key)]
        [clojure.contrib.json :only (read-json)]
        [org.micromanager.mm :only (load-mm gui)]))

(defn get-search-icon []
  (SwingResourceManager/getIcon
    org.micromanager.MMStudioMainFrame "icons/zoom.png"))

(defn set-model [table m]
  (.setModel table m)
  (.setRowSorter table (TableRowSorter. m)))

(defn set-filter [table text]
  (let [sorter (.getRowSorter table)]
      (do (.setRowFilter sorter (RowFilter/regexFilter text (int-array 0))))))

(defn connect-search [search-field table]
  (let [d (.getDocument search-field)
        f (fn [] (SwingUtilities/invokeLater
                   #(do (set-filter table (.getText d 0 (.getLength d)))
                        (.setBackground search-field
                          (if (zero? (.getRowCount table))
                            Color/PINK Color/WHITE)))))]
    (.addDocumentListener d
      (reify DocumentListener
        (insertUpdate [_ _] (f))
        (changedUpdate [_ _] (f))
        (removeUpdate [_ _] (f))))))

(defn listen-to-open [table]
  (.addMouseListener table
    (proxy [MouseAdapter] []
      (mouseClicked [e]
        (if (= 2 (.getClickCount e))
          (. gui openAcquisitionData (.getValueAt table (.getSelectedRow table) -1)))))))

(defn create-browser [model]
  (let [f (JFrame. "Micro-Manager Data Set Browser")
        p (JPanel.)
        t (proxy [JTable] [] (isCellEditable [_ _] false))
        sc (JScrollPane. t)
        tf (JTextField.)
        search-label (JLabel.)
        index-button (JButton. "Add folder...")
        columns-button (JButton. "Columns...")]
    (set-model t model)
    (doto p (.add sc) (.add tf) (.add index-button)
            (.add columns-button) (.add search-label))
    (doto t (.setAutoCreateRowSorter true)
            (.setShowGrid false)
            (.setGridColor Color/LIGHT_GRAY)
            (.setShowVerticalLines true))
    (doto search-label (.setIcon (get-search-icon)))
    (.setFont tf (.getFont t))
    (.setLayout p (SpringLayout.))
    (constrain-to-parent sc :n 32 :w 5 :s -5 :e -5)
    (constrain-to-parent tf :n 5 :w 25 :n 28 :w 200)
    (constrain-to-parent index-button :n 5 :w 205 :n 28 :w 330)
    (constrain-to-parent columns-button :n 5 :w 330 :n 28 :w 450)
    (constrain-to-parent search-label :n 5 :w 5 :n 28 :w 25)
    (.setEnabled columns-button false)
    (.setEnabled index-button false)
    (connect-search tf t)
    (listen-to-open t)
    (attach-action-key tf "ESCAPE" #(.setText tf ""))
    (doto f (.. getContentPane (add p))
       (.setBounds 50 50 500 500)
       (.setVisible true))
    {:frame f :table t :scroll-pane sc :index-button index-button
     :columns-button columns-button :search-field tf})) 
    
(defn find-data-sets [root-dir]
  (map #(.getParent %)
    (->> (File. root-dir)
      file-seq
      (filter #(= (.getName %) "metadata.txt")))))

(defn get-frame-index [file-name]
  (try (Integer/parseInt (second (.split file-name "_")))
     (catch Exception e nil)))

(defn count-frames [data-set]
  (inc
    (apply max -1
      (filter identity
        (map #(get-frame-index (.getName %))
             (.listFiles (File. data-set)))))))

(defn get-display-and-comments [data-set]
  (let [f (File. data-set "display_and_comments.txt")]
    (if (.exists f) (read-json (slurp f) false) nil)))

(defn read-summary-map [data-set]
  (-> (->> (File. data-set "metadata.txt")
           FileReader. BufferedReader. line-seq
           (take-while #(not (.startsWith % "},")))
           (apply str))
      (.concat "}}") (read-json false) (get "Summary")))

(defn get-summary-map [data-set]
  (merge (read-summary-map data-set)
    (if-let [frames (count-frames data-set)]
      {"Frames" frames})
    (if-let [d+c (get-display-and-comments data-set)]
      {"Comment" (get-in d+c ["Comments" "Summary"])
       "FrameComments" (dissoc (get d+c "Comments") "Summary")})
    (let [data-dir (File. data-set)]
      {"CurrentPath" (.getAbsolutePath data-dir)
       "CurrentName" (.getName data-dir)})))

(defn get-summary-maps [root-dir]
  (map get-summary-map (find-data-sets root-dir)))
  
(defn create-table-model [summary-maps headings]
  (proxy [AbstractTableModel] []
    (getRowCount [] (count summary-maps))
    (getColumnCount [] (count headings))
    (getValueAt [row col]
      (get (nth summary-maps row)
        (if (neg? col) "CurrentPath" (nth headings col))))
    (getColumnName [col] (get headings col))))

(def default-headings ["CurrentPath" "Time" "Frames" "Comment"])

(defn start-browser []
  (load-mm)
  (-> (get-summary-maps "/Users/arthur/qqq")
    (create-table-model default-headings)
    create-browser))

;(defn update-browser [dir headings]
;  (.setModel (:table browser)
;             (create-table-model
;               (swap! sm concat (get-summary-maps dir)) headings)))
;



