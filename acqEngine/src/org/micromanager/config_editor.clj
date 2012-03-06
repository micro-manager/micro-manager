(ns org.micromanager.config-editor
  (:import [javax.swing DefaultListModel JFrame JList JPanel JTable 
                        JScrollPane JViewport ListSelectionModel SpringLayout]
           [javax.swing.event CellEditorListener ListSelectionListener]
           [javax.swing.table AbstractTableModel]
           [java.awt Color Dimension])
  (:use [org.micromanager.mm :only [load-mm core edt get-config]]))

(declare update-presets-table)


;; editing groups

(defn add-group [components group]
  (core defineConfigGroup group)
  (update-groups-table (:groups-table components))
  )

(defn rename-group [components group-old group-new]
  (core renameConfigGroup group-old group-new)
  (update-groups-table (:groups-table components))
  )

(defn delete-group [components group]
  (core deleteConfigGroup group)
  (update-groups-table (:groups-table components))
  )

;; editing presets

(defn add-property [group]
  )

(defn remove-property [group property])

(defn add-preset [components group preset]
  (core defineConfig group preset)
  (update-presets-table components group))

(defn rename-preset [components group preset-old preset-new]
  (core renameConfig group preset-old preset-new)
  (update-presets-table components group))

(defn delete-preset [components group preset]
  (core deleteConfig group preset)
  (update-presets-table components group))

(defn set-preset-value [components group preset property value]
  ; TODO: make this work.
  )

;; swing layout

(defn put-constraint [comp1 edge1 comp2 edge2 dist]
  (let [edges {:n SpringLayout/NORTH
               :w SpringLayout/WEST
               :s SpringLayout/SOUTH
               :e SpringLayout/EAST}]
  (.. comp1 getParent getLayout
            (putConstraint (edges edge1) comp1 
                           dist (edges edge2) comp2))))

(defn put-constraints [comp & args]
  (let [args (partition 3 args)
        edges [:n :w :s :e]]
    (dorun (map #(apply put-constraint comp %1 %2) edges args))))

(defn constrain-to-parent
  "Distance from edges of parent comp args"
  [& args]
  (doseq [[comp & params] (partition 9 args)]
    (apply put-constraints comp
           (flatten (map #(cons (.getParent comp) %) (partition 2 params))))))

(defn add-component [parent child & constraints]
  (.add parent child)
  (apply constrain-to-parent child constraints))

(defn context-menu [component & items]
  (partition 2 items))

;; main stuff

(load-mm)

(defn update-list [list data]
  (let [model (.getModel list)]
    (.clear model)
    (doseq [datum data]
      (.addElement model datum)))
  list)

(defn groups []
  (sort (seq (core getAvailableConfigGroups))))

(defn update-group-list [list]
  (update-list list (sort (seq (core getAvailableConfigGroups)))))

(defn group-list []
  (update-group-list (JList. (DefaultListModel.))))

(defn presets [group]
  (when-not (empty? group)
    (sort (seq (core getAvailableConfigs group)))))

(defn group-data [group]
  (into (sorted-map) (map #(vector % (get-config group %)) (presets group))))

(defn properties [group-data]
  (sort (set (apply concat (map keys (vals group-data))))))

(defn property-name [prop-vec]
  (let [[dev prop] prop-vec]
    (str dev "-" prop)))

(defn table-data [group-data]
  (for [property (properties group-data)]
          (for [preset (keys group-data)]
            (get-in group-data [preset property] ""))))
    
(defn into-1d-array
  ([data]
    (into-array data))
  ([type data]
    (if-not (empty? data)
      (into-array data)
      (make-array type 0))))

(defn into-2d-array
  ([data]
    (into-array (map into-array data)))
  ([type data]
    (if-not (empty? data)
      (into-array (map #(into-array type %) data))
      (make-array type 0 0))))

(defn table []
  (let [table (JTable.)]
    (doto table
      )))

(defn scroll-pane [table]
   (let [sp (JScrollPane. table)]
    sp))

(defn attach-selection-listener [table f]
  (-> table .getSelectionModel
      (.addListSelectionListener
        (reify ListSelectionListener
          (valueChanged [this event]
            (when-not (.getValueIsAdjusting event)
              (try
                (f (.getSelectedRow table))
                (catch Exception e (.printStackTrace e)))))))))

(defn populate-list [list data]
  (let [model (.getModel list)]
    (.clear model)
    (doseq [datum data]
      (.addElement model datum)))
  list)

(defn handle-group-text-changed [e]
  (println (.getText (.getComponent (.getSource e)))))

(defn allow-group-renaming [table]
  (.. table
      (getDefaultEditor String)
      (addCellEditorListener
        (reify CellEditorListener 
          (editingStopped [this e]
            (handle-group-text-changed e))))))
  
(defn group-table-model []
  (proxy [AbstractTableModel] []
    (getColumnCount [] 1)
    (getRowCount [] (count (core getAvailableConfigGroups)))))
    

(defn show []
  (let [f (JFrame. "Micro-Manager Configuration Preset Editor")
        cp (.getContentPane f)
        groups-table (table)
        presets-table (table)
        preset-names-table (table)
        groups-sp (scroll-pane groups-table)
        presets-sp (scroll-pane presets-table)
        ]
    (edt 
      (.setAutoResizeMode presets-table JTable/AUTO_RESIZE_OFF)
      (.. groups-table getSelectionModel (setSelectionMode ListSelectionModel/SINGLE_SELECTION))
      (.. presets-table getTableHeader (setReorderingAllowed false))
      (.setRowHeaderView presets-sp preset-names-table)
      (.. preset-names-table getParent (setPreferredSize (Dimension. 150 100)))
      ;(.setFixedCellWidth preset-names-list 150)
      ;(.setFixedCellHeight preset-names-list (.getRowHeight presets-table))
      (.setBackground preset-names-table (Color. 0xE0 0xE0 0xE0))
      (doto cp
        (.setLayout (SpringLayout.))
        (add-component presets-sp :n 5 :w 155 :s -5 :e -5)
        (add-component groups-sp :n 5 :w 5 :s -5 :w 150))
        (.setBounds f 100 100 800 500)
        (.show f))
    {:groups-table groups-table
     :presets-table presets-table
     :preset-names-table preset-names-table}))

(defn set-table-data [table body header]
  (.setDataVector
    (.getModel table)
    (into-2d-array String body)
    (into-1d-array String header)))

(defn update-presets-table [components group]
  (let [preset-names-table (components :preset-names-table)
        presets-table (components :presets-table)
        group-data (group-data group)
        body (table-data group-data)
        header (keys group-data)
        prop-names (map list (map property-name (properties group-data)))]
    (edt (set-table-data presets-table body header)
         (when-let [column-objects (.getColumns (.getColumnModel presets-table))]
           (doseq [column (enumeration-seq column-objects)]
             (.setPreferredWidth column 120)))
         (set-table-data preset-names-table prop-names (list ""))
         (.. preset-names-table getColumnModel (getColumn 0) (setMaxWidth 150)))))

(defn update-groups-table [t]
  (edt (set-table-data t (map list (groups)) (list "Groups"))))

(defn activate-groups-table [components]
  (attach-selection-listener
    (components :groups-table)
    #(update-presets-table components
                           (try (.getValueAt (components :groups-table) % 0)
                                (catch Exception _ nil)))))


;; test/run

(defn start
  ([]
    (start nil))
  ([group]
    (let [components (show)]
      (update-groups-table (components :groups-table))
      (activate-groups-table components)
      components)))
