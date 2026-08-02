///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com
//
// COPYRIGHT:    100X Imaging Inc, San Francisco, 2009
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

package org.micromanager.internal.utils;

import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Objects;
import javax.swing.AbstractCellEditor;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.events.ChannelGroupChangedEvent;
import org.micromanager.events.PropertyChangedEvent;
import org.micromanager.internal.MMStudio;

/**
 * /**
 * PropertyEditor provides UI for manipulating sets of autofocus properties.
 * JFrame based component for generic manipulation of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 *
 * @author Nenad Amodaj
 */
public final class AutofocusPropertyEditor extends JDialog {
   private final Studio studio_;
   private static final long serialVersionUID = 1507097881635431043L;

   private final PropertyTableData data_;
   private final PropertyCellEditor cellEditor_;
   private final JCheckBox showReadonlyCheckBox_;

   private static final String PREF_SHOW_READONLY = "show_readonly";
   private final DefaultAutofocusManager afMgr_;
   private JComboBox<String> methodCombo_;

   /**
    * Constructs the Autofocus Property Editor.
    *
    * @param studio Gives access to the API of the current MM instance
    * @param afmgr  We presumably need access to functions not available through the API
    */
   public AutofocusPropertyEditor(Studio studio, DefaultAutofocusManager afmgr) {
      super();
      studio_ = studio;
      afMgr_ = afmgr;
      setModal(false);
      data_ = new PropertyTableData();
      JTable table = new DaytimeNighttime.Table();
      table.setAutoCreateColumnsFromModel(false);
      table.setModel(data_);

      cellEditor_ = new PropertyCellEditor();
      PropertyCellRenderer renderer = new PropertyCellRenderer(studio);

      for (int k = 0; k < data_.getColumnCount(); k++) {
         TableColumn column = new TableColumn(k, 200, renderer, cellEditor_);
         table.addColumn(column);
      }

      SpringLayout springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setSize(551, 514);
      final UserProfile profile = MMStudio.getInstance().profile();
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            cleanup();
         }

         @Override
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            showReadonlyCheckBox_.setSelected(
                  profile.getSettings(AutofocusPropertyEditor.class).getBoolean(
                        PREF_SHOW_READONLY, true));
            data_.updateStatus();
            data_.fireTableStructureChanged();
         }
      });
      setTitle("Autofocus properties");

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setBounds(100, 100, 400, 300);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);

      JScrollPane scrollPane = new JScrollPane();
      scrollPane.setFont(new Font("Arial", Font.PLAIN, 10));
      scrollPane.setBorder(new BevelBorder(BevelBorder.LOWERED));
      getContentPane().add(scrollPane);
      springLayout
            .putConstraint(SpringLayout.SOUTH, scrollPane, -5,
                  SpringLayout.SOUTH, getContentPane());
      springLayout
            .putConstraint(SpringLayout.NORTH, scrollPane, 70,
                  SpringLayout.NORTH, getContentPane());
      springLayout
            .putConstraint(SpringLayout.EAST, scrollPane, -5,
                  SpringLayout.EAST, getContentPane());
      springLayout
            .putConstraint(SpringLayout.WEST, scrollPane, 5,
                  SpringLayout.WEST, getContentPane());

      scrollPane.setViewportView(table);

      table = new DaytimeNighttime.Table();
      table.setAutoCreateColumnsFromModel(false);

      final JButton refreshButton = new JButton();
      springLayout
            .putConstraint(SpringLayout.NORTH, refreshButton, 10,
                  SpringLayout.NORTH, getContentPane());
      springLayout
            .putConstraint(SpringLayout.WEST, refreshButton, 10,
                  SpringLayout.WEST, getContentPane());
      springLayout
            .putConstraint(SpringLayout.SOUTH, refreshButton, 33,
                  SpringLayout.NORTH, getContentPane());
      springLayout
            .putConstraint(SpringLayout.EAST, refreshButton, 110,
                  SpringLayout.WEST, getContentPane());
      URL resource = getClass().getResource("/org/micromanager/icons/arrow_refresh.png");
      if (resource != null) {
         refreshButton.setIcon(new ImageIcon(resource));
      }
      refreshButton.setFont(new Font("Arial", Font.PLAIN, 10));
      getContentPane().add(refreshButton);
      refreshButton.addActionListener(e -> refresh());
      refreshButton.setText("Refresh! ");

      showReadonlyCheckBox_ = new JCheckBox();
      springLayout.putConstraint(SpringLayout.NORTH, showReadonlyCheckBox_, 41,
            SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, showReadonlyCheckBox_, 10,
            SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, showReadonlyCheckBox_, 64,
            SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, showReadonlyCheckBox_, 183,
            SpringLayout.WEST, getContentPane());
      showReadonlyCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      showReadonlyCheckBox_.addActionListener(e -> {
         // show/hide read-only properties
         data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
         data_.updateStatus();
         data_.fireTableStructureChanged();
      });
      showReadonlyCheckBox_.setText("Show read-only properties");
      getContentPane().add(showReadonlyCheckBox_);

      // restore values from the previous session
      showReadonlyCheckBox_.setSelected(profile.getSettings(
            AutofocusPropertyEditor.class).getBoolean(PREF_SHOW_READONLY, true));

      JButton btnClose = new JButton("Close");
      btnClose.addActionListener(arg0 -> {
         cleanup();
         dispose();
      });
      springLayout
            .putConstraint(SpringLayout.SOUTH, btnClose, 0,
                  SpringLayout.SOUTH, refreshButton);
      springLayout
            .putConstraint(SpringLayout.EAST, btnClose, -10,
                  SpringLayout.EAST, getContentPane());
      getContentPane().add(btnClose);

      if (afMgr_ != null) {
         methodCombo_ = new JComboBox<>();
         String[] afDevs = afMgr_.getAfDevices();
         for (String devName : afDevs) {
            methodCombo_.addItem(devName);
         }
         if (afMgr_.getAutofocusMethod() != null) {
            methodCombo_.setSelectedItem(afMgr_.getAutofocusMethod().getName());
         }
         methodCombo_.addActionListener(
               arg0 -> changeAFMethod((String) methodCombo_.getSelectedItem()));
         springLayout
               .putConstraint(SpringLayout.WEST, methodCombo_, 80, SpringLayout.EAST,
                     refreshButton);
         springLayout
               .putConstraint(SpringLayout.SOUTH, methodCombo_, 0, SpringLayout.SOUTH,
                     refreshButton);
         springLayout.putConstraint(SpringLayout.EAST, methodCombo_, -6, SpringLayout.WEST,
               btnClose);
         getContentPane().add(methodCombo_);
      }

      data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
      studio_.events().registerForEvents(this);
   }

   protected void changeAFMethod(String focusDev) {
      cellEditor_.stopEditing();
      methodCombo_.setSelectedItem(focusDev);
      afMgr_.setAutofocusMethodByName(focusDev);

      updateStatus();
   }

   protected void refresh() {
      data_.refresh();
   }

   /**
    * Reconstructs the UI.
    */
   public void rebuild() {
      ActionListener l = methodCombo_.getActionListeners()[0];

      try {
         if (l != null) {
            methodCombo_.removeActionListener(l);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      methodCombo_.removeAllItems();
      String[] afDevs = afMgr_.getAfDevices();
      for (String devName : afDevs) {
         methodCombo_.addItem(devName);
      }
      methodCombo_.addActionListener(arg0 -> changeAFMethod(
            (String) methodCombo_.getSelectedItem()));
      if (afMgr_.getAutofocusMethod() != null) {
         methodCombo_.setSelectedItem(afMgr_.getAutofocusMethod().getName());
      }
   }

   /**
    * Updates the UI with the current autofocus device and its properties.
    */
   public void updateStatus() {
      if (data_ != null) {
         data_.updateStatus();
      }
   }

   /**
    * Handles Property Changed Event for autofocus properties.
    * This enables automatic updates when properties change externally.
    *
    * @param event Holds information about the Property that changed.
    */
   @Subscribe
   public void onPropertyChanged(PropertyChangedEvent event) {
      String device = event.getDevice();
      String property = event.getProperty();
      String value = event.getValue();

      // Only update if this is an autofocus device property
      if (afMgr_ != null && afMgr_.getAutofocusMethod() != null) {
         String afDeviceName = afMgr_.getAutofocusMethod().getName();
         if (device.equals(afDeviceName)) {
            data_.updateProperty(property, value);
         }
      }
   }

   private void handleException(Exception e) {
      ReportingUtils.showError(e, this);
   }


   /**
    * Saves settings to profile, so they can be restored in the next session.
    */
   public void cleanup() {
      studio_.profile().getSettings(AutofocusPropertyEditor.class)
            .putBoolean(PREF_SHOW_READONLY, showReadonlyCheckBox_.isSelected());
      if (afMgr_ != null) {
         if (afMgr_.getAutofocusMethod() != null) {
            afMgr_.getAutofocusMethod().applySettings();
            afMgr_.getAutofocusMethod().saveSettings();
         }
      }
   }


   /**
    * Property table data model, representing MMCore data.
    */
   final class PropertyTableData extends AbstractTableModel {
      private static final long serialVersionUID = 1L;

      public final String[] columnNames_ = {
            "Property",
            "Value",
      };

      ArrayList<PropertyItem> propList_ = new ArrayList<>();
      private boolean showReadOnly_ = true;

      public PropertyTableData() {
         updateStatus();
      }

      public void setShowReadOnly(boolean show) {
         showReadOnly_ = show;
      }

      @Override
      public int getRowCount() {
         return propList_.size();
      }

      @Override
      public int getColumnCount() {
         return columnNames_.length;
      }

      public PropertyItem getPropertyItem(int row) {
         return propList_.get(row);
      }

      @Override
      public Object getValueAt(int row, int col) {

         PropertyItem item = propList_.get(row);
         if (col == 0) {
            return item.device + "-" + item.name;
         } else if (col == 1) {
            return item.value;
         }

         return null;
      }

      @Override
      public void setValueAt(Object value, int row, int col) {
         PropertyItem item = propList_.get(row);
         if (col == 1 && afMgr_.getAutofocusMethod() != null) {
            try {
               if (item.isInteger()) {
                  afMgr_.getAutofocusMethod()
                        .setPropertyValue(item.name, NumberUtils.intStringDisplayToCore(value));
               } else if (item.isFloat()) {
                  afMgr_.getAutofocusMethod()
                        .setPropertyValue(item.name, NumberUtils.doubleStringDisplayToCore(value));
               } else {
                  afMgr_.getAutofocusMethod().setPropertyValue(item.name, value.toString());
               }

               // For C++ autofocus devices, PropertyChangedEvent will be triggered automatically
               // For Java autofocus plugins, manually update the value since they don't trigger
               // events
               item.value = value.toString();
               fireTableCellUpdated(row, col);

               afMgr_.getAutofocusMethod().applySettings();
               afMgr_.getAutofocusMethod().saveSettings();
            } catch (Exception e) {
               handleException(e);
            }
         }
      }

      @Override
      public String getColumnName(int column) {
         return columnNames_[column];
      }

      @Override
      public boolean isCellEditable(int nRow, int nCol) {
         if (nCol == 1) {
            return !propList_.get(nRow).readOnly;
         } else {
            return false;
         }
      }

      public void refresh() {
         if (afMgr_.getAutofocusMethod() == null) {
            return;
         }

         try {
            for (PropertyItem item : propList_) {
               item.value = afMgr_.getAutofocusMethod().getPropertyValue(item.name);
            }
            this.fireTableDataChanged();
         } catch (Exception e) {
            handleException(e);
         }
      }

      /**
       * Updates a single property value without refreshing the entire table.
       * This is called in response to PropertyChangedEvent callbacks.
       *
       * @param propertyName Name of the property that changed
       * @param newValue New value of the property
       */
      public void updateProperty(String propertyName, String newValue) {
         // Find the property in the list and update it
         for (int i = 0; i < propList_.size(); i++) {
            PropertyItem item = propList_.get(i);
            if (item.name.equals(propertyName)) {
               item.value = newValue;
               fireTableCellUpdated(i, 1); // Update value column (column 1)
               return;
            }
         }
      }

      public void updateStatus() {
         propList_.clear();
         PropertyItem[] properties = new PropertyItem[0];

         if (afMgr_.getAutofocusMethod() != null) {
            properties = afMgr_.getAutofocusMethod().getProperties();
         }

         for (PropertyItem property : properties) {
            if (!property.preInit) {
               if (showReadOnly_ || !property.readOnly) {
                  propList_.add(property);
               }
            }
         }
         this.fireTableStructureChanged();
      }

   }

   /**
    * This method is called when the channel group changes.
    * It updates the property table data to reflect the new state.
    *
    * @param event The event containing information about the channel group change.
    */
   @Subscribe
   public void onChannelGroupChanged(ChannelGroupChangedEvent event) {
      data_.updateStatus();
   }

   /**
    * Cell editing using either JTextField or JComboBox depending on whether the
    * property enforces a set of allowed values.
    */
   public final class PropertyCellEditor extends AbstractCellEditor implements TableCellEditor {
      private static final long serialVersionUID = 1L;
      // This is the component that will handle the editing of the cell value
      JTextField text_ = new JTextField();
      // Set for the remainder of the current AWT event right after we close
      // the popup via setPopupVisible(false), then cleared on the next EDT
      // cycle. See setPopupVisible() below for why this exists.
      private boolean justClosedPopup_ = false;

      JComboBox<String> combo_ = new JComboBox<String>() {
         @Override
         public void setPopupVisible(boolean visible) {
            if (visible) {
               // Clicking the combo box's own arrow/button while its popup
               // is already open can trigger a same-click close-then-reopen
               // race: MenuSelectionManager sees that click as landing
               // outside the popup and closes it, then the arrow button's
               // own toggle handler (processing the same click right after)
               // sees the now-closed state and calls setPopupVisible(true)
               // again, reopening it. The popup ends up glitched/stuck
               // instead of cleanly closed. Ignore "become visible"
               // requests while already visible, or immediately after we
               // just closed it ourselves within this same event, to break
               // that race.
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
      JCheckBox check_ = new JCheckBox();
      SliderPanel slider_ = new SliderPanel();
      int editingCol_;
      PropertyItem item_;
      // True only after the user picks an item from the open dropdown.
      // getCellEditorValue() returns the original value when false, making any
      // external stopCellEditing() call (e.g. Tab) a no-op commit.
      private boolean selectionMade_ = false;

      /**
       * Component that edits the cell's value.
       */
      public PropertyCellEditor() {
         super();
         check_.addActionListener(e -> fireEditingStopped());

         // Tells the combo box's UI delegate it is being used as a table
         // cell editor, so its built-in Enter/Escape key handling (e.g.
         // confirming the highlighted item and handing off to the table)
         // behaves as javax.swing.DefaultCellEditor's JComboBox constructor
         // sets it up.
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
                  // before hide() begins). Committing synchronously here
                  // runs our table-mutating code interleaved with that
                  // teardown, which can leave the popup visually stuck
                  // mid-collapse. Defer to the next EDT cycle so hide()
                  // finishes first, and go through stopCellEditing() (not
                  // fireEditingStopped() directly) so a popup that got
                  // spuriously reopened by the race above is forced closed
                  // before the editor is removed.
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
               // Remember any real value change (mouse or arrow keys) so a
               // later commit reflects it, but only commit *now* when the
               // popup is open: clicking an item there is itself the confirm
               // gesture. Arrow-key browsing with the popup closed should
               // not commit on every keystroke; JTable's own Enter/Tab/
               // focus-loss handling will call stopCellEditing() when the
               // user actually confirms.
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
      }

      public void stopEditing() {
         stopCellEditing();
      }

      /**
       * Closes the dropdown's popup, if open, before actually stopping or
       * canceling the edit.
       *
       * <p>External callers (e.g. {@link #stopEditing()}, invoked when the
       * autofocus method changes) may call {@code stopCellEditing()} on the
       * active editor programmatically. If the combo box's popup is still
       * open at that point, removing the editor out from under it leaves
       * the popup as a visual orphan, and the table can end up attaching a
       * leftover editing state to the wrong row if its structure changes
       * shortly after. Hiding the popup first ensures it closes through the
       * normal PopupMenuListener path before that happens.
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

      // This method is called when a cell value is edited by the user.
      @Override
      public Component getTableCellEditorComponent(JTable table, Object value,
                                                   boolean isSelected, int rowIndex, int colIndex) {

         editingCol_ = colIndex;

         PropertyTableData data = (PropertyTableData) table.getModel();
         item_ = data.getPropertyItem(rowIndex);
         // Configure the component with the specified value

         if (colIndex == 1) {
            if (item_.allowed.length == 0) {
               if (item_.hasRange) {
                  if (item_.isInteger()) {
                     slider_.setLimits((int) item_.lowerLimit, (int) item_.upperLimit);
                  }  else {
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
            }

            combo_.putClientProperty("popupOpen", null);
            combo_.removeAllItems();
            for (String allowed : item_.allowed) {
               combo_.addItem(allowed);
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
         } else if (colIndex == 2) {
            return check_;
         }
         return null;
      }

      /**
       * Selects {@code value} in {@code combo} if it is actually present
       * among the combo's items, otherwise clears the selection.
       *
       * <p>{@code JComboBox.setSelectedItem()}/{@code getSelectedItem()} is
       * not a reliable presence check: {@code DefaultComboBoxModel} stores
       * whatever object is passed in as the selected item regardless of
       * whether it is one of the combo's actual items, so a value absent
       * from the list would otherwise appear to "stick" instead of falling
       * back to no selection.
       */
      private void selectItemOrClear(JComboBox<String> combo, String value) {
         for (int i = 0; i < combo.getItemCount(); i++) {
            if (Objects.equals(combo.getItemAt(i), value)) {
               combo.setSelectedItem(value);
               return;
            }
         }
         combo.setSelectedIndex(-1);
      }

      // This method is called when editing is completed.
      // It must return the new value to be stored in the cell.
      @Override
      public Object getCellEditorValue() {
         if (editingCol_ == 1) {
            if (item_.allowed.length == 0) {
               if (item_.hasRange) {
                  return slider_.getText();
               } else {
                  return text_.getText();
               }
            } else {
               return selectionMade_ ? combo_.getSelectedItem() : item_.value;
            }
         } else {
            if (editingCol_ == 2) {
               return check_;
            }
         }

         return null;
      }
   }

   /**
    * Cell rendering for the device property table.
    */
   public final class PropertyCellRenderer implements TableCellRenderer {
      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      PropertyItem item_;
      Studio studio_;

      public PropertyCellRenderer(Studio studio) {
         super();
         studio_ = studio;
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus,
                                                     int rowIndex, int colIndex) {

         PropertyTableData data = (PropertyTableData) table.getModel();
         item_ = data.getPropertyItem(rowIndex);

         Component comp;

         if (colIndex == 0) {
            JLabel lab = new JLabel();
            lab.setText((String) value);
            lab.setOpaque(true);
            lab.setHorizontalAlignment(JLabel.LEFT);
            comp = lab;
         } else if (colIndex == 1) {
            if (item_.hasRange) {
               SliderPanel slider = new SliderPanel();
               slider.setLimits(item_.lowerLimit, item_.upperLimit);
               try {
                  slider.setText((String) value);
               } catch (ParseException ex) {
                  ReportingUtils.logError(ex);
               }
               slider.setToolTipText((String) value);
               comp = slider;
            } else {
               JLabel lab = new JLabel();
               lab.setOpaque(true);
               lab.setText(item_.value);
               lab.setHorizontalAlignment(JLabel.LEFT);
               comp = lab;
            }
         } else {
            comp = new JLabel("Undefinded");
         }

         if (item_.readOnly) {
            comp.setBackground(studio_.app().skin().getDisabledBackgroundColor());
            comp.setForeground(studio_.app().skin().getDisabledTextColor());
         } else {
            comp.setBackground(studio_.app().skin().getBackgroundColor());
            comp.setForeground(studio_.app().skin().getEnabledTextColor());
         }
         return comp;
      }

      // The following methods override the defaults for performance reasons
      public void validate() {
      }

      public void revalidate() {
      }

   }
}

