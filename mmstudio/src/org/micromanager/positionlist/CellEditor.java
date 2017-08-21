package org.micromanager.positionlist;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;

/**
 * Editor component for the position list table
 */
public class CellEditor extends AbstractCellEditor implements TableCellEditor, 
        FocusListener {
   private static final long serialVersionUID = 3L;
   // This is the component that will handle editing of the cell's value
   JTextField text_ = new JTextField();
   int editingCol_;

   public CellEditor(Font editingFont) {
      super();
      text_.setFont(editingFont);
   }
   
   public void addListener() {
      text_.addFocusListener(this);
   }

   @Override
   public void focusLost(FocusEvent e) {
      fireEditingStopped();
   }

   @Override
   public void focusGained(FocusEvent e) {

   }

   // This method is called when a cell value is edited by the user.
   @Override
   public Component getTableCellEditorComponent(JTable table, Object value,
         boolean isSelected, int rowIndex, int colIndex) {

      // https://stackoverflow.com/a/3055930
      if (value == null) {
         return null;
      }

     editingCol_ = colIndex;

      // Configure the component with the specified value
      if (colIndex == 0) {
         text_.setText((String)value);
         return text_;
      }

      return null;
   }
                                                                          
   // This method is called when editing is completed.
   // It must return the new value to be stored in the cell. 
   @Override
   public Object getCellEditorValue() {
      if (editingCol_ == 0) {
            return text_.getText();
      }
      return null;
   }
}
