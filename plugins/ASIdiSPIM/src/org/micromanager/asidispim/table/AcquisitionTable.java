package org.micromanager.asidispim.table;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.DropMode;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.AcquisitionPanel;
import org.micromanager.asidispim.Data.AcquisitionSettings;
import org.micromanager.asidispim.api.ASIdiSPIMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;


/**
 * The acquistion table.
 */
@SuppressWarnings("serial")
public class AcquisitionTable extends JScrollPane {
    // used to set, get, and clear MM PositionList objects
    private final ScriptInterface gui_;
    
    // column constants
    public static final int NUMBER_COLUMN = 0;
    public static final int NAME_COLUMN = 1;
    public static final int SAVE_PREFIX_COLUMN = 2;
    public static final int SAVE_DIRECTORY_COLUMN = 3;
    public static final int POSITION_LIST_COLUMN = 4;
    
    public static final String DEFAULT_ACQ_NAME = "None";
    
    private String currentAcqName_;
    private AcquisitionTableData data_;
    private AcquisitionTablePositionList lstPositions_;
        
    private final JTable table_;
    private final JFileChooser fileBrowser_;
    private final AcquisitionPanel acqPanel_;
    
    public AcquisitionTable(final ScriptInterface gui, final AcquisitionPanel acqPanel) {
        acqPanel_ = acqPanel;
        gui_ = gui;
        
        // nothing selected to start
        currentAcqName_ = DEFAULT_ACQ_NAME;

        // create the JTable with custom TableModel
        data_ = new AcquisitionTableData();
        table_ = new JTable(new AcquisitionTableModel(data_));
        table_.setFillsViewportHeight(true);
        
        // cancel JTable edits when focus is lost to prevent errors
        table_.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        
        // only select a single row at a time
        table_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // disable drag to reorder columns
        table_.getTableHeader().setReorderingAllowed(false);
        
        // enable drag and drop
        table_.setDragEnabled(true);
        table_.setDropMode(DropMode.INSERT_ROWS);
        table_.setTransferHandler(new AcquisitionTableTransferHandler(table_));
 
        // used to set save directory root
        fileBrowser_ = new JFileChooser();
        fileBrowser_.setDialogTitle("Select the save directory root...");
        fileBrowser_.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
 
        // settings that don't persist when loading JSON settings
        initTableSettings();
        
        // handle user interaction
        createEventHandlers();
        createTableModelEventHandler();
        
        // display the JTable in the JScrollPane
        setViewportView(table_);
    }
    
    /**
     * Table settings that need to be reset when loading JSON table data.
     */
    private void initTableSettings() {
        // center the # column text and keep the # column size constant
        final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        final TableColumn column = table_.getColumnModel().getColumn(NUMBER_COLUMN);
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        column.setCellRenderer(centerRenderer);
        column.setMaxWidth(20);
        column.setMinWidth(20);
        
        // Note: the TableModel is initialized when a playlist is loaded
        // so we needed to add a new TableModelListener
        createTableModelEventHandler();
    }

    /**
     * Create the event handler that handles table edit events.
     */
    private void createTableModelEventHandler() {
        // listen for table edit events
        table_.getModel().addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent event) {
                switch (event.getColumn()) {
                    case NAME_COLUMN:
                        final String oldName = currentAcqName_;
                        final String newName = table_.getValueAt(event.getFirstRow(), NAME_COLUMN).toString();
                        if (!oldName.equals(newName)) {
                            currentAcqName_ = newName;
                            data_.renameAcquisitionSettings(oldName, newName);
                            //System.out.println("rename: " + oldName + " => " + newName);
                        }
                        break;
                    case SAVE_PREFIX_COLUMN:
                        final String prefix = table_.getValueAt(event.getFirstRow(), SAVE_PREFIX_COLUMN).toString();
                        acqPanel_.setAcquisitionNamePrefix(prefix);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    /**
     * Handle user table row selection and browsing to save directory roots.
     */
    private void createEventHandlers() {
        // listen for selection events
        table_.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                // save current settings in AcquisitionTableData
                if (!currentAcqName_.equals(DEFAULT_ACQ_NAME)) {
                    data_.updateAcquisitionSettings(currentAcqName_, acqPanel_.getCurrentAcquisitionSettings());
                    //System.out.println("SAVING ACQ: " + currentAcqName);
                }
                // set the current acquisition name
                final int index = table_.getSelectedRow();
                if (index != -1) {
                    currentAcqName_ = table_.getValueAt(index, NAME_COLUMN).toString();
                } else {
                    currentAcqName_ = DEFAULT_ACQ_NAME;
                }
                changeAcquisitionSettings(index);
            }
        });
        
        // open a file browser when double clicking the save directory column
        table_.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                // double click with the left mouse button
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    final int row = table_.rowAtPoint(event.getPoint());
                    final int col = table_.columnAtPoint(event.getPoint());
                    if (row >= 0 && col == SAVE_DIRECTORY_COLUMN) {
                        // System.out.println("mouseClicked => row: " + row + " col: " + col);
                        if (fileBrowser_.showOpenDialog(table_) == JFileChooser.APPROVE_OPTION) {
                            final String root = fileBrowser_.getSelectedFile().toString();
                            table_.setValueAt(root, row, col);
                            try {
                                acqPanel_.setSavingDirectoryRoot(root);
                            } catch (ASIdiSPIMException e) {
                                ReportingUtils.showError("could not set the directory root!");
                            }
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Change the settings using the metadata list to get the acquisition name, which
     * is then used to lookup the AcquisitionSettings and PositionList objects.
     * 
     * @param index the index in the metadata list
     */
    public void changeAcquisitionSettings(final int index) {
        if (index != -1) {
            // change to new acquisition settings
            final AcquisitionMetadata metadata = data_.getMetadataByIndex(index);
            final AcquisitionSettings settings = data_.getAcquisitionSettings(metadata.getAcquisitionName());
            acqPanel_.setAcquisitionSettings(settings);
            // change the PositionList if one is selected
            final String positionListName = metadata.getPositionListName();
            if (!positionListName.equals(AcquisitionTablePositionList.NO_POSITION_LIST)) {
                // the ListSelectionListener of the AcquisitionTablePositionList handles
                // switching the position list when setSelectedValue is called
                lstPositions_.setSelectedValue(positionListName, true);
            } else {
                // clear PositionList when no position list is assigned
                lstPositions_.setSelectedValue(AcquisitionTablePositionList.NO_POSITION_LIST, true);
            }
        }
    }
    
    /**
     * Sets the position list ui component for the acquisition table to control.
     * 
     * @param lstPositions the position list selector
     */
    public void setPositionListControls(final AcquisitionTablePositionList lstPositions) {
        this.lstPositions_ = lstPositions;
    }
    
    /**
     * Sets the table data to the new data set.<P>
     * 
     * Used to load JSON settings.
     * 
     * @param data the new table data
     */
    public void setTableData(final AcquisitionTableData data) {
        this.data_ = data;
        table_.setModel(new AcquisitionTableModel(this.data_));
        initTableSettings();
    }

    /**
     * Set all table entries with the name selected to the default value.
     * 
     * @param text the name of the position list to unselect
     */
    public void unselectPositionListName(final String name) {
        final int size = table_.getRowCount();
        for (int i = 0; i < size; i++) {
            final String selected = table_.getValueAt(i, POSITION_LIST_COLUMN).toString();
            if (selected.equals(name)) {
                table_.setValueAt(AcquisitionTablePositionList.NO_POSITION_LIST, i, POSITION_LIST_COLUMN);
            }
        }
    }
    
    /**
     * Updates the selected position list name in the AcquisitionTable when renaming a PositionList.
     * 
     * @param oldName the original name
     * @param newName the new name
     */
    public void updateSelectedPositionListName(final String oldName, final String newName) {
        final int size = table_.getRowCount();
        for (int i = 0; i < size; i++) {
            final String selected = table_.getValueAt(i, POSITION_LIST_COLUMN).toString();
            if (selected.equals(oldName)) {
                table_.setValueAt(newName, i, POSITION_LIST_COLUMN);
            }
        }
    }
    
    /**
     * Adds a new PositionList and updates the combo box with new items in the table.
     * 
     * @param name the unique name of the position list
     * @param names the new list of items for the combo box
     */
    public void addPositionList(final String name, final String[] names) {
        final PositionList positionList = getMMPositionList();
        data_.addPositionList(name, positionList);
    }
    
    /**
     * Removes the PositionList and updates the AcquisitionTable to set any selected 
     * position lists with that name in the table to the default value.
     * 
     * @param name the unique name of the position list
     */
    public void removePositionList(final String name) {
        data_.removePositionList(name);
        unselectPositionListName(name);
    }
    
    public void renamePositionList(final String oldName, final String newName) {
        data_.renamePositionList(oldName, newName);
        updateSelectedPositionListName(oldName, newName);
    }
    
    public void addAcquisitionSettings(final String name, final AcquisitionSettings acqSettings) {
        data_.addAcquisitionSettings(name, acqSettings);
        revalidate(); // updates JScrollBar when adding elements
        table_.repaint();
    }
    
    // TODO: currentAcqName = ACQ_NAME_DEFAULT; before selection method... find a better way?
    public void removeSelectedAcquisition() {
        // nothing to remove
        if (data_.getMetadataList().size() == 0) {
            return;
        }
        // remove the acquisition
        final int index = table_.getSelectedRow();
        if (index != -1) { // no row selected
            final String name = table_.getValueAt(index, 1).toString();
            data_.removeAcquisitionSettings(name, index);
            if (index-1 >= 0) {
                currentAcqName_ = DEFAULT_ACQ_NAME; // TODO: is this correct? maybe use name?
                table_.setRowSelectionInterval(index-1, index-1);
            } else { // TODO: select forward if there are still items in acqs? (remove first item with others in table)
                currentAcqName_ = DEFAULT_ACQ_NAME;
                table_.clearSelection();
            }
            table_.repaint();
            System.out.println("remove acquisition: " + name);
        }
    }
    
    /**
     * Returns a copy of the current Micro-Manager PositionList.
     * 
     * @return a copy of the current PositionList
     */
    public PositionList getMMPositionList() {
        PositionList positionList = null;
        try {
            positionList = gui_.getPositionList();
        } catch (MMScriptException e) {
            ReportingUtils.showError("could not get position list!");
        }
        // newInstance because we want an actual copy not a reference
        return PositionList.newInstance(positionList);
        // return positionList;
    }
    
    /**
     * Sets the current Micro-Manager PositionList to the argument.
     * 
     * @param positionList the new position list
     */
    public void setMMPositionList(final PositionList positionList) {
        try {
            gui_.setPositionList(positionList);
        } catch (MMScriptException e) {
            ReportingUtils.showError("could not set the position list!");
        }
    }
    
    /**
     * Clears all positions in the current Micro-Manager PositionList 
     * by setting the PositionList to an empty PositionList object.
     */
    public void clearMMPositionList() {
        setMMPositionList(new PositionList());
    }
    
    /**
     * Clears all data in the acquisition table, items in the position list, 
     * the current Micro-Manager PositionList, and resets currentAcqName to 
     * the default value. This is used when loading settings.
     */
    public void clearData() {
        currentAcqName_ = DEFAULT_ACQ_NAME;
        lstPositions_.clearSelectionAndItems();
        data_.clearAllData();
        clearMMPositionList();
        repaint();
    }
   
    /**
     * Updates the AcquisitionSettings with a new save directory.<P>
     * Called from the AcquisitionPanel text field event handler for save directory.
     * 
     * @param name the new save directory
     */
    public void updateSaveDirectoryRoot(final String name) {
        //System.out.println("currentAcq: " + currentAcq);
        final AcquisitionSettings settings = data_.getAcquisitionSettings(currentAcqName_);
        if (settings != null) {
            settings.saveDirectoryRoot = name;
            repaint();
        }
    }
    
    /**
     * Updates the AcquisitionSettings with a new save name prefix.<P>
     * Called from the AcquisitionPanel text field event handler for save name prefix.
     * 
     * @param name the new save name prefix
     */
    public void updateSaveNamePrefix(final String name) {
        final AcquisitionSettings settings = data_.getAcquisitionSettings(currentAcqName_);
        if (settings != null) {
            settings.saveNamePrefix = name;
            repaint();
        }
    }
    
    /**
     * Returns true if the name is valid and contains only  
     * characters, digits, and underscores.
     * 
     * @param name the String to check
     * @return true if the String is valid
     */
    public static boolean isNameValid(final String name) {
        final int length = name.length();
        for (int i = 0; i < length; i++) {
            if (!Character.isLetterOrDigit(name.charAt(i))) {
                if (name.charAt(i) == '_') {
                    continue;
                }
                return false;
            }
        }
        return true;
    }
    /**
     * Returns the index of the selected row.
     * 
     * @return the index of the selected row
     */
    public int getSelectedRow() {
        return table_.getSelectedRow();
    }
    
    /**
     * Clear the selection and reset currentAcqName 
     * because nothing is selected. 
     */
    public void clearSelection() {
        currentAcqName_ = DEFAULT_ACQ_NAME;
        table_.clearSelection();
    }
    
    /**
     * Returns the currently selected acquisition name.
     * 
     * @return the currently selected acquisition name
     */
    public String getCurrentAcqName() {
        return currentAcqName_;
    }
    
    /**
     * Returns the acquisition table data.
     * 
     * @return the acquisition table data
     */
    public AcquisitionTableData getTableData() {
        return data_;
    }

    /**
     * Returns the acquisition panel.
     * 
     * @return the acquisition panel
     */
    public AcquisitionPanel getAcqPanel() {
        return acqPanel_;    
    }
    
    /**
     * Returns the internal JTable object.
     * 
     * @return the internal table
     */
    public JTable getTable() {
        return table_;
    }
    
}
