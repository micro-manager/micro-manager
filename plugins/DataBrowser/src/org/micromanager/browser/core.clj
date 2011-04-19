; FILE:         browser/core.clj
; PROJECT:      Micro-Manager Data Browser Plugin
; ----------------------------------------------------------------------------
; AUTHOR:       Arthur Edelstein, arthuredelstein@gmail.com, April 19, 2011
; COPYRIGHT:    University of California, San Francisco, 2011
; LICENSE:      This file is distributed under the BSD license.
;               License text is included with the source distribution.
;               This file is distributed in the hope that it will be useful,
;               but WITHOUT ANY WARRANTY; without even the implied warranty
;               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
;               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
;               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
;               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

(ns org.micromanager.browser.core
  (:import [javax.swing BorderFactory JButton JComboBox JFrame JLabel
                        JList JPanel JScrollPane JSplitPane
                        JTable JTextField RowFilter SpringLayout SwingUtilities]
           [javax.swing.table AbstractTableModel DefaultTableModel
                              TableColumn TableRowSorter]
           [javax.swing.event DocumentListener TableModelListener]
           [java.io BufferedReader File FileReader PrintWriter]
           [java.util Vector]
           [java.util.prefs Preferences]
           [java.awt Color Dimension Font Insets]
           [java.awt.event KeyAdapter MouseAdapter]
           [com.swtdesigner SwingResourceManager])
  (:use [org.micromanager.browser.utils
            :only (gen-map constrain-to-parent create-button create-icon-button
                   attach-action-key remove-borders choose-directory
                   read-value-from-prefs write-value-to-prefs)]
        [clojure.contrib.json :only (read-json)]
        [org.micromanager.mm :only (load-mm gui)]))

(def browser (atom nil))

(def settings-window (atom nil))

(def headings (atom nil))

(def collections (atom nil))

(def prefs (.. Preferences userRoot
      (node "MMDataBrowser") (node "b3d184b1-c580-4f06-a1d9-b9cc00f12641")))

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

(defn get-model-column-index [table text]
  (let [table-model (.getModel table)]
    (-> (vec
      (for [i (range (.getColumnCount table-model))]
        (.getColumnName table-model i)))
      (.indexOf text))))

(defn open-selected-files [table]
  (let [path-column (get-model-column-index table "Path")]
    (doseq [i (.getSelectedRows table)]
      (.openAcquisitionData gui
        (.. table getModel
            (getValueAt (.convertRowIndexToModel table i) path-column))))))

(defn listen-to-open [table]
  (.addMouseListener table
    (proxy [MouseAdapter] []
      (mouseClicked [e]
        (when (= 2 (.getClickCount e)) ; double click
          (open-selected-files table))))))

(defn remove-locations [] 
  (let [location-table (get-in @settings-window [:locations :table])
        selected-rows (.getSelectedRows location-table)
        location-model (.getModel location-table)
        browser-table (:table @browser)
        browser-model (.getModel browser-table)
        location-column (get-model-column-index browser-table "Location")]
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

(defn get-display-index [table index]
  (let [column-model (.getColumnModel table)]
    (first
      (for [i (range (.getColumnCount column-model))
        :when (= index (.getModelIndex (.getColumn column-model i)))]
          i))))

(defn update-column [tags-model col]
  (when (:table @browser)
    (let [on (.getValueAt tags-model col 0)
          tag (.getValueAt tags-model col 1)
          table (:table @browser)
          column-model (.getColumnModel table)
          model-index (get-model-column-index table tag)
          display-index (get-display-index table model-index)]
      (when (<= 0 model-index)
        (when (and (not on) display-index)
          (.removeColumn column-model (.getColumn column-model display-index)))
        (when (and on (not display-index))
          (println model-index)
          (.addColumn column-model
            (doto (TableColumn. model-index)
              (.setHeaderValue tag))))))))

(defn update-all-columns [tags-model]
  (dorun (map #(update-column tags-model %) (range (.getRowCount tags-model)))))

(defn create-column-table []
  (let [table (proxy [JTable] []
                (isCellEditable [_ i] (get [true false] i)))
        model (proxy [DefaultTableModel] [0 2]
                (getColumnClass [i]
                  (get [Boolean String] i)))]
    (doto table
      (.setModel model)
      (.setRowSelectionAllowed false)
      (.setFocusable false)
      (.. getColumnModel (getColumn 0) (setMinWidth 20))
      (.. getColumnModel (getColumn 0) (setMaxWidth 20)))
      (.addTableModelListener model
        (reify TableModelListener
          (tableChanged [_ e]
            (dorun (map #(update-column model %)
              (range (.getFirstRow e) (inc (.getLastRow e))))))))
    (doseq [tag tags]
      (.addRow model (Vector. [false tag])))
    table))

;; collection files

(defn read-collection-map []
  (or (read-value-from-prefs prefs "collection-files")
      (let [name (System/getProperty "user.name")]
        {name (.getAbsolutePath (File. (str name ".mmdb.txt")))})))

(defn save-collection-map [collection-map]
  (write-value-to-prefs prefs "collection-files" collection-map))

(defn get-current-data-and-settings []
  (let [table (@browser :table)
        model (.getModel table)]
    {:browser-model-data (map seq (seq (. model getDataVector)))
     :browser-model-headings (map #(.getColumnName model %)
                               (range (.getColumnCount model)))
     :window-size (let [f (@browser :frame)] [(.getWidth f) (.getHeight f)])
     :display-columns
       (let [total-width (float (.getWidth table))]
         (->> table .getColumnModel .getColumns enumeration-seq
              (map #(hash-map :width (/ (.getWidth %) total-width)
                              :title (.getIdentifier %)))))
     :locations
       (->> @settings-window :locations :table .getModel .getDataVector
            seq (map seq) flatten)
     :sorted-column
       (when-let [sort-key (->> table .getRowSorter .getSortKeys seq first)]
         {:order ({SortOrder/ASCENDING 1 SortOrder/DESCENDING -1}
                   (.getSortOrder sort-key))
          :model-column (.getColumnName model (.getColumn sort-key))})
     }))

(defn apply-data-and-settings [settings]
  (let [table (@browser :table)
        model (.getModel table)
        {:keys [browser-model-data
                browser-model-headings
                window-size display-columns
                locations sorted-column]} settings]
    (.setDataVector model
      (Vector. (map #(Vector. %) (:browser-model-data settings)))
      (Vector. browser-model-headings))
    ))

(defn get-current-collection-file []
   (->> @browser :collection-menu
               .getSelectedItem (get @collections)))

(defn save-current-data-and-settings []
    (with-open [pr (PrintWriter. (get-current-collection-file))]
      (write-json (get-current-data-and-settings) pr)))

(defn load-data-and-settings [name]
  (let [f (get @collections name)]
    (apply-data-and-settings (read-json (slurp f))))
    (.setSelectedItem (@browser :collection-menu) name))

(defn update-collection-menu []
  (let [menu (@browser :collection-menu)
        names (sort (keys @collections))]
    (.removeAllItems menu)
    (dorun (map #(.addItem menu %) names))
    (.addItem menu "New...")
    (.addItem menu "Load...")))

;; windows

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
      split-pane :n 5 :w 5 :s -5 :e -5
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
        search-label (JLabel. (get-icon "zoom.png"))
        settings-button (create-button "Settings..."
                          #(.show (:frame @settings-window)))
        collection-label (JLabel. "Collection:")
        collection-menu (JComboBox.)]
    (doto panel (.add scroll-pane) (.add search-field)
                (.add settings-button) (.add search-label)
                (.add collection-label) (.add collection-menu))
    (doto table
      (.setAutoCreateRowSorter true)
      (.setShowGrid false)
      (.setGridColor Color/LIGHT_GRAY)
      (.setShowVerticalLines true))
    (attach-action-key table "ENTER" #(open-selected-files table))
    (.setFont search-field (.getFont table))
    (.setLayout panel (SpringLayout.))
    (constrain-to-parent scroll-pane :n 32 :w 5 :s -5 :e -5
                         search-field :n 5 :w 25 :n 28 :w 200
                         settings-button :n 5 :w 405 :n 28 :w 510
                         search-label :n 5 :w 5 :n 28 :w 25
                         collection-label :n 5 :w 205 :n 28 :w 275
                         collection-menu :n 5 :w 275 :n 28 :w 405)
    (connect-search search-field table)
    (.setSortsOnUpdates (.getRowSorter table) true)
    (listen-to-open table)
    (attach-action-key search-field "ESCAPE" #(.setText search-field ""))
    (doto frame
      (.. getContentPane (add panel))
      (.setBounds 50 50 540 500))
    (gen-map frame table scroll-pane settings-button search-field
             collection-menu)))

(defn start-browser []
  (load-mm)
  (reset! collections (read-collection-map))
  (reset! settings-window (create-settings-window))
  (reset! browser (create-browser))
  (reset! headings tags)
  (.setModel (:table @browser) (create-table-model tags))
  (update-all-columns (.getModel (get-in @settings-window [:columns :table])))
  (update-collection-menu)
  (add-location "/Users/arthur/qqqq")
  (.show (@browser :frame))
  browser)


