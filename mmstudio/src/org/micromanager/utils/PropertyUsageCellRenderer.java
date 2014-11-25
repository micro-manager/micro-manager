

package org.micromanager.utils;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author arthur
 */
public class PropertyUsageCellRenderer implements TableCellRenderer {

   PropertyItem item_;
   JCheckBox cb_ = new JCheckBox();

   @Override
   public Component getTableCellRendererComponent(JTable table, Object value, 
           boolean isSelected, boolean hasFocus, int rowIndex, int column) {
      PropertyTableData data = (PropertyTableData) table.getModel();
      item_ = data.getPropertyItem(rowIndex);

      cb_.setSelected(item_.confInclude);
      cb_.setBackground(Color.white);
      if (item_.readOnly) {
         cb_.setEnabled(false);
      }
      return (Component) cb_;
   }

    
    // The following methods override the defaults for performance reasons
	public void validate() {}
	public void revalidate() {}
	protected void firePropertyChange(String propertyName, Object oldValue, 
           Object newValue) {}
	public void firePropertyChange(String propertyName, boolean oldValue, 
           boolean newValue) {}
}
