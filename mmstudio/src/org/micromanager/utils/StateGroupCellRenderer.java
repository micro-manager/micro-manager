
package org.micromanager.utils;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.micromanager.ConfigGroupPad;

/**
 *
 * @author arthur
 */
/**
 * Rendering element for the property table.
 *
 */
public class StateGroupCellRenderer extends PropertyValueCellRenderer {

    private static final long serialVersionUID = 1L;
    // This method is called each time a cell in a column
    // using this renderer needs to be rendered.
    StateItem stateItem_;

   @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

        // https://stackoverflow.com/a/3055930
        if (value == null) {
           return null;
        }

        ConfigGroupPad.StateTableData data = (ConfigGroupPad.StateTableData) table.getModel();

        stateItem_ = data.getPropertyItem(rowIndex);

        Component comp;


        JLabel label = new JLabel();
        label.setOpaque(true);
        label.setFont(new Font("Arial", Font.BOLD, 11));
        label.setText((String) value);
        label.setToolTipText(stateItem_.descr);
        label.setHorizontalAlignment(JLabel.LEFT);
        comp = label;

        
        if (isSelected) {
            comp.setBackground(Color.LIGHT_GRAY);
        } else {
            comp.setBackground(Color.WHITE);
        }

        return comp;
    }
      // The following methods override the defaults for performance reasons
   @Override
      public void validate(){}
   @Override
      public void revalidate(){}
   @Override
      protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
   @Override
      public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}

   }
