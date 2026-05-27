package org.micromanager.internal.utils;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Objects;
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

   // True only after the user picks an item from the open dropdown.
   // getCellEditorValue() returns the original value when false, making any
   // external stopCellEditing() call (e.g. Tab) a no-op commit.
   private boolean selectionMade_ = false;

   public StatePresetCellEditor() {
      super();

      combo_.addPopupMenuListener(new PopupMenuListener() {
         @Override
         public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            combo_.putClientProperty("popupOpen", Boolean.TRUE);
         }

         @Override
         public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            combo_.putClientProperty("popupOpen", null);
         }

         @Override
         public void popupMenuCanceled(PopupMenuEvent e) {
            // Popup was dismissed without a selection (Escape or click outside).
            // Clear the flag and cancel the cell edit in one step so the user
            // does not need a second Escape/click to dismiss the editor too.
            combo_.putClientProperty("popupOpen", null);
            fireEditingCanceled();
         }
      });

      combo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (Boolean.TRUE.equals(combo_.getClientProperty("popupOpen"))) {
               selectionMade_ = true;
               fireEditingStopped();
            }
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
      selectionMade_ = false;
      combo_.putClientProperty("popupOpen", null);
      combo_.removeAllItems();
      for (int i = 0; i < allowed.length; i++) {
         combo_.addItem(allowed[i]);
      }
      // setSelectedItem silently fails when item_.config is not in the list
      // (e.g. "" when no preset matches). Fall back to no selection (-1) so
      // the combo shows blank rather than defaulting to item 0.
      combo_.setSelectedItem(item_.config);
      if (!Objects.equals(item_.config, combo_.getSelectedItem())) {
         combo_.setSelectedIndex(-1);
      }
   }

   // This method is called when editing is completed.
   // It must return the new value to be stored in the cell.
   @Override
   public Object getCellEditorValue() {
      if (item_.allowed.length == 0) {
         return text_.getText();
      } else if (item_.allowed.length == 1) {
         if (item_.singleProp && item_.hasLimits) {
            return slider_.getText();
         } else if (item_.singlePropAllowed != null && item_.singlePropAllowed.length == 0) {
            return text_.getText();
         } else {
            return selectionMade_ ? combo_.getSelectedItem() : item_.config;
         }
      } else {
         return selectionMade_ ? combo_.getSelectedItem() : item_.config;
      }
   }
}
