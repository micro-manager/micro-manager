package org.micromanager.internal.dialogs;

import java.awt.Component;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import javax.swing.AbstractCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.TableCellEditor;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Cell editing using either JTextField or JComboBox depending on whether the
 * property enforces a set of allowed values.
 */
public final class ChannelCellEditor extends AbstractCellEditor implements TableCellEditor {

   private static final long serialVersionUID = -8374637422965302637L;
   private final JTextField text_ = new JTextField();
   // Set for the remainder of the current AWT event right after we close the
   // popup via setPopupVisible(false), then cleared on the next EDT cycle.
   // See setPopupVisible() below for why this exists.
   private boolean justClosedPopup_ = false;

   private final JComboBox<String> channelSelect_ = new JComboBox<String>() {
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
   private final JCheckBox checkBox_ = new JCheckBox();
   boolean checkBoxValue_ = false;
   private final JLabel colorLabel_ = new JLabel();
   private int editCol_ = -1;
   private ChannelSpec channel_ = null;

   private final CheckBoxChangeListener checkBoxChangeListener_;

   // True only after the user picks an item from the open dropdown.
   // getCellEditorValue() returns the original value when false, making any
   // external stopCellEditing() call a no-op.
   private boolean selectionMade_ = false;

   public ChannelCellEditor() {
      checkBoxChangeListener_ = new CheckBoxChangeListener(this);

      // Tells the combo box's UI delegate it is being used as a table cell
      // editor, so its built-in Enter/Escape key handling (e.g. confirming
      // the highlighted item and handing off to the table) behaves as
      // javax.swing.DefaultCellEditor's JComboBox constructor sets it up.
      channelSelect_.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);

      channelSelect_.addPopupMenuListener(new PopupMenuListener() {
         @Override
         public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            channelSelect_.putClientProperty("popupOpen", Boolean.TRUE);
         }

         @Override
         public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            boolean wasOpen = Boolean.TRUE.equals(channelSelect_.getClientProperty("popupOpen"));
            channelSelect_.putClientProperty("popupOpen", null);
            // JComboBox only fires an ActionEvent when the selection actually
            // changes. If the user reopens the popup and clicks the item that
            // was already selected, the popup closes normally here but no
            // ActionEvent ever fires, leaving the cell editor stuck open.
            // Treat that case as an implicit re-confirmation.
            if (wasOpen && !selectionMade_) {
               selectionMade_ = true;
               // This listener fires *during* the popup's own hide()
               // teardown (unlike the ActionListener above, which fires
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
            channelSelect_.putClientProperty("popupOpen", null);
            cancelCellEditing();
         }
      });

      channelSelect_.addActionListener(e -> {
         // Remember any real value change (mouse or arrow keys) so a later
         // commit reflects it, but only commit *now* when the popup is open:
         // clicking an item there is itself the confirm gesture. Arrow-key
         // browsing with the popup closed should not commit on every
         // keystroke; JTable's own Enter/Tab/focus-loss handling will call
         // stopCellEditing() when the user actually confirms.
         if (!Objects.equals(channelSelect_.getSelectedItem(), channel_.config())) {
            selectionMade_ = true;
         }
         if (Boolean.TRUE.equals(channelSelect_.getClientProperty("popupOpen"))) {
            stopCellEditing();
         }
      });
   }

   /**
    * Closes the Configuration dropdown's popup, if open, before actually
    * stopping or canceling the edit.
    *
    * <p>Callers such as {@code applySettingsFromGUI()} may call
    * {@code stopCellEditing()} on the active editor programmatically (e.g.
    * right before the Up/Down/Remove buttons reorder the underlying rows).
    * If the combo box's popup is still open at that point, removing the
    * editor out from under it leaves the popup as a visual orphan, and the
    * table can end up attaching a leftover editing state to the wrong row
    * after the reorder — the same symptom as the original stuck-dropdown
    * bug. Hiding the popup first ensures it closes through the normal
    * PopupMenuListener path before the row data underneath it changes.
    */
   @Override
   public boolean stopCellEditing() {
      if (channelSelect_.isPopupVisible()) {
         channelSelect_.hidePopup();
      }
      return super.stopCellEditing();
   }

   @Override
   public void cancelCellEditing() {
      if (channelSelect_.isPopupVisible()) {
         channelSelect_.hidePopup();
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

      ChannelTableModel model = (ChannelTableModel) table.getModel();
      ArrayList<ChannelSpec> channels = model.getChannels();
      channel_ = channels.get(rowIndex);

      colIndex = table.convertColumnIndexToModel(colIndex);

      // Configure the component with the specified value
      editCol_ = colIndex;
      if (colIndex == 0) {
         checkBox_.removeChangeListener(checkBoxChangeListener_);
         checkBoxValue_ = (Boolean) value;
         checkBox_.setSelected(checkBoxValue_);
         checkBox_.addChangeListener(checkBoxChangeListener_);
         return checkBox_;
      } else if (colIndex == 2 || colIndex == 3) {
         // exposure and z offset
         text_.setText(NumberUtils.doubleToDisplayString((Double) value));
         return text_;
      } else if (colIndex == 4) {
         checkBox_.removeChangeListener(checkBoxChangeListener_);
         checkBox_.addChangeListener(checkBoxChangeListener_);
         checkBox_.setSelected((Boolean) value);
         return checkBox_;
      } else if (colIndex == 5) {
         // skip
         text_.setText(NumberUtils.intToDisplayString((Integer) value));
         return text_;
      } else if (colIndex == 1) {
         channelSelect_.putClientProperty("popupOpen", null);
         channelSelect_.removeAllItems();

         // Only allow channels that aren't already selected in a different
         // row.
         HashSet<String> usedChannels = new HashSet<>();
         for (int i = 0; i < model.getChannels().size(); ++i) {
            if (i != rowIndex) {
               usedChannels.add((String) model.getValueAt(i, 1));
            }
         }
         String[] configs = model.getAvailableChannels();
         for (String config : configs) {
            if (!usedChannels.contains(config)) {
               channelSelect_.addItem(config);
            }
         }
         selectItemOrClear(channelSelect_, channel_.config());
         // Reset only now, after the item-list rebuild above has settled:
         // removeAllItems() drops the selection to null and can fire a
         // spurious ActionEvent (selectedItem=null) that our own
         // ActionListener would otherwise misread as a real user change,
         // incorrectly marking selectionMade_ true before any real
         // interaction happens. That poisoned flag then silently defeats
         // the popupMenuWillBecomeInvisible reselect-same-item commit further
         // down, leaving the cell editor stuck open indefinitely.
         selectionMade_ = false;

         // Return the configured component
         return channelSelect_;
      } else {
         // ColorEditor takes care of this
         return colorLabel_;
      }
   }

   /**
    * This method is called when editing is completed.
    * It must return the new value to be stored in the cell.
    */
   @Override
   public Object getCellEditorValue() {
      // TODO: if content of column does not match type we get an exception
      try {
         if (editCol_ == 0) {
            return checkBox_.isSelected();
         } else if (editCol_ == 1) {
            return selectionMade_ ? channelSelect_.getSelectedItem() : channel_.config();
         } else if (editCol_ == 2 || editCol_ == 3) {
            return NumberUtils.displayStringToDouble(text_.getText());
         } else if (editCol_ == 4) {
            return checkBox_.isSelected();
         } else if (editCol_ == 5) {
            return NumberUtils.displayStringToInt(text_.getText());
         } else if (editCol_ == 6) {
            return colorLabel_.getBackground();
         } else {
            return "Internal error: unknown column";
         }
      } catch (ParseException p) {
         ReportingUtils.showError(p);
      }
      return "Internal error: unknown column";
   }

   private class CheckBoxChangeListener implements ChangeListener {
      private final ChannelCellEditor cce_;

      public CheckBoxChangeListener(ChannelCellEditor cce) {
         cce_ = cce;
      }

      @Override
      public void stateChanged(ChangeEvent e) {
         if (checkBox_.isSelected() != checkBoxValue_) {
            cce_.fireEditingStopped();
            // avoid calling fireEditingStopped multiple times:
            checkBoxValue_ = checkBox_.isSelected();
         }
      }
   }
}
