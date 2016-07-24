///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 20, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006, 2014
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

package org.micromanager.internal;

/**
 * @author Nenad Amodaj
 * PropertyEditor provides UI for manipulating sets of device properties
 */

import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.ImageIcon;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import net.miginfocom.swing.MigLayout;

import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.events.PropertyChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.Studio;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.PropertyValueCellEditor;
import org.micromanager.internal.utils.PropertyValueCellRenderer;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.PropertyTableData;
import org.micromanager.internal.utils.ShowFlags;
import org.micromanager.internal.utils.ShowFlagsPanel;
import org.micromanager.internal.utils.PropertyNameCellRenderer;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * JFrame based component for generic manipulation of device properties.
 * Represents the entire system state as a list of triplets:
 * device - property - value
 *
 * aka the "Device/Property Browser"
 */
public class PropertyEditor extends MMFrame {
   private static final long serialVersionUID = 1507097881635431043L;

   private JTable table_;
   private PropertyEditorTableData data_;
   private ShowFlags flags_;

   private static final String PREF_SHOW_READONLY = "show_readonly";
   private JCheckBox showCamerasCheckBox_;
   private JCheckBox showShuttersCheckBox_;
   private JCheckBox showStagesCheckBox_;
   private JCheckBox showStateDevicesCheckBox_;
   private JCheckBox showOtherCheckBox_;
   private JCheckBox showReadonlyCheckBox_;
   private JScrollPane scrollPane_;
   private Studio studio_;
   private CMMCore core_;

   public PropertyEditor(Studio studio) {
      super("property editor");

      studio_ = studio;
      studio_.events().registerForEvents(this);
      core_ = studio_.core();

      flags_ = new ShowFlags();
      flags_.load(PropertyEditor.class);

      createTable();
      createComponents();
   }

   private void createTable() {
      data_ = new PropertyEditorTableData(
            core_, "", "", 1, 2, getContentPane());
      data_.gui_ = studio_;
      data_.flags_ = flags_;
      data_.showUnused_ = true;
      data_.setColumnNames("Property", "Value", "");

      table_ = new DaytimeNighttime.Table();
      table_.setAutoCreateColumnsFromModel(false);
      table_.setModel(data_);

      table_.addColumn(new TableColumn(0, 200, new PropertyNameCellRenderer(), null));
      table_.addColumn(new TableColumn(1, 200, new PropertyValueCellRenderer(false), new PropertyValueCellEditor(false)));
   }

   private void createComponents() {
      final DefaultUserProfile profile = DefaultUserProfile.getInstance();

      setIconImage(Toolkit.getDefaultToolkit().getImage(
              getClass().getResource("/org/micromanager/icons/microscope.gif") ) );

      setMinimumSize(new Dimension(400, 400));
      setLayout(new MigLayout("fill, insets 2"));

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            profile.setBoolean(PropertyEditor.class, PREF_SHOW_READONLY,
               showReadonlyCheckBox_.isSelected());
            flags_.save(PropertyEditor.class);
         }
         @Override
         public void windowOpened(WindowEvent e) {
            // restore values from the previous session
            showReadonlyCheckBox_.setSelected(
               profile.getBoolean(PropertyEditor.class,
                  PREF_SHOW_READONLY, true));
            data_.update(false);
            data_.fireTableStructureChanged();
        }
      });
      setTitle("Device Property Browser");

      loadAndRestorePosition(100, 100, 400, 300);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      add(new ShowFlagsPanel(data_, flags_, core_,
               core_.getSystemStateCache()),
            "split 2, flowy");

      Font defaultFont = new Font("Arial", Font.PLAIN, 10);

      showReadonlyCheckBox_ = new JCheckBox("Show Read-Only Properties");
      showReadonlyCheckBox_.setFont(defaultFont);
      showReadonlyCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            // show/hide read-only properties
            data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
            data_.update(false);
            data_.fireTableStructureChanged();
          }
      });
      // restore values from the previous session
      showReadonlyCheckBox_.setSelected(profile.getBoolean(
               PropertyEditor.class, PREF_SHOW_READONLY, true));
      data_.setShowReadOnly(showReadonlyCheckBox_.isSelected());
      add(showReadonlyCheckBox_);

      final JButton refreshButton = new JButton("Refresh",
            new ImageIcon(getClass().getResource(
              "/org/micromanager/icons/arrow_refresh.png")));
      refreshButton.setFont(defaultFont);
      refreshButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            refresh();
         }
      });
      add(refreshButton, "width 100!, alignx left, aligny top, gaptop 20, gapbottom push, wrap");

      scrollPane_ = new JScrollPane();
      scrollPane_.setViewportView(table_);
      scrollPane_.setFont(defaultFont);
      scrollPane_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      add(scrollPane_, "span, grow, push, wrap");
   }

   protected void refresh() {
      data_.gui_ = studio_;
      data_.flags_ = flags_;
      data_.showUnused_ = true;
      data_.refresh(false);
   }

   @Subscribe
   public void onPropertiesChanged(PropertiesChangedEvent event) {
      // avoid re-executing a refresh because of callbacks while we are
      // updating
      if (!data_.updating()) {
         refresh();
      }
   }

   @Subscribe
   public void onPropertyChanged(PropertyChangedEvent event) {
      String device = event.getDevice();
      String property = event.getProperty();
      String value = event.getValue();
      data_.update(device, property, value);
   }

   /**
    * Manually save now; if we wait until the program actually exits, then
    * the profile will be done finalizing and our settings won't get saved.
    */
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      flags_.save(PropertyEditor.class);
   }

    public class PropertyEditorTableData extends PropertyTableData {
      public PropertyEditorTableData(CMMCore core, String groupName, String presetName,
         int PropertyValueColumn, int PropertyUsedColumn, Component parentComponent) {

         super(core, groupName, presetName, PropertyValueColumn, PropertyUsedColumn, false);
      }

      private static final long serialVersionUID = 1L;

      @Override
      public void setValueAt(Object value, int row, int col) {
         PropertyItem item = propListVisible_.get(row);
         studio_.logs().logMessage("Setting value " + value + " at row " + row);
         if (col == PropertyValueColumn_) {
            setValueInCore(item,value);
         }
         core_.updateSystemStateCache();
         refresh(true);
         studio_.app().refreshGUIFromCache();
         fireTableCellUpdated(row, col);
      }

      public void update (String device, String propName, String newValue) {
         PropertyItem item = getItem(device, propName);
         if (item != null) {
            item.value = newValue;
            // Better to call fireTableCellUpdated(row, col)???
            fireTableDataChanged();
         }
      }

      @Override
      public void update(ShowFlags flags, String groupName, String presetName, boolean fromCache) {
         try {
            StrVector devices = core_.getLoadedDevices();
            propList_.clear();

            if (!fromCache) {
               // Some properties may not be readable if we are
               // mid-acquisition.
               studio_.live().setSuspended(true);
            }
            for (int i = 0; i < devices.size(); i++) {
               if (data_.showDevice(flags, devices.get(i))) {
                  StrVector properties = core_.getDevicePropertyNames(devices.get(i));
                  for (int j = 0; j < properties.size(); j++){
                     PropertyItem item = new PropertyItem();
                     item.readFromCore(core_, devices.get(i), properties.get(j), fromCache);

                     if ((!item.readOnly || showReadOnly_) && !item.preInit) {
                        propList_.add(item);
                     }
                  }
               }
            }

            updateRowVisibility(flags);

            if (!fromCache) {
               studio_.live().setSuspended(false);
            }
         } catch (Exception e) {
            ReportingUtils.showError(e, "Error updating Device Property Browser");
         }
         this.fireTableStructureChanged();

      }
   }
}

