(ns org.micromanager.browser.core
  (:import [javax.swing BorderFactory JButton JFrame JLabel
                        JList JPanel JScrollPane JSplitPane
                        JTable JTextField RowFilter SpringLayout SwingUtilities]
           [javax.swing.table AbstractTableModel DefaultTableModel TableRowSorter]
           [javax.swing.event DocumentListener]
           [java.io BufferedReader File FileReader]
           [java.util Vector]
           [java.awt Color Dimension Font Insets]
           [java.awt.event KeyAdapter MouseAdapter]
           [com.swtdesigner SwingResourceManager])
  (:use [org.micromanager.browser.utils
            :only (gen-map constrain-to-parent create-button
                   attach-action-key remove-borders)]
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

(defn create-settings-window []
  (let [labeled-list
          (fn [label-text parent]
            (let [label (JLabel. label-text)
                  list (JList.)
                  panel (JPanel.)
                  scroll-pane (JScrollPane. panel)]
              (.setBorder list (BorderFactory/createLineBorder (Color/GRAY)))
              (doto panel (.add label) (.add list)
                          (.setLayout (SpringLayout.)))
              (constrain-to-parent label :n 0 :w 0 :n 20 :e 0
                                   list :n 20 :w 0 :s 0 :e 0)
              (.add parent scroll-pane)
              list))
        split-pane (JSplitPane. JSplitPane/HORIZONTAL_SPLIT true)
        locations-list (labeled-list "Locations" split-pane)
        columns-list (labeled-list "Columns" split-pane)
        main-panel (JPanel.)
        frame (JFrame. "Micro-Manager Data Set Browser Settings")]
    (apply remove-borders (.getComponents split-pane))
    (.. frame getContentPane (add main-panel))
    (doto split-pane
      (.setResizeWeight 0.5)
      (.setDividerLocation 0.5))
    (remove-borders split-pane)
    (.add main-panel split-pane)
    (.setLayout main-panel (SpringLayout.))
    (constrain-to-parent
      split-pane :n 32 :w 5 :s -5 :e -5)
    (.setBounds frame 50 50 600 600)
    (gen-map frame locations-list columns-list)))

(defn create-browser [settings-window]
  (let [frame (JFrame. "Micro-Manager Data Set Browser")
        panel (JPanel.)
        table (proxy [JTable] [] (isCellEditable [_ _] false))
        scroll-pane (JScrollPane. table)
        search-field (JTextField.)
        search-label (JLabel.)
        settings-button (create-button "Settings..."
                          #(.show (:frame settings-window)))]
    (doto panel (.add scroll-pane) (.add search-field)
                (.add settings-button) (.add search-label))
    (doto table (.setAutoCreateRowSorter true)
                (.setShowGrid false)
                (.setGridColor Color/LIGHT_GRAY)
                (.setShowVerticalLines true))
    (doto search-label (.setIcon (get-search-icon)))
    (.setFont search-field (.getFont table))
    (.setLayout panel (SpringLayout.))
    (constrain-to-parent scroll-pane :n 32 :w 5 :s -5 :e -5
                         search-field :n 5 :w 25 :n 28 :w 200
                         settings-button :n 5 :w 205 :n 28 :w 330
                         search-label :n 5 :w 5 :n 28 :w 25)
    (connect-search search-field table)
    (.setSortsOnUpdates (.getRowSorter table) true)
    (listen-to-open table)
    (attach-action-key search-field "ESCAPE" #(.setText search-field ""))
    (doto frame (.. getContentPane (add panel))
       (.setBounds 50 50 500 500)
       (.setVisible true))
    (gen-map frame table scroll-pane settings-button search-field)))
    
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

(defn create-table-model [headings]
  (DefaultTableModel. (Vector. headings) 0))

(def default-headings ["CurrentPath" "Time" "Frames" "Comment"])
  
(defn add-summary-maps [browser map-list headings]
  (.start (Thread. (fn [] (doseq [m map-list]
                      (SwingUtilities/invokeLater (fn []
                      (.addRow (.getModel (:table browser))
                        (Vector. (map #(get m %) headings))))))))))     

;; create a table model with all metadata.
;; Then use .addColumn, .removeColumn to show and hide columns, as controlled by
;; the settings window. The DefaultTableModel can be used to keep track of the 
;; "CurrentPath" for a given row even if that column is hidden.

(defn start-browser []
  (load-mm)
  (let [settings-window (create-settings-window)
        browser (create-browser settings-window)
        maps (get-summary-maps "/Users/arthur/qqq")]
    (.setModel (:table browser) (create-table-model default-headings))
    (add-summary-maps browser maps default-headings)
    browser))



