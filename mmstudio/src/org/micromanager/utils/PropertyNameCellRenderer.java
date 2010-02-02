/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.micromanager.api.MMPropertyTableModel;

/**
 *
 * @author arthur
 */
public class PropertyNameCellRenderer implements TableCellRenderer {

    PropertyItem item_;

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int column) {
        MMPropertyTableModel data = (MMPropertyTableModel) table.getModel();
        item_ = data.getPropertyItem(rowIndex);
        JLabel lab = new JLabel();
        lab.setOpaque(true);
        lab.setHorizontalAlignment(JLabel.LEFT);
        lab.setText((String) value);

        if (item_.readOnly) {
			//comp.setForeground(Color.DARK_GRAY);
			lab.setBackground(Color.LIGHT_GRAY);
		} else {
			//comp.setForeground(Color.BLACK);
			lab.setBackground(Color.WHITE);
		}    
        return lab;
    }

    // The following methods override the defaults for performance reasons
    public void validate() {}
    public void revalidate() {}
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
}
