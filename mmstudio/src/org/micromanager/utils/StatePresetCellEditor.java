
package org.micromanager.utils;

import java.awt.Component;
import java.awt.event.ActionEvent;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;


import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.Arrays;
import javax.swing.JTable;
import org.micromanager.ConfigGroupPad.StateTableData;

/**
 *
 * @author arthur
 */
////////////////////////////////////////////////////////////////////////////
/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 */
public class StatePresetCellEditor extends AbstractCellEditor implements TableCellEditor {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
// This is the component that will handle the editing of the cell value
    JTextField text_ = new JTextField();
    JComboBox combo_ = new JComboBox();
    StateItem item_;
    SliderPanel slider_ = new SliderPanel();

    public StatePresetCellEditor() {
        super();
        slider_.addEditActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                fireEditingStopped();
            }
        });

        slider_.addSliderMouseListener(new MouseAdapter() {

         @Override
            public void mouseReleased(MouseEvent e) {
                fireEditingStopped();
            }
        });
    }

// This method is called when a cell value is edited by the user.
    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int rowIndex, int vColIndex) {

        // https://stackoverflow.com/a/3055930
        if (value == null) {
           return null;
        }

        if (isSelected) {
            // cell (and perhaps other cells) are selected
        }

        StateTableData data = (StateTableData) table.getModel();
        item_ = data.getPropertyItem(rowIndex);
        // Configure the component with the specified value

        if (item_.allowed.length == 0) {
            text_.setText((String) value);
            return text_;
        }

        if (item_.allowed.length == 1) {
            if (item_.singleProp) {
                if (item_.hasLimits) {
                    // slider editing
                    if (item_.isInteger()) {
                        slider_.setLimits((int) item_.lowerLimit, (int) item_.upperLimit);
                    } else {
                        slider_.setLimits(item_.lowerLimit, item_.upperLimit);
                    }
                   try {
                      slider_.setText((String) value);
                   } catch (ParseException ex) {
                      ReportingUtils.logError(ex);
                   }
                    return slider_;

                } else if (item_.singlePropAllowed != null && item_.singlePropAllowed.length > 0) {
                    setComboBox(item_.allowed);
                    return combo_;
                } else {
                    text_.setText((String) value);
                    return text_;
                }
            }
        }


        if( 1 < item_.allowed.length ){
           boolean allNumeric2 = true;
           // test that first character of every possible value is a numeral
           // if so, show user the list sorted by the numeric prefix
           for (int k = 0; k < item_.allowed.length; k++) {
              if (item_.allowed[k].length() > 0 && !Character.isDigit(item_.allowed[k].charAt(0))) {
                allNumeric2 = false;
                break;
              }
           }
           if (allNumeric2) {
            Arrays.sort(item_.allowed, new SortFunctionObjects.NumericPrefixStringComp());
           }
           else{
        	Arrays.sort(item_.allowed);	
           }
        }

        setComboBox(item_.allowed);

        // Return the configured component
        return combo_;
    }

    private void setComboBox(String[] allowed) {
        // remove old listeners
        ActionListener[] l = combo_.getActionListeners();
        for (int i = 0; i < l.length; i++) {
            combo_.removeActionListener(l[i]);
        }
        combo_.removeAllItems();
        for (int i = 0; i < allowed.length; i++) {
            combo_.addItem(allowed[i]);
        }

        // remove old items
        combo_.removeAllItems();

        // add new items
        for (int i = 0; i < allowed.length; i++) {
            combo_.addItem(allowed[i]);
        }
        combo_.setSelectedItem(item_.config);

        // end editing on selection change
        combo_.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                fireEditingStopped();
            }
        });

    }

// This method is called when editing is completed.
// It must return the new value to be stored in the cell.
    @Override
    public Object getCellEditorValue() {
        if (item_.allowed.length == 1) {
            if (item_.singleProp && item_.hasLimits) {
                return slider_.getText();
            } else if (item_.singlePropAllowed != null && item_.singlePropAllowed.length == 0) {
                return text_.getText();
            } else {
                return combo_.getSelectedItem();
            }
        } else {
            return combo_.getSelectedItem();
        }
    }
}
