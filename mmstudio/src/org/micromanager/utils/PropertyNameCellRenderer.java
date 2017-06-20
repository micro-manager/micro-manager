///////////////////////////////////////////////////////////////////////////////
//FILE:          PropertyNameCellRenderer.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.utils;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

/**
 *
 * @author arthur
 */
public class PropertyNameCellRenderer implements TableCellRenderer {

    PropertyItem item_;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int column) {

       // https://stackoverflow.com/a/3055930
       if (value == null) {
          return null;
       }

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
