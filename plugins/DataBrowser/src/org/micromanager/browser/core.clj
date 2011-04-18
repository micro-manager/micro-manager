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
            :only (gen-map constrain-to-parent create-button create-icon-button
                   attach-action-key remove-borders choose-directory)]
        [clojure.contrib.json :only (read-json)]
        [org.micromanager.mm :only (load-mm gui)]))

(def browser (atom nil))

(def settings-window (atom nil))

(def headings (atom nil))

(def tags [
  "ChColors" "ChContrastMax" "ChContrastMin" "ChNames" "Channels" "Comment"
  "ComputerName" "Date" "Depth" "Directory" "FrameComments" "Frames" "GridColumn"
  "GridRow" "Height" "IJType" "Interval_ms" "KeepShutterOpenChannels"
  "KeepShutterOpenSlices" "Location" "MetadataVersion" "MicroManagerVersion"
  "Name" "Path" "PixelAspect" "PixelSize_um" "PixelType" "PositionIndex"
  "Positions" "Prefix" "Slices" "SlicesFirst" "Source" "Time" "TimeFirst"
  "UUID" "UserName" "Width" "z-step_um"
   ])

(defn get-icon [name]
  (SwingResourceManager/getIcon
    org.micromanager.MMStudioMainFrame (str "icons/" name)))

(defn set-model [table m]
  (.setModel table m)
  (.setRowSorter table (TableRowSorter. m)))

(defn set-filter [table text]
  (let [sorter (.getRowSorter table)
        column-indices
          (int-array (map #(.getModelIndex %)
               (enumeration-seq (.. table getColumnModel getColumns))))]
      (do (.setRowFilter sorter (RowFilter/regexFilter text column-indices)))))

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

(defn get-column-index [table text]
  (let [table-model (.getModel table)]
    (-> (vec
      (for [i (range (.getColumnCount table-model))]
        (.getColumnName table-model i)))
      (.indexOf text))))

(defn listen-to-open [table]
  (.addMouseListener table
    (proxy [MouseAdapter] []
      (mouseClicked [e]
        (when (= 2 (.getClickCount e)) ; double click
          (open-selected-files table))))))

(defn open-selected-files [table]
  (let [path-column (get-column-index table "Path")]
    (doseq [i (.getSelectedRows table)]
      (.openAcquisitionData gui
        (.getValueAt table i path-column)))))

(defn remove-locations [] 
  (let [location-table (get-in @settings-window [:locations :table])
        selected-rows (.getSelectedRows location-table)
        location-model (.getModel location-table)
        browser-table (:table @browser)
        browser-model (.getModel browser-table)
        location-column (get-column-index browser-table "Location")]
    (println browser-model "," location-model "," location-column)
    (doseq [location-row (reverse selected-rows)]
      (when-let [loc (.getValueAt location-model location-row 0)]
        (println location-row)
        (doseq [browser-row (reverse (range (.getRowCount browser-model)))]
          (when (= (.getValueAt browser-model browser-row location-column) loc)
            (.removeRow browser-model browser-row))))
      (.removeRow location-model location-row))))
    
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

(defn get-summary-map [data-set location]
  (merge (read-summary-map data-set)
    (if-let [frames (count-frames data-set)]
      {"Frames" frames})
    (if-let [d+c (get-display-and-comments data-set)]
      {"Comment" (get-in d+c ["Comments" "Summary"])
       "FrameComments" (dissoc (get d+c "Comments") "Summary")})
    (let [data-dir (File. data-set)]
      {"Path"     (.getAbsolutePath data-dir)
       "Name"     (.getName data-dir)
       "Location" location})))

(defn get-summary-maps [root-dir]
  (map #(get-summary-map % root-dir) (find-data-sets root-dir)))

(defn create-table-model [headings]
  (DefaultTableModel. (Vector. headings) 0))

(def default-headings ["Path" "Time" "Frames" "Comment" "Location"])
  
(defn add-summary-maps [browser map-list headings]
  (.start (Thread. (fn [] (doseq [m map-list]
                      (SwingUtilities/invokeLater
                        (fn []
                          (.addRow (.getModel (:table browser))
                            (Vector. (map #(get m %) headings))))))))))     

;; create a table model with all metadata.
;; Then use .addColumn, .removeColumn to show and hide columns, as controlled by
;; the settings window. The DefaultTableModel can be used to keep track of the 
;; "CurrentPath" for a given row even if that column is hidden.

(defn add-location [location]
  (.. (get-in @settings-window [:locations :table])
      getModel (addRow (Vector. (list location))))
  (let [maps (get-summary-maps location)]
    (add-summary-maps @browser maps @headings)))

(defn user-add-location []
  (when-let [loc (choose-directory nil
                     "Please add a location to scan for files")]
    (add-location (.getAbsolutePath loc))))

(defn create-column-table []
  (let [table (proxy [JTable] [0 2]
                (isCellEditable [_ i] (get [true false] i)))
        model (proxy [DefaultTableModel] [0 2]
                (getColumnClass [i]
                  (get [Boolean String] i)))]
    (doto table
      (.setModel model)
      (.setRowSelectionAllowed false)
      (.setFocusable false)
      (.. getColumnModel (getColumn 0) (setMinWidth 20))
      (.. getColumnModel (getColumn 0) (setMaxWidth 20))
    ; (.. getColumnModel (getColumn 1) (setMaximumWidth 20))
    ; (.setAutoResizeMode JTable/AUTO_RESIZE_LAST_COLUMN))
    )
    (doseq [tag tags]
      (.addRow model (Vector. [false tag])))
    table))

(defn create-settings-window []
  (let [label-table
          (fn [table label-text parent]
            (let [label (JLabel. label-text)
                  panel (JPanel.)
                  scroll-pane (JScrollPane. table)]
              (.setBorder table (BorderFactory/createLineBorder (Color/GRAY)))
              (doto panel (.add label) (.add scroll-pane)
                          (.setLayout (SpringLayout.)))
              (constrain-to-parent label :n 0 :w 0 :n 20 :e 0
                                   scroll-pane :n 20 :w 0 :s 0 :e 0)
              (.add parent panel)
              (remove-borders table)
              (.setTableHeader table nil)
              (gen-map table panel)))
        split-pane (JSplitPane. JSplitPane/HORIZONTAL_SPLIT true)
        locations (label-table (proxy [JTable] [0 1] (isCellEditable [_ _] false))
                               "Locations" split-pane)
        add-location-button
          (create-icon-button (get-icon "plus.png") user-add-location)
        remove-location-button
          (create-icon-button (get-icon "minus.png") remove-locations)
        columns (label-table (create-column-table) "Columns" split-pane)
        main-panel (JPanel.)
        frame (JFrame. "Micro-Manager Data Set Browser Settings")]
    (apply remove-borders (.getComponents split-pane))
    (.. frame getContentPane (add main-panel))
    (doto split-pane
      (.setResizeWeight 0.5)
      (.setDividerLocation 0.5))
    (remove-borders split-pane)
    (.add main-panel split-pane)
    (doto (:panel locations)
      (.add add-location-button) (.add remove-location-button))
    (.setLayout main-panel (SpringLayout.))
    (constrain-to-parent
      split-pane :n 32 :w 5 :s -5 :e -5
      add-location-button :n 0 :e -38 :n 18 :e -20
      remove-location-button :n 0 :e -18 :n 18 :e 0)
    (.setBounds frame 50 50 600 600)
    (gen-map frame locations columns)))

(defn create-browser []
  (let [frame (JFrame. "Micro-Manager Data Set Browser")
        panel (JPanel.)
        table (proxy [JTable] [] (isCellEditable [_ _] false))
        scroll-pane (JScrollPane. table)
        search-field (JTextField.)
        search-label (JLabel.)
        settings-button (create-button "Settings..."
                          #(.show (:frame @settings-window)))]
    (doto panel (.add scroll-pane) (.add search-field)
                (.add settings-button) (.add search-label))
    (doto table
      (.setAutoCreateRowSorter true)
      (.setShowGrid false)
      (.setGridColor Color/LIGHT_GRAY)
      (.setShowVerticalLines true))
    (attach-action-key table "ENTER" #(open-selected-files table))
    (doto search-label (.setIcon (get-icon "zoom.png")))
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

(defn start-browser []
  (load-mm)
  (reset! settings-window (create-settings-window))
  (reset! browser (create-browser))
  (reset! headings default-headings)
  (.setModel (:table @browser) (create-table-model default-headings))
  (add-location "/Users/arthur/qqqq")
  browser)


