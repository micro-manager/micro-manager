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
  (:import [javax.swing BorderFactory JButton JComboBox JFrame JLabel JOptionPane
                        JList JPanel JScrollPane JSplitPane SortOrder
                        JTable JTextField RowFilter SpringLayout]
           [javax.swing.table AbstractTableModel DefaultTableModel
                              TableColumn TableRowSorter]
           [javax.swing.event DocumentListener TableModelListener]
           [java.io BufferedReader File FileReader PrintWriter]
           [java.util Vector]
           [java.util.prefs Preferences]
           [java.awt Color Dimension Font Insets]
           [java.awt.event ItemEvent ItemListener KeyAdapter MouseAdapter]
           [com.swtdesigner SwingResourceManager]
           [org.micromanager.acquisition ImageStorageListener MMImageCache])
  (:use [org.micromanager.browser.utils
            :only (gen-map constrain-to-parent create-button create-icon-button
                   attach-action-key remove-borders choose-directory
                   read-value-from-prefs write-value-to-prefs remove-nth
                   awt-event)]
        [clojure.contrib.json :only (read-json write-json)]
        [org.micromanager.mm :only (load-mm gui)]))

(def browser (atom nil))

(def settings-window (atom nil))

(def collections (atom nil))

(def current-data (atom nil))

(def scanning-agent (agent nil))

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

(defn set-filter [table text]
  (let [sorter (.getRowSorter table)
        column-indices
        (int-array (map #(.getModelIndex %)
        (enumeration-seq (.. table getColumnModel getColumns))))]
    (do (.setRowFilter sorter (RowFilter/regexFilter text column-indices)))))

(defn connect-search [search-field table]
  (let [d (.getDocument search-field)
        f #(awt-event
            (set-filter table (.getText d 0 (.getLength d)))
            (.setBackground search-field
              (if (zero? (.getRowCount table))
                Color/PINK Color/WHITE)))]
    (.addDocumentListener d
      (reify DocumentListener
        (insertUpdate [_ _] (f))
        (changedUpdate [_ _] (f))
        (removeUpdate [_ _] (f))))))

(defn get-model-column-index [table text]
  (.. table (getColumn text) getModelIndex))

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

(defn create-browser-table-model [headings]
  (proxy [AbstractTableModel] []
    (getRowCount [] (count @current-data))
    (getColumnCount [] (count (first @current-data)))
    (getValueAt [row column] (nth (nth @current-data row) column))
    (getColumnName [column] (nth tags column))))

(defn get-row-path [row]
  (nth row (.indexOf tags "Path")))

(defn refresh-row [rows new-row]
  (let [changed (atom false)
        new-path (get-row-path new-row)
        data
          (vec
            (for [row rows]
              (if (= (get-row-path row) new-path)
                (do (reset! changed true) new-row)
                row)))]
    (if @changed
      data
      (conj data new-row))))

(defn add-browser-table-row [new-row]
  (swap! current-data refresh-row (vec new-row))
  (.fireTableDataChanged (.getModel (@browser :table))))

(defn remove-browser-table-row [n]
  (swap! current-data remove-nth n)
  (.fireTableRowsDeleted (.getModel (@browser :table)) n n))

(defn remove-location [loc]
  (let [location-column (.indexOf tags "Location")]
    (doseq [browser-row (reverse (range (count @current-data)))]
      (when (= (nth (nth @current-data browser-row) location-column) loc)
        (remove-browser-table-row browser-row)))))

(defn remove-selected-locations [] 
  (let [location-table (get-in @settings-window [:locations :table])
        selected-rows (.getSelectedRows location-table)
        location-model (.getModel location-table)
        location-column (.indexOf tags "Location")]
    (doseq [location-row (reverse selected-rows)]
      (when-let [loc (.getValueAt location-model location-row 0)]
        (when-not (empty? loc)
          (remove-location loc)))
      (.removeRow location-model location-row))))
    
(defn clear-history []
  (remove-location ""))

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
  (println "get-summary-map" data-set location)
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

(def default-headings ["Path" "Time" "Frames" "Comment" "Location"])
  
(defn add-summary-maps [browser map-list headings]
  (send-off scanning-agent 
            (fn [_] (doseq [m map-list]
                      (Thread/sleep 5)
                      (add-browser-table-row
                        (map #(get m %) headings))))))

(defn add-location [location]
  (.. (get-in @settings-window [:locations :table])
      getModel (addRow (Vector. (list location))))
  (let [maps (get-summary-maps location)]
    (add-summary-maps @browser maps tags)))

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

(defn update-browser-column [])

(defn add-browser-column [tag]
  (let [column (doto (TableColumn. (.indexOf tags tag))
                 (.setHeaderValue tag))]
    (.addColumn (@browser :table) column)
    column))

(defn column-visible? [tag]
  (true?
    (some #{true}
      (for [col (enumeration-seq (.getColumns (->@browser :table .getColumnModel)))]
        (= (.getIdentifier col) tag)))))

(defn set-column-visible [tag visible]
      (let [table (@browser :table)]
        (if (and visible (not (column-visible? tag)))
          (add-browser-column tag))
        (if (and (not visible) (column-visible? tag))
          (.removeColumn (.getColumnModel table)
            (.getColumn table tag)))))

(defn create-column-model []
   (proxy [AbstractTableModel] []
                (getRowCount [] (count tags))
                (getColumnCount [] 2)
                (getValueAt [row column]
                  (let [tag (nth tags row)]
                    (condp = column
                      0 (column-visible? tag)
                      1 tag)))
                (setValueAt [val row column]
                  (when (zero? column)
                    (let [tag (nth tags row)]
                      (set-column-visible tag val))))
                (getColumnClass [column]
                  (get [Boolean String] column))))

(defn create-column-table []
  (let [table (proxy [JTable] []
                (isCellEditable [_ i] (get [true false] i)))
        model (create-column-model)]
    (doto table
      (.setModel model)
      (.setRowSelectionAllowed false)
      (.setFocusable false)
      (.. getColumnModel (getColumn 0) (setMinWidth 20))
      (.. getColumnModel (getColumn 0) (setMaxWidth 20)))
    table))

(defn update-collection-menu [name ]
  (let [menu (@browser :collection-menu)
        names (sort (keys @collections))
        listeners (.getItemListeners menu)]
    (dorun (map #(.removeItemListener menu %) listeners))
    (.removeAllItems menu)
    (dorun (map #(.addItem menu %) names))
    (.addItem menu "New...")
    (.setSelectedItem (@browser :collection-menu) name)
    (dorun (map #(.addItemListener menu %) listeners))))

;; collection files


(defn save-last-collection [name]
  (write-value-to-prefs prefs "last-collection" name))

(defn load-last-collection []
  (or (read-value-from-prefs prefs "last-collection")
    (System/getProperty "user.name")))

(defn read-collection-map []
  (reset! collections
    (or (read-value-from-prefs prefs "collection-files")
        (let [name (System/getProperty "user.name")]
          {name (.getAbsolutePath (File. (str name ".mmdb.txt")))}))))

(defn save-collection-map []
  (write-value-to-prefs prefs "collection-files" @collections))

(defn get-current-data-and-settings []
  (let [table (@browser :table)
        model (.getModel table)]
    {:browser-model-data @current-data
     :browser-model-headings tags
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
  (awt-event
    (let [table (@browser :table)
          model (.getModel table)
          {:keys [browser-model-data
                  browser-model-headings
                  window-size display-columns
                  locations sorted-column]} settings]
      (def dc display-columns)
      (reset! current-data browser-model-data)
      (doto model (.fireTableDataChanged) (.fireTableStructureChanged))
      (-> @settings-window :locations :table .getModel
        (.setDataVector
          (Vector. (map #(Vector. (list %)) (seq locations)))
          (Vector. (list "Locations"))))
      (let [column-model (.. table getColumnModel)]
        (println (-> column-model .getColumns enumeration-seq count))
        (dorun (map #(.removeColumn column-model %)
             (-> column-model .getColumns enumeration-seq reverse))))
      (println display-columns)
      (let [total-width (.getWidth table)]
        (doseq [col display-columns]
          (println col)
          (doto (add-browser-column (:title col))
            (.setPreferredWidth (* total-width (:width col)))))))
      (-> @settings-window :columns :table
                           .getModel .fireTableDataChanged)))

(defn save-data-and-settings [collection-name]
  (println "save-data-and-settings" collection-name)
  (with-open [pr (PrintWriter. (get @collections collection-name))]
    (write-json (get-current-data-and-settings) pr))
  (println "saved data and settings: " (:display-columns (get-current-data-and-settings))))

(defn load-data-and-settings [name]
  (save-last-collection name)
  (let [f (get @collections name)]
    (apply-data-and-settings (read-json (slurp f))))
  (update-collection-menu name))

(defn user-creates-collection []
  (let [prompt-msg "Please enter a name for the new collection."]
    (loop [msg prompt-msg]
       (let [collection-name (JOptionPane/showInputDialog msg)]
         (cond
           (empty? (.trim collection-name))
             (recur (str "Name must contain at least one character.\n"
                         prompt-msg))
           (contains? @collections collection-name)
             (recur (str "There is already a collection named " collection-name "!\n"
                         prompt-msg))
           :else collection-name)))))

(defn new-data-and-settings []
  (let [collection-name (user-creates-collection)]
    (swap! collections assoc collection-name
      (.getAbsolutePath (File. (str collection-name ".mmdb.txt"))))
    (println "hi")
    (reset! current-data nil)
    (save-collection-map)
    (save-data-and-settings collection-name)
    (awt-event
      (update-collection-menu collection-name)
        (let [m (-> @browser :table .getModel)]
          (.fireTableDataChanged m)
             ))))

(defn create-image-storage-listener []
  (reify ImageStorageListener
    (imageStorageFinished [_ path]
      (add-summary-maps @browser
                        (list (get-summary-map path "")) tags))))
      
(defn refresh-collection []
  (let [n (.indexOf tags "Location")
        locations (distinct (map #(get % n) @current-data))]
    ; blah blah
  ))

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
          (create-icon-button (get-icon "minus.png") remove-selected-locations)
        columns (label-table (create-column-table) "Columns" split-pane)
        frame (JFrame. "Micro-Manager Data Set Browser Settings")
        main-panel (.getContentPane frame)
        clear-history-button (create-button "Clear history" clear-history)]
    (apply remove-borders (.getComponents split-pane))
    (doto split-pane
      (.setResizeWeight 0.5)
      (.setDividerLocation 0.5))
    (remove-borders split-pane)
    (doto main-panel (.add split-pane) (.add clear-history-button)) 
    (doto (:panel locations)
      (.add add-location-button) (.add remove-location-button))
    (.setLayout main-panel (SpringLayout.))
    (constrain-to-parent
      split-pane :n 30 :w 5 :s -5 :e -5
      add-location-button :n 0 :e -38 :n 18 :e -20
      remove-location-button :n 0 :e -18 :n 18 :e 0
      clear-history-button :n 5 :w 5 :n 30 :w 125)
    (.setBounds frame 50 50 600 600)
    (gen-map frame locations columns)))

(defn create-collection-menu-listener []
  (reify ItemListener
    (itemStateChanged [_ e]
      (println e)
      (let [item (.getItem e)]
        (if (= (.getStateChange e) ItemEvent/SELECTED)
          (condp = item
            "New..." (new-data-and-settings)
            (load-data-and-settings item))
          (save-data-and-settings item))))))

(defn create-browser []
  (let [frame (JFrame. "Micro-Manager Data Set Browser")
        panel (.getContentPane frame)
        table (proxy [JTable] [] (isCellEditable [_ _] false))
        scroll-pane (JScrollPane. table)
        search-field (JTextField.)
        search-label (JLabel. (get-icon "zoom.png"))
        refresh-button (create-button "Refresh" refresh-collection)
        settings-button (create-button "Settings..."
                          #(.show (:frame @settings-window)))
        collection-label (JLabel. "Collection:")
        collection-menu (JComboBox.)]
    (doto panel
       (.add scroll-pane) (.add search-field)
       (.add settings-button) (.add search-label)
       (.add collection-label) (.add collection-menu))
    (doto table
      (.setAutoCreateRowSorter true)
      (.setShowGrid false)
      (.setGridColor Color/LIGHT_GRAY)
      (.setShowVerticalLines true))
    (.addItemListener collection-menu (create-collection-menu-listener))
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
      (.setBounds 50 50 540 500))
    (gen-map frame table scroll-pane settings-button search-field
             collection-menu)))

(defn init-columns []
  (vec (map #(vec (list % false)) tags)))

(defn start-browser []
  (load-mm)
  (read-collection-map)
  (reset! settings-window (create-settings-window))
  (reset! browser (create-browser))
  (MMImageCache/addImageStorageListener (create-image-storage-listener))
  (.setModel (:table @browser) (create-browser-table-model tags))
  (load-data-and-settings (load-last-collection))
  (.show (@browser :frame))
  browser)


