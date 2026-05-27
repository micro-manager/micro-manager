package org.micromanager.internal.utils;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.Arrays;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.TableCellEditor;
import org.micromanager.internal.ConfigGroupPad.StateTableData;

/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 *
 * @author arthur
 */
public final class StatePresetCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = 1L;
   // This is the component that will handle the editing of the cell value
   JTextField text_ = new JTextField();
   JComboBox<String> combo_ = new JComboBox<>();
   StateItem item_;
   SliderPanel slider_ = new SliderPanel();

   public StatePresetCellEditor() {
      super();

      // Commit only when the user picks a *different* value from the dropdown.
      // ActionListener fires spuriously on focus loss, so we use PopupMenuListener
      // instead: snapshot the selection when the popup opens, then compare on close.
      combo_.addPopupMenuListener(new PopupMenuListener() {
         private Object itemOnOpen_ = null;

         @Override
         public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            itemOnOpen_ = combo_.getSelectedItem();
         }

         @Override
         public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            // popupMenuCanceled fires first on Escape (on most L&Fs), so by the
            // time we arrive here itemOnOpen_ has been nulled and we do nothing.
            if (itemOnOpen_ != null && !itemOnOpen_.equals(combo_.getSelectedItem())) {
               itemOnOpen_ = null;
               fireEditingStopped();
            } else {
               itemOnOpen_ = null;
               fireEditingCanceled();
            }
         }

         @Override
         public void popupMenuCanceled(PopupMenuEvent e) {
            itemOnOpen_ = null;
            fireEditingCanceled();
         }
      });

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


      if (1 < item_.allowed.length) {
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
         } else {
            Arrays.sort(item_.allowed);
         }
      }

      setComboBox(item_.allowed);

      // Return the configured component
      return combo_;
   }

   private void setComboBox(String[] allowed) {
      combo_.removeAllItems();
      for (int i = 0; i < allowed.length; i++) {
         combo_.addItem(allowed[i]);
      }
      combo_.setSelectedItem(item_.config);
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
