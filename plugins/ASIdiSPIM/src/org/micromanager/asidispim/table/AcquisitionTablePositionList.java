package org.micromanager.asidispim.table;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.micromanager.api.PositionList;
import org.micromanager.asidispim.AcquisitionPanel;

/**
 * This class manages the PositionList selector in the multi-acquisition window.
 * <P>
 * Most of the complexity is in the ListSelectionListener event handler and how it  
 * interacts with the rest of the system.
 */
@SuppressWarnings("serial")
public class AcquisitionTablePositionList extends JScrollPane {

    public static final String NO_POSITION_LIST = "None";
    
    private String selectedName_;
    private String prevSelectedName_;
    
    private final JList list_;
    private final DefaultListModel model_;
    private final AcquisitionTable table_;
    
    public AcquisitionTablePositionList(final AcquisitionTable acqTable) {
        table_ = acqTable;
        model_ = new DefaultListModel();
        list_ = new JList(model_);
        
        // set selection name defaults
        selectedName_ = NO_POSITION_LIST;
        prevSelectedName_ = NO_POSITION_LIST;
        
        // the default value should always be in the list
        model_.addElement(NO_POSITION_LIST);
        
        // handle list selection
        createEventHandlers();
        
        // display the list in the scroll pane
        setViewportView(list_);
    }
    
    /**
     * Clears the current selection and resets selectedName and
     * prevSelectedName to their default values. Also removes all 
     * items from the list model.
     * <P>
     * Note: This gets used when clearing all data to load new
     * acquisition settings from a JSON file.
     */
    public void clearSelectionAndItems() {
        selectedName_ = NO_POSITION_LIST;
        prevSelectedName_ = NO_POSITION_LIST;
        list_.clearSelection();
        model_.clear();
    }
    
    /**
     * Clears the current selection and resets selectedName and
     * prevSelectedName to their default values.
     */
    public void clearSelectionAndReset() {
        selectedName_ = NO_POSITION_LIST;
        prevSelectedName_ = NO_POSITION_LIST;
        list_.clearSelection();
    }
    
    /**
     * Returns the number of items in the list.
     * 
     * @return the number of items
     */
    public int getNumItems() {
        return model_.size();
    }
    
    /**
     * Adds an item to the list.
     * 
     * @param item the item name
     */
    public void addItem(final String item) {
        model_.addElement(item);
    }
    
    /**
     * Removes an item from the list.
     * 
     * @param item the item name
     */
    public void removeItem(final String item) {
        model_.removeElement(item);
    }
    
    /**
     * Renames the oldName to the newName.
     * 
     * @param oldName the name to rename
     * @param newName the new name
     */
    public void renameItem(final String oldName, final String newName) {
        final int index = model_.indexOf(oldName);
        if (index != -1) {
            model_.remove(index);
            model_.insertElementAt(newName, index);
        }
    }
    
    /**
     * Adds a list of items to the list.
     * 
     * @param items the list of items to add
     */
    public void addItems(final String[] items) {
        model_.addElement(NO_POSITION_LIST);
        for (final String item : items) {
            model_.addElement(item);
        }
    }
    
    /**
     * Returns the list of PositionList names. These are the values 
     * in the Swing component itself, not the AcquisitionMetadata.
     * 
     * @return the list of names
     */
    public String[] getPositionListNames() {
        final int size = model_.size();
        final String[] positionNames = new String[size];
        for (int i = 0; i < size; i++) {
            positionNames[i] = model_.getElementAt(i).toString();
        }
        return positionNames;
    }
    
    /**
     * Returns true if the item is contained within the list.
     * 
     * @param item the item to search for
     * @return true if the item is present
     */
    public boolean contains(final String item) {
        return model_.contains(item);
    }
    
    /**
     * Returns the selected value as an object so that you can check for null.
     * 
     * @return the selected value or null if nothing is selected
     */
    public Object getSelectedValue() {
        return list_.getSelectedValue();
    }
    
    /**
     * Sets the selected value of the position list selector.
     * 
     * @param obj the value to set
     * @param shouldScroll true if the component should scroll
     */
    public void setSelectedValue(final Object obj, final boolean shouldScroll) {
        list_.setSelectedValue(obj, shouldScroll);
    }
    
    /**
     * Creates the listener that fires events when the user selects items from the list.
     * <P>
     * This method saves the previously selected PositionList and sets the current PositionList 
     * to the one associated with the currently selected position list name.
     * 
     */
    public void createEventHandlers() {
        // when the user selects a position list name on the JList
        list_.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                // only fire once on item selection
                if (!event.getValueIsAdjusting()) {
                    // no selection => null (exception when calling toString())
                    final Object selected = list_.getSelectedValue();
                    if (selected != null) {
                        // update the position list name in the AcquisitionTableData
                        if (table_.getSelectedRow() != -1) {
                            table_.getTableData().setMetadataPositionListName(
                                    table_.getSelectedRow(), selected.toString());
                            table_.repaint();
                        }
   
                        // update the multiple positions check box in the AcquisitionPanel
                        final AcquisitionPanel acqPanel = table_.getAcqPanel();
                        if (selected.toString().equals(NO_POSITION_LIST)) {
                            acqPanel.updateCheckBox(acqPanel.getMultiplePositionsCheckBox(), false);
                        } else {
                            acqPanel.updateCheckBox(acqPanel.getMultiplePositionsCheckBox(), true);
                        }
                        table_.getAcqPanel().repaint();
                        
                        // first selection or addition to the position lists
                        if (selectedName_.equals(NO_POSITION_LIST) 
                                && prevSelectedName_.equals(NO_POSITION_LIST)) {
                            selectedName_ = selected.toString();
                            final PositionList positionList = table_.getTableData().getPositionList(selectedName_);
                            if (positionList != null) {
                                table_.setMMPositionList(positionList);
                            }
                            prevSelectedName_ = selectedName_;
                            return; // early exit => no need to save position list
                        }
                        
                        // set current name after the "first selection" block
                        selectedName_ = selected.toString();
                        
                        // save previously selected position list
                        // this check prevents NO_POSITION_LIST_SELECTED from being added to the HashMap
                        if (!prevSelectedName_.equals(NO_POSITION_LIST)) {
                            // save the position list that was previously selected
                            final PositionList currentPositionList = table_.getMMPositionList();
                            table_.getTableData().addPositionList(prevSelectedName_, currentPositionList);
                            // load the position list associated with the new selected name
                            if (selectedName_.equals(NO_POSITION_LIST)) {
                                table_.clearMMPositionList();
                            } else {
                                final PositionList positionList = table_.getTableData().getPositionList(selectedName_);
                                if (positionList != null) {
                                    table_.setMMPositionList(positionList);
                                }
                            }
                        }

                        prevSelectedName_ = selectedName_;
                        //System.out.println("selectedName: " + selectedName + " prevSelectedName: " + prevSelectedName);
                    }
                }
            }
        });
        
    }
    
    // -----------
    // Debug Tools
    // -----------
    
    public boolean validatePositionsSize() {
        final int positionsSize = table_.getTableData().getPositionsSize();
        if (positionsSize != model_.size()) {
            System.out.println("Validate Error: The number of items in the AcquisitionTablePositionList "
                    + "should be the same as the number of keys in AcquisitionTableData positions field.");
            return false;
        } else {
            System.out.println("Validate Success: The number of positions are the same.");
        }
        return true;
    }
    
}
