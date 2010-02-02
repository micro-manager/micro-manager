/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import java.awt.Component;
import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author arthur
 */
public class PropertyUsageCellRenderer implements TableCellRenderer {
    PropertyItem item_;

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int column) {
        PropertyTableData data = (PropertyTableData)table.getModel();
		item_ = data.getPropertyItem(rowIndex);

        JCheckBox cb = new JCheckBox();
			cb.setSelected(item_.confInclude);
			//((AbstractTableModel) table.getModel()).fireTableCellUpdated(rowIndex, column);
			if (item_.readOnly)
				cb.setEnabled(false);
		return (Component) cb;
    }

    
    // The following methods override the defaults for performance reasons
	public void validate() {}
	public void revalidate() {}
	protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
	public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
