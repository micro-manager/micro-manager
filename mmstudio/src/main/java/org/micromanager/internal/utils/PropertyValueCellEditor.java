package org.micromanager.internal.utils;

import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.TableCellEditor;

/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 */
public final class PropertyValueCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = 1L;
   // This is the component that will handle the editing of the cell value
   JTextField text_ = new JTextField();
   JComboBox<String> combo_ = new JComboBox<>();
   SliderPanel slider_ = new SliderPanel();

   PropertyItem item_;

   public boolean disableExcluded_;


   public PropertyValueCellEditor() {
      this(false);
   }

   public PropertyValueCellEditor(boolean disableExcluded) {
      super();

      disableExcluded_ = disableExcluded;

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

      slider_.addEditActionListener(e -> fireEditingStopped());

      slider_.addSliderMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            fireEditingStopped();
         }
      });

      text_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
               fireEditingStopped();
            }
         }
      });

      text_.addFocusListener(new FocusAdapter() {
         @Override
         public void focusLost(FocusEvent e) {
            // fireEditingStopped();
         }

      });
   }

   // This method is called when a cell value is edited by the user.
   @Override
   public Component getTableCellEditorComponent(JTable table, Object value,
                                                boolean isSelected, int rowIndex, int colIndex) {

      MMPropertyTableModel data = (MMPropertyTableModel) table.getModel();
      item_ = data.getPropertyItem(rowIndex);

      // Configure the component with the specified value:
      if (item_.confInclude || !disableExcluded_) {
         if (item_.allowed.length == 0) {
            if (item_.hasRange) {
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
            } else {
               text_.setText((String) value);
               return text_;
            }
         } else {
            combo_.removeAllItems();
            for (int i = 0; i < item_.allowed.length; i++) {
               combo_.addItem(item_.allowed[i]);
            }
            combo_.setSelectedItem(item_.value);
            return combo_;
         }
      } else {
         return null;
      }

   }

   // This method is called when editing is completed.
   // It must return the new value to be stored in the cell.
   @Override
   public Object getCellEditorValue() {
      if (item_.allowed.length == 0) {
         if (item_.hasRange) {
            return slider_.getText();
         } else {
            return text_.getText();
         }
      } else {
         return combo_.getSelectedItem();
      }
   }
}
