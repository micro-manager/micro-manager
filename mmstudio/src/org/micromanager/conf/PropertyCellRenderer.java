///////////////////////////////////////////////////////////////////////////////
//FILE:          MMConfigFileException.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id$
//
package org.micromanager.conf;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Displays table cell data in some of the wizard pages.
 *
 */
public class PropertyCellRenderer extends DefaultTableCellRenderer {
   // This method is called each time a cell in a column
   // using this renderer needs to be rendered.
   Property item_;
   
   public Component getTableCellRendererComponent(JTable table, Object value,
         boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {
      Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIndex, colIndex);
      
      PropertyTableModel data = (PropertyTableModel)table.getModel();
      item_ = data.getPropertyItem(rowIndex);
      
      if (isSelected) {
         cell.setBackground(Color.gray);
      } else
         cell.setBackground(Color.white);
      
      JLabel lab = (JLabel) cell;
      if (colIndex == 0) {
         lab.setText((String)value);
         lab.setHorizontalAlignment(JLabel.LEFT);
      } else if (colIndex == 1) {
         lab.setText((String) (value));
         lab.setHorizontalAlignment(JLabel.LEFT);
      } else if (colIndex == 2){
         lab.setText((String) (value));
         lab.setHorizontalAlignment(JLabel.LEFT);
      } else {
         lab.setText("Undefined");
      }
      
      return lab;
   }
   
   // The following methods override the defaults for performance reasons
   public void validate() {}
   public void revalidate() {}
   protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
   public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
   public PropertyCellRenderer() {
      super();
   }
}
