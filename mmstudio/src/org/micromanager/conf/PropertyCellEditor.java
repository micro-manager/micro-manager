///////////////////////////////////////////////////////////////////////////////
//FILE:          PropertyCellEditor.java
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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;

/**
 * In-place table editor for string cells.
 *
 */
public class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
   // This is the component that will handle the editing of the cell value
   JTextField text_ = new JTextField();
   JComboBox combo_ = new JComboBox();
   int editingCol_;
   Property item_;
   
   public PropertyCellEditor() {
      super();
   }
   
   // This method is called when a cell value is edited by the user.
   public Component getTableCellEditorComponent(JTable table, Object value,
         boolean isSelected, int rowIndex, int colIndex) {
      
      editingCol_ = colIndex;
               
      PropertyTableModel data = (PropertyTableModel)table.getModel();
      item_ = data.getPropertyItem(rowIndex);
      
      // Configure the component with the specified value
      
      if (colIndex == 2) {
         if (item_.allowedValues_.length == 0) {
            text_.setText((String)value);
            return text_;
         }
      
         ActionListener[] l = combo_.getActionListeners();
         for (int i=0; i<l.length; i++)
            combo_.removeActionListener(l[i]);
         combo_.removeAllItems();
         for (int i=0; i<item_.allowedValues_.length; i++){
            combo_.addItem(item_.allowedValues_[i]);
         }
         combo_.setSelectedItem(item_.value_);
         
         // end editing on selection change
         combo_.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               fireEditingStopped();
            }
         });
                    
         return combo_;
      }
      return null;
   }
   
   // This method is called when editing is completed.
   // It must return the new value to be stored in the cell.
   public Object getCellEditorValue() {
      if (editingCol_ == 2) {
         if (item_.allowedValues_.length == 0)
            return text_.getText();
         else
            return combo_.getSelectedItem();
      }
      return null;
   }
}
