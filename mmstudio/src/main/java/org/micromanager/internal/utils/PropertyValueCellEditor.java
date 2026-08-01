package org.micromanager.internal.utils;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.Objects;
import javax.swing.AbstractCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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
   // Set for the remainder of the current AWT event right after we close the
   // popup via setPopupVisible(false), then cleared on the next EDT cycle.
   // See setPopupVisible() below for why this exists.
   private boolean justClosedPopup_ = false;

   JComboBox<String> combo_ = new JComboBox<String>() {
      @Override
      public void setPopupVisible(boolean visible) {
         if (visible) {
            // Clicking the combo box's own arrow/button while its popup is
            // already open can trigger a same-click close-then-reopen race:
            // MenuSelectionManager sees that click as landing outside the
            // popup and closes it, then the arrow button's own toggle
            // handler (processing the same click right after) sees the
            // now-closed state and calls setPopupVisible(true) again,
            // reopening it. The popup ends up glitched/stuck instead of
            // cleanly closed. Ignore "become visible" requests while
            // already visible, or immediately after we just closed it
            // ourselves within this same event, to break that race.
            if (isPopupVisible() || justClosedPopup_) {
               return;
            }
         } else if (isPopupVisible()) {
            justClosedPopup_ = true;
            SwingUtilities.invokeLater(() -> justClosedPopup_ = false);
         }
         super.setPopupVisible(visible);
      }
   };
   SliderPanel slider_ = new SliderPanel();

   PropertyItem item_;

   public boolean disableExcluded_;

   // True only after the user picks an item from the open dropdown.
   // getCellEditorValue() returns the original value when false, making any
   // external stopCellEditing() call (e.g. Tab) a no-op commit.
   private boolean selectionMade_ = false;


   public PropertyValueCellEditor() {
      this(false);
   }

   public PropertyValueCellEditor(boolean disableExcluded) {
      super();

      disableExcluded_ = disableExcluded;

      // Tells the combo box's UI delegate it is being used as a table cell
      // editor, so its built-in Enter/Escape key handling (e.g. confirming
      // the highlighted item and handing off to the table) behaves as
      // javax.swing.DefaultCellEditor's JComboBox constructor sets it up.
      combo_.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);

      combo_.addPopupMenuListener(new PopupMenuListener() {
         @Override
         public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            combo_.putClientProperty("popupOpen", Boolean.TRUE);
         }

         @Override
         public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            boolean wasOpen = Boolean.TRUE.equals(combo_.getClientProperty("popupOpen"));
            combo_.putClientProperty("popupOpen", null);
            // JComboBox only fires an ActionEvent when the selection actually
            // changes. If the user reopens the popup and clicks the item that
            // was already selected, the popup closes normally here but no
            // ActionEvent ever fires, leaving the cell editor stuck open
            // (shown with the "editing" look) until something else forces a
            // table event. Treat that case as an implicit re-confirmation.
            if (wasOpen && !selectionMade_) {
               selectionMade_ = true;
               // This listener fires *during* the popup's own hide()
               // teardown (unlike the ActionListener below, which fires
               // before hide() begins). Committing synchronously here runs
               // our table-mutating code interleaved with that teardown,
               // which can leave the popup visually stuck mid-collapse.
               // Defer to the next EDT cycle so hide() finishes first, and
               // go through stopCellEditing() (not fireEditingStopped()
               // directly) so a popup that got spuriously reopened by the
               // race above is forced closed before the editor is removed.
               SwingUtilities.invokeLater(() -> stopCellEditing());
            }
         }

         @Override
         public void popupMenuCanceled(PopupMenuEvent e) {
            combo_.putClientProperty("popupOpen", null);
            cancelCellEditing();
         }
      });

      combo_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // Remember any real value change (mouse or arrow keys) so a later
            // commit reflects it, but only commit *now* when the popup is
            // open: clicking an item there is itself the confirm gesture.
            // Arrow-key browsing with the popup closed should not commit on
            // every keystroke; JTable's own Enter/Tab/focus-loss handling
            // will call stopCellEditing() when the user actually confirms.
            if (!Objects.equals(combo_.getSelectedItem(), item_.value)) {
               selectionMade_ = true;
            }
            if (Boolean.TRUE.equals(combo_.getClientProperty("popupOpen"))) {
               stopCellEditing();
            }
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

   /**
    * Closes the dropdown's popup, if open, before actually stopping or
    * canceling the edit.
    *
    * <p>External callers (e.g. a dialog's OK button) may call
    * {@code stopCellEditing()} on the active editor programmatically. If the
    * combo box's popup is still open at that point, removing the editor out
    * from under it leaves the popup as a visual orphan, and the table can
    * end up attaching a leftover editing state to the wrong row if its
    * structure changes shortly after. Hiding the popup first ensures it
    * closes through the normal PopupMenuListener path before that happens.
    */
   @Override
   public boolean stopCellEditing() {
      if (combo_.isPopupVisible()) {
         combo_.hidePopup();
      }
      return super.stopCellEditing();
   }

   @Override
   public void cancelCellEditing() {
      if (combo_.isPopupVisible()) {
         combo_.hidePopup();
      }
      super.cancelCellEditing();
   }

   /**
    * Selects {@code value} in {@code combo} if it is actually present among
    * the combo's items, otherwise clears the selection.
    *
    * <p>{@code JComboBox.setSelectedItem()}/{@code getSelectedItem()} is not
    * a reliable presence check: {@code DefaultComboBoxModel} stores whatever
    * object is passed in as the selected item regardless of whether it is
    * one of the combo's actual items, so a value absent from the list would
    * otherwise appear to "stick" instead of falling back to no selection.
    */
   private static void selectItemOrClear(JComboBox<String> combo, String value) {
      for (int i = 0; i < combo.getItemCount(); i++) {
         if (Objects.equals(combo.getItemAt(i), value)) {
            combo.setSelectedItem(value);
            return;
         }
      }
      combo.setSelectedIndex(-1);
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
            combo_.putClientProperty("popupOpen", null);
            combo_.removeAllItems();
            for (int i = 0; i < item_.allowed.length; i++) {
               combo_.addItem(item_.allowed[i]);
            }
            selectItemOrClear(combo_, item_.value);
            // Reset only now, after the item-list rebuild above has settled:
            // removeAllItems() drops the selection to null and can fire a
            // spurious ActionEvent (selectedItem=null) that our own
            // ActionListener would otherwise misread as a real user change,
            // incorrectly marking selectionMade_ true before any real
            // interaction happens. That poisoned flag then silently defeats
            // the popupMenuWillBecomeInvisible reselect-same-item commit
            // further down, leaving the cell editor stuck open indefinitely.
            selectionMade_ = false;
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
         return selectionMade_ ? combo_.getSelectedItem() : item_.value;
      }
   }
}
