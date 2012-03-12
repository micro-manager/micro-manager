(ns org.micromanager.config-editor
  (:import [javax.swing DefaultListModel JFrame JList JPanel JTable
                        DefaultListSelectionModel JScrollPane JViewport
                        ListSelectionModel SpringLayout JPopupMenu]
           [javax.swing.event CellEditorListener ListSelectionListener]
           [java.awt.event MouseAdapter]
           [javax.swing.table AbstractTableModel]
           [java.awt Color Dimension])
  (:use [org.micromanager.mm :only [config-struct load-mm
                                    gui core edt get-config]]))

(load-mm)

;; utils

(defn slow-update [reference update-fn]
  (let [a (agent nil)
        update-fn-sender
        (fn [_ ref _ _]
          (send-off a
            #(if (not= % @ref)
               (update-fn @ref))
               @ref))]                                
    (add-watch reference (gensym "slow_update")
               update-fn-sender)))
           

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
  (partition 2 items)
  ;;TODO: finish this
  )

(defn scroll-pane [table]
   (let [sp (JScrollPane. table)]
    sp))

(defn update-table [table]
  (doto (.getModel table)
    .fireTableDataChanged
    .fireTableStructureChanged)
  table)

;; events

(defn attach-double-click-listener [component f]
  (.addMouseListener component
    (proxy [MouseAdapter] []
      (mouseClicked [event]
        (when (= 2 (.getClickCount event))
          (f (.getX event) (.getY event)))))))

;; main stuff

(load-mm)

(defn presets [group]
  (when-not (empty? group)
    (sort (seq (core getAvailableConfigs group)))))

(def groups (atom nil))

(def group-data (atom nil))

(defonce widgets (atom nil))

(defn get-selected-group []
  (let [row (.getSelectedRow (@widgets :groups-table))
        groups @groups]
    (when (< -1 row (count groups))
      (nth groups row))))


(defn update-group-data []
    (reset! group-data 
            (when-let [group (get-selected-group)]
              (into (sorted-map)
                    (map #(vector % (get-config group %))
                         (presets group))))))

(defn update-groups []
  (reset! groups (vec (seq (core getAvailableConfigGroups))))
  (update-group-data)
  (-> @widgets :groups-table update-table)
  @group-data)

(defn properties [group-data]
  (sort (set (apply concat (map keys (vals group-data))))))

(defn property-name [prop-vec]
  (let [[dev prop] prop-vec]
    (str dev "-" prop)))

(defn group-table-model []
  (proxy [AbstractTableModel] []
    (getColumnName [_] "Groups")
    (getColumnCount [] 1)
    (getRowCount [] (count @groups))
    (isCellEditable [_ _] true)
    (getValueAt [row column] (nth @groups row))
    (setValueAt [val row column]
      (let [old-val (nth @groups row)]
        (core renameConfigGroup old-val val)
        (update-groups)))))

(defn preset-names-table-model []
  (proxy [AbstractTableModel] []
    (getColumnName [_] "Presets")
    (getColumnCount [] 1)
    (getRowCount [] (count (keys @group-data)))
    (isCellEditable [_ _] true)
    (getValueAt [row column] (nth (keys @group-data) row))
    (setValueAt [val row column]
      (let [old-val (.getValueAt this row column)]
        (core renameConfig (get-selected-group) old-val val)
        (update-group-data)))))

(defn presets-table-model []
  (proxy [AbstractTableModel] []
    (getColumnName [column] (property-name (nth (properties @group-data) column)))
    (getColumnCount [] (count (properties @group-data)))
    (getRowCount [] (count (keys @group-data)))
    (isCellEditable [_ _] false) ;; will be true
    (getValueAt [row column] (get (nth (vals @group-data) row)
                                  (nth (properties @group-data) column)))
    (setValueAt [val row column]
                ;(let [old-val (.getValueAt this row column)]
        ;TODO: finish
        )))

;; table row selection rules

(defn require-single-selection [table]
  (.. table getSelectionModel (setSelectionMode ListSelectionModel/SINGLE_SELECTION)))

(defn attach-selection-listener [table f]
  (let [list-selection-listener
        (reify ListSelectionListener
          (valueChanged [this event]
            (try
              (f (.getSelectedRow table))
              (catch Exception e (.printStackTrace e)))))]
      (-> table .getSelectionModel
          (.addListSelectionListener list-selection-listener))))
  
(defn set-selected-row [table row]
  (when (and (< -1 row (.getRowCount table))
             (not= (first (.getSelectedRows table)) row))   
    (.setRowSelectionInterval table row row))) 

(defn link-table-row-selection [table1 table2]
  (attach-selection-listener table1 #(set-selected-row table2 %))
  (attach-selection-listener table2 #(set-selected-row table1 %)))

(defn double-click-for-new-row [table f]
  (attach-double-click-listener
    (.getParent table)
    (fn [x y]
      (let [r (.getBounds table)]
        (when (> y (+ (.y r) (.height r)))
          (f))))))

(defn start-editing-cell [table row col]
  (edt 
    (.toFront (@widgets :frame))
    (doto table
         (.changeSelection row col false false)
         ;.requestFocus
         (.editCellAt row col)
      (.. getEditorComponent requestFocusInWindow))))


(defn redraw-data []
  (update-group-data)
  (let [preset-names-table (@widgets :preset-names-table)
        presets-table (@widgets :presets-table)]
    (update-table preset-names-table)
         (cancel-cell-editing preset-names-table)
         (cancel-cell-editing presets-table)
         (update-table presets-table)
         (set-preferred-column-width presets-table)))

(defn new-group-name []
  (loop [index 1]
    (let [guess (str "New Group " index)]
      (if ((set @groups) guess)
        (recur (inc index))
        guess))))

(defn new-group []
  (let [table  (@widgets :groups-table)
        group-name (new-group-name)]
    (core defineConfigGroup group-name)
    (swap! groups conj group-name)
    (update-table table)
    (let [row (dec (.getRowCount table))]
      (start-editing-cell table row 0))))

(defn new-preset-name [group]
  (loop [index 1]
    (let [guess (str "New Preset " index)]
      (if ((set (presets group)) guess)
        (recur (inc index))
        guess))))

(defn new-preset [group]
  (let [preset-name (new-preset-name group)
        preset (config-struct (core getConfigGroupState group))]
    (doseq [[[d p] v] preset]
      (core defineConfig group preset-name d p v))
    (redraw-data)))

(defn add-property [group dev prop]
  (when-not ((set (properties @group-data)) [dev prop])
    (let [val (core getProperty dev prop)]
      (doseq [preset (presets group)]
        (core defineConfig group preset dev prop val)))
    (redraw-data)))
    
(defn remove-property [group dev prop]
  (when ((set (properties @group-data)) [dev prop])
    (doseq [preset (presets group)]
      (core deleteConfig group preset dev prop))
    (redraw-data)))

(defn delete-group [group]
  (core deleteConfigGroup group)
  (update-groups))

(defn delete-preset [group preset]
  (core deleteConfig group preset)
  (redraw-data))

(defn show-popup-menu [popup-menu mouse-event]
  (when (.isPopupTrigger mouse-event)
    (def e mouse-event)
          (let [point (.getPoint mouse-event)
                source (.getSource mouse-event)
                row (.rowAtPoint source point)
                column (.columnAtPoint source point)]
            (set-selected-row source row)
            (.show popup-menu source (.x point) (.y point)))))

(defn context-menu-on-table [table popup-menu]
  (.addMouseListener table
    (proxy [MouseAdapter] []
      (mousePressed [e]
        (show-popup-menu popup-menu e))
      (mouseReleased [e]
        (show-popup-menu popup-menu e)))))
           
(defn cancel-cell-editing [table]
  (when-let [editor (.getCellEditor table)]
    (.cancelCellEditing editor)))
  
(defn show []
  (let [f (JFrame. "Micro-Manager Configuration Preset Editor")
        cp (.getContentPane f)
        groups-table (JTable.)
        presets-table (JTable.)
        preset-names-table (JTable.)
        groups-sp (scroll-pane groups-table)
        presets-sp (scroll-pane presets-table)
        ]
    (edt 
      (.setAutoResizeMode presets-table JTable/AUTO_RESIZE_OFF)
      (doall (map require-single-selection [groups-table presets-table preset-names-table]))
      (.. presets-table getTableHeader (setReorderingAllowed false))
      (.setRowHeaderView presets-sp preset-names-table)
      (.. preset-names-table getParent (setPreferredSize (Dimension. 150 100)))
      ;(.setFixedCellWidth preset-names-list 150)
      ;(.setFixedCellHeight preset-names-list (.getRowHeight presets-table))
      (.setBackground preset-names-table (Color. 0xE0 0xE0 0xE0))
      (.setModel groups-table (group-table-model))
      (.setModel preset-names-table (preset-names-table-model))
      (.setModel presets-table (presets-table-model))
      (link-table-row-selection presets-table preset-names-table)
      (double-click-for-new-row groups-table #(println "new row!"))
      (.setAutoResizeMode presets-table JTable/AUTO_RESIZE_OFF)
      (doto cp
        (.setLayout (SpringLayout.))
        (add-component presets-sp :n 5 :w 155 :s -5 :e -5)
        (add-component groups-sp :n 5 :w 5 :s -5 :w 150))
        (.setBounds f 100 100 800 500)
        (.show f))
    {:frame f
     :groups-table groups-table
     :presets-table presets-table
     :preset-names-table preset-names-table}))

(defn set-preferred-column-width [table]
  (->> table
       .getColumnModel .getColumns enumeration-seq
       (map #(.setPreferredWidth % 125)) dorun))

(defn activate-groups-table [components]
  (let [groups-table (components :groups-table)]
    (attach-selection-listener
      groups-table
      (fn [_] (redraw-data)))))

;; test/run

(defn start
  ([]
    (start nil))
  ([group]
    (when-let [frame (:frame @widgets)]
      (edt (try (doto frame .hide .dispose) (catch Throwable _))))
    (reset! widgets (show))
    (update-groups)
    (activate-groups-table @widgets)))
