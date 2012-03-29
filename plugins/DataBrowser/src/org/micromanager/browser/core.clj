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
                        JTable JTextField RowFilter RowSorter$SortKey SpringLayout]
           [javax.swing.table AbstractTableModel DefaultTableModel
                              TableColumn TableRowSorter]
           [javax.swing.event DocumentListener TableModelListener]
           [java.io BufferedReader File FileReader PrintWriter]
           [java.util Comparator Vector]
           [java.util.prefs Preferences]
           [java.util.concurrent LinkedBlockingQueue]
           [java.awt Color Dimension Font Insets]
           [java.awt.event ItemEvent ItemListener KeyAdapter MouseAdapter
                           WindowAdapter WindowListener]
           [com.swtdesigner SwingResourceManager]
           [org.micromanager.api ImageCacheListener]
           [org.micromanager.acquisition MMImageCache]
           [org.micromanager.utils JavaUtils ReportingUtils])
  (:use [org.micromanager.browser.utils
            :only (gen-map constrain-to-parent create-button create-icon-button
                   attach-action-key remove-borders choose-directory
                   read-value-from-prefs write-value-to-prefs 
                   remove-value-from-prefs remove-nth
                   awt-event persist-window-shape close-window
                   create-alphanumeric-comparator
                   super-location? get-file-parent)]
        [clojure.data.json :only (read-json json-str)]
        [org.micromanager.mm :only (load-mm gui)]))

(def browser (atom nil))

(def settings-window (atom nil))

(def collections (atom nil))

(def current-data (ref nil))

(def current-locations (ref (sorted-set)))

(def pending-locations (LinkedBlockingQueue.))

(def pending-data-sets (LinkedBlockingQueue.))

(def stop (atom false))

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

(def alphanumeric-comparator (create-alphanumeric-comparator))

(defn clear-queues []
  (.clear pending-locations)
  (.clear pending-data-sets))

(defn update-browser-status []
  (-> @browser :frame
      (.setTitle
        (str "Micro-Manager Data Set Browser ("
             (if (and (empty? pending-data-sets)
                      (empty? pending-locations))
               "Idle" "Scanning")
             " \u2014 " (count @current-data) " images)"))))

(defn get-icon [name]
  (SwingResourceManager/getIcon
    org.micromanager.MMStudioMainFrame (str "icons/" name)))

(defn get-table-columns [table]
  (when-let [col-vector (.. table getColumnModel getColumns)]
    (enumeration-seq col-vector)))

(defn remove-all-columns [table]
  (let [column-model (. table getColumnModel)]
    (dorun (->> table get-table-columns reverse
                      (map #(.removeColumn column-model %))))))

(defn set-filter [table text]
  (let [chunks (.split text "\\s")
        sorter (.getRowSorter table)
        column-indices
          (int-array (map #(.getModelIndex %) (get-table-columns table)))
        filters
          (map #(RowFilter/regexFilter (str "(?i)\\Q" % "\\E") column-indices)
               chunks)]
    (do (.setRowFilter sorter   
          (RowFilter/andFilter filters)))))

(def filter-agent (agent nil))

(defn connect-search [search-field table]
  (let [d (.getDocument search-field)
        f #(send-off filter-agent
            (fn [_]
              (set-filter table (.getText d 0 (.getLength d)))
              (.setBackground search-field
                (if (zero? (.getRowCount table))
                  Color/PINK Color/WHITE))))]
    (.addDocumentListener d
      (reify DocumentListener
        (insertUpdate [_ _] (f))
        (changedUpdate [_ _] (f))
        (removeUpdate [_ _] (f))))))

(defn row-index-to-path [i]
  (let [table (@browser :table)]
    (.. table getModel
        (getValueAt (.convertRowIndexToModel table i)
                    (.indexOf tags "Path")))))

(defn open-selected-files [table]
  (doseq [i (.getSelectedRows table)]
    (let [f (row-index-to-path i)]
      (if (.exists (File. f))
        (.openAcquisitionData gui f false)
        (ReportingUtils/showError "File not found.")))))

(defn listen-to-open [table]
  (.addMouseListener table
    (proxy [MouseAdapter] []
      (mouseClicked [e]
        (when (= 2 (.getClickCount e)) ; double click
          (open-selected-files table))))))

(defn create-browser-table-model [headings]
  (proxy [AbstractTableModel] []
    (getRowCount [] (count @current-data))
    (getColumnCount [] (count headings))
    (getValueAt [row column] (when (pos? (count @current-data))
                               (nth (nth @current-data row) column)))
    (getColumnName [column] (nth headings column))))

(defn create-locations-table-model []
  (proxy [AbstractTableModel] []
    (getRowCount [] (count @current-locations))
    (getColumnCount [] 1)
    (getValueAt [row _] (nth (vec @current-locations) row))
    (getColumnName [_] "Locations")))

(defn get-row-path [row]
  (nth row (.indexOf tags "Path")))

(defn refresh-row [rows new-row]
  (let [location-col (.indexOf tags "Location")
        new-loc (new-row location-col)
        changed (atom false)
        new-path (get-row-path new-row)
        data
          (vec
            (for [old-row rows]
              (if (= (get-row-path old-row) new-path)
                (do (reset! changed true)
                    (let [old-loc (nth old-row location-col)]
                      (if (super-location? new-loc old-loc)
                        (assoc new-row location-col old-loc)
                        new-row)))
                old-row)))]
    (if @changed
      data
      (conj data new-row))))

(defn update-browser-table []
  (let [table (@browser :table)
        selected-rows (.getSelectedRows table)
        selected-paths (set (map row-index-to-path selected-rows))]
    (.. table getModel fireTableDataChanged)
    (doseq [selected-row selected-rows]
      (.addRowSelectionInterval table selected-row selected-row))
;    (doseq [row (range (.getRowCount table))]
;      (when (selected-paths (row-index-to-path row))
;        (.addRowSelectionInterval table row row)))))
))

(defn add-browser-table-row [new-row]
  (let [row-vec (vec new-row)
        location (row-vec (.indexOf tags "Location"))]
    (dosync
      (if (or (contains? @current-locations location) (= location ""))
        (alter current-data refresh-row row-vec))))
  (awt-event (update-browser-table)))
    
(defn remove-location [loc]
  (dosync
    (alter current-locations disj loc)
    (let [location-column (.indexOf tags "Location")]
      (alter current-data
             (fn [coll] (vec (remove #(= (nth % location-column) loc) coll))))))
  (awt-event (update-browser-table)
             (-> @settings-window :locations :table .getModel .fireTableDataChanged)
))

(defn remove-selected-locations [] 
  (let [location-table (-> @settings-window :locations :table)
        location-model (.getModel location-table)
        selected-rows (.getSelectedRows location-table)]
    (awt-event
      (dorun
        (map remove-location
          (remove nil?
            (for [location-row selected-rows]
              (.getValueAt location-model location-row 0))))))
      (.fireTableDataChanged location-model))
  (awt-event (update-browser-status)))
    
(defn clear-history []
  (remove-location "")
  (awt-event (update-browser-status)))

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
  (let [raw-summary-map (read-summary-map data-set)]
    (merge raw-summary-map
      (if-let [frames (count-frames data-set)]
        {"Frames" frames})
      (if-let [d+c (get-display-and-comments data-set)]
        {"Comment" (get-in d+c ["Comments" "Summary"])
         "FrameComments" (dissoc (get d+c "Comments") "Summary")})
      (let [data-dir (File. data-set)
            position-count (get raw-summary-map "Positions")
            position (get raw-summary-map "Position")
            path (if (or (.. data-dir getName (startsWith "Snap"))
                         (not (empty? position))
                         (and position-count (pos? position-count)))
                   (get-file-parent data-dir)
                   (.getAbsolutePath data-dir))]
        {"Path"     path
         "Name"      (.getName (File. path))
         "Location" location}))))

(defn remove-sibling-positions [summary-map]
  (doseq [pending-data-set pending-data-sets]
    (when (super-location? (first pending-data-set) (get summary-map "Path"))
      (.remove pending-data-sets pending-data-set)
      (println "removed " pending-data-set))))

(def default-headings ["Path" "Time" "Frames" "Comment" "Location"])

(defn start-scanning-thread []
  (doto (Thread.
            (fn []
              (try
                (dorun
                  (loop []
                    (Thread/sleep 5)
                    (let [location (.take pending-locations)]
                      (when-not (= location pending-locations)
                        (doseq [data-set (find-data-sets location)]
                          ; (println "data-set:" data-set)
                          (.put pending-data-sets [data-set location]))
                        (recur)))))
                  (catch Exception e nil)))
          "data browser scanning thread") .start))


(defn start-reading-thread []
  (doto (Thread.
          (fn []
            (try
              (dorun
                (loop []
                  (Thread/sleep 5)
                  (let [data-set (.take pending-data-sets)]
                    ;(println @current-locations (second data-set) (contains? @current-locations (second data-set)))
                    (if (= data-set pending-data-sets) ;; poison
                      (update-browser-status)
                      (let [loc (second data-set)]
                        (when (or (= loc "") (contains? @current-locations loc))
                          (let [m (apply get-summary-map data-set)]
                            (add-browser-table-row (map #(get m %) tags))
                            (remove-sibling-positions m)
                            (awt-event (update-browser-status))))
                        (recur))))))
              (catch Exception e (.printStackTrace e))
              ))
          "data browser reading thread") .start))

(defn scan-location [location]
  (.put pending-locations location)
    (update-browser-status)
    (awt-event (-> @settings-window :locations :table
                   .getModel .fireTableDataChanged)))

(defn add-location [location]
  (if
    (dosync
      (if (some #{true}
                (map #(super-location? location %) @current-locations))
        nil
        (do (doseq [old-loc @current-locations]
              (if (super-location? old-loc location)
                (remove-location old-loc)))
              (alter current-locations conj location))))
    (do (scan-location location) true)
    false))

(defn user-add-location []
  (when-let [loc (choose-directory nil
                     "Please add a location to scan for Micro-Manager image sets.")]
    (when-not (add-location (.getAbsolutePath loc))
      (JOptionPane/showMessageDialog
        (@settings-window :frame) "This new location cannot be added because it is already
inside an existing location in your collection."
        (.getAbsolutePath loc) JOptionPane/INFORMATION_MESSAGE))))

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
      (for [col (get-table-columns (@browser :table))]
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


(defn set-default-comparator [table]
  (let [row-sorter (.getRowSorter table)]
    (dotimes [i (-> @browser :table .getModel .getColumnCount)]
        (.setComparator row-sorter i alphanumeric-comparator))))

(defn update-collection-menu [name]
  (awt-event
    (let [menu (@browser :collection-menu)
          names (sort (keys @collections))
          listeners (.getItemListeners menu)]
      (dorun (map #(.removeItemListener menu %) listeners))
      (.removeAllItems menu)
      (dorun (map #(.addItem menu %) names))
      (.addItem menu "New...")
      (.setSelectedItem (@browser :collection-menu) name)
      (dorun (map #(.addItemListener menu %) listeners)))))

;; collection files


(defn set-last-collection-name [name]
  (write-value-to-prefs prefs "last-collection" name))

(defn get-last-collection-name []
  (or (read-value-from-prefs prefs "last-collection")
    (System/getProperty "user.name")))

(defn read-collection-map []
  (reset! collections
    (or (read-value-from-prefs prefs "collection-files")
        (let [name (System/getProperty "user.name")]
          {name (.getAbsolutePath (File. (JavaUtils/getApplicationDataPath)
                                         (str name ".mmdb.txt")))}))))

(defn save-collection-map []
  (write-value-to-prefs prefs "collection-files" @collections))

;; "data and settings"

(defn fresh-data-and-settings []
  {:browser-model-data nil
   :browser-model-headings tags
   :window-size nil
   :display-columns
     [{:width 0.3 :title "Path"}
      {:width 0.3 :title "Time"}
      {:width 0.1 :title "Frames"}
      {:width 0.3 :title "Comment"}]
   :locations nil
   :sorted-column {:order 1 :model-column "Time"}})

(defn save-data-and-settings [collection-name settings]
  (let [data-path (get @collections collection-name)]
    (-> (File. data-path) .getParent (File.) .mkdirs)
    (with-open [pr (PrintWriter. data-path)]
      (.print pr (json-str settings)))))

(defn load-data-and-settings [name]
  (or 
    (when-let [f (get @collections name)]
      (when (.exists (File. f))
        (read-json (slurp f))))
    (fresh-data-and-settings)))

;; data and settings <--> gui

(defn get-current-data-and-settings []
  (let [table (@browser :table)
        model (.getModel table)]
    {:browser-model-data (map #(zipmap tags %) @current-data)
     :browser-model-headings tags
     :window-size (let [f (@browser :frame)] [(.getWidth f) (.getHeight f)])
     :display-columns
       (let [total-width (float (.getWidth table))]
         (when (pos? total-width)
           (map #(hash-map :width (/ (.getWidth %) total-width)
                                  :title (.getIdentifier %))
                (get-table-columns table))))
     :locations @current-locations
     :sorted-column
       (when-let [sort-key (->> table .getRowSorter .getSortKeys seq first)]
         {:order ({SortOrder/ASCENDING 0 SortOrder/DESCENDING 1 SortOrder/UNSORTED 2}
                   (.getSortOrder sort-key))
          :model-column (.getColumnName model (.getColumn sort-key))})}))

(defn apply-data-and-settings [collection-name settings]
  (clear-queues)
  (update-collection-menu collection-name)
  (set-last-collection-name collection-name)
  (let [table (@browser :table)
        model (.getModel table)
        {:keys [browser-model-data
                browser-model-headings
                window-size display-columns
                locations sorted-column]} settings]
    (dosync
      (ref-set current-data (vec (map (fn [r] (vec (map #(get r (keyword %)) tags))) browser-model-data)))
      (ref-set current-locations (apply sorted-set locations)))
    (when (pos? (count @current-data))
      (set-default-comparator table))
    (.fireTableDataChanged model)
    (-> @settings-window :locations :table .getModel .fireTableDataChanged)
    (remove-all-columns table)
    (let [total-width (.getWidth table)]
      (doseq [col display-columns]
        (doto (add-browser-column (:title col))
          (.setPreferredWidth (* total-width (:width col))))))
    ;(println "sorted-column" sorted-column :model-column)
    (when sorted-column
    (try
      (.. table getRowSorter
          (setSortKeys (list (RowSorter$SortKey.
                               (.indexOf tags (sorted-column :model-column))
                               (nth (SortOrder/values) (sorted-column :order))))))
      (catch Exception e nil)))
    (-> @settings-window :columns :table
                         .getModel .fireTableDataChanged))
  (update-browser-status))

;; creating a new collection

(defn user-specifies-collection-name []
  (let [prompt-msg "Please enter a name for the new collection."]
    (loop [msg prompt-msg]
       (let [collection-name (JOptionPane/showInputDialog msg)]
         (cond
           (nil? collection-name)
             nil
           (empty? (.trim collection-name))
             (recur (str "Name must contain at least one character.\n"
                         prompt-msg))
           (contains? @collections collection-name)
             (recur (str "There is already a collection named " collection-name "!\n"
                         prompt-msg))
           :else collection-name)))))

(defn create-new-collection []
  (let [collection-name (user-specifies-collection-name)]
    (if collection-name
      (do
        (swap! collections assoc collection-name
          (.getAbsolutePath (File. (JavaUtils/getApplicationDataPath)
                                   (str collection-name ".mmdb.txt"))))
        (save-collection-map)
        (awt-event
          (apply-data-and-settings collection-name (fresh-data-and-settings))))
      (update-collection-menu (get-last-collection-name)))))

(defn create-image-storage-listener []
  (reify ImageCacheListener
    (imagingFinished [_ path]
      ;(println "image storage:" path)
      (doseq [data-set (find-data-sets path)]
        (.put pending-data-sets [data-set ""])))))

(defn refresh-collection []
  (clear-queues)
  (dorun (map scan-location @current-locations)))

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
    (-> locations :table (.setModel (create-locations-table-model)))
    (persist-window-shape prefs "settings-window-shape" frame)
    (gen-map frame locations columns)))

(defn create-collection-menu-listener []
  (reify ItemListener
    (itemStateChanged [_ e]
      (let [item (.getItem e)]
        (if (= (.getStateChange e) ItemEvent/SELECTED)
          (condp = item
            "New..." (create-new-collection)
            (awt-event (apply-data-and-settings item (load-data-and-settings item))))
          (save-data-and-settings item (get-current-data-and-settings)))))))

(defn handle-exit []
  (println "Shutting down Data Browser.")
  (clear-queues)
  (.put pending-data-sets pending-data-sets)
  (.put pending-locations pending-locations)
  (close-window (@browser :frame))
  (close-window (@settings-window :frame))
  true)

(defn create-browser []
  (let [frame (JFrame.)
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
       (.add scroll-pane) (.add search-field) (.add refresh-button)
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
                         settings-button :n 5 :w 500 :n 28 :w 600
                         refresh-button :n 5 :w 405 :n 28 :w 500
                         search-label :n 5 :w 5 :n 28 :w 25
                         collection-label :n 5 :w 205 :n 28 :w 275
                         collection-menu :n 5 :w 275 :n 28 :w 405)
    (connect-search search-field table)
    (.setSortsOnUpdates (.getRowSorter table) false)
    (listen-to-open table)
    (attach-action-key search-field "ESCAPE" #(.setText search-field ""))
    (doto frame
      (.setBounds 50 50 620 500)
      (.addWindowListener
        (proxy [WindowAdapter] []
          (windowClosing [e]
            (clear-queues)
              (save-data-and-settings
                (get-last-collection-name)
                  (get-current-data-and-settings))
            (close-window (@settings-window :frame))
            (.setVisible frame false)))))
    (persist-window-shape prefs "browser-shape" frame)
    (gen-map frame table scroll-pane settings-button search-field
             collection-menu refresh-button)))

(defn init-columns []
  (vec (map #(vec (list % false)) tags)))

(defn start-browser []
  (load-mm)
  (read-collection-map)
  (reset! settings-window (create-settings-window))
  (reset! browser (create-browser))
  (update-browser-status)
  (start-scanning-thread)
  (start-reading-thread)
  ;(MMImageCache/addImageCacheListener (create-image-storage-listener))
  (awt-event
    (.show (@browser :frame))
    (.setModel (:table @browser) (create-browser-table-model tags))
    (let [collection-name (get-last-collection-name)]
      (apply-data-and-settings collection-name (load-data-and-settings collection-name))))
  browser)

(defn show-browser []
  (if-not @browser
    (start-browser)
    (.show (@browser :frame))))


