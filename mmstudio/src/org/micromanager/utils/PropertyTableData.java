package org.micromanager.utils;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;
import mmcorej.PropertySetting;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;

/**
 * Property table data model, representing MMCore data
 */
public class PropertyTableData extends AbstractTableModel implements MMPropertyTableModel {

   private static final long serialVersionUID = -5582899855072387637L;
   int PropertyNameColumn_;
   protected int PropertyValueColumn_;
   int PropertyUsedColumn_;
   public boolean disabled = false;
   public String groupName_;
   public String presetName_;
   public ShowFlags flags_;
   public ScriptInterface gui_;
   public boolean showUnused_;
   protected boolean showReadOnly_;
   String[] columnNames_ = new String[3];
   public ArrayList<PropertyItem> propList_ = new ArrayList<PropertyItem>(); // The table data is stored in here.
   public ArrayList<PropertyItem> propListVisible_ = new ArrayList<PropertyItem>(); // The table data is stored in here.
   protected CMMCore core_ = null;
   Configuration groupData_[];
   PropertySetting groupSignature_[];
   private String[] presetNames_;
   private volatile boolean updating_;
   private boolean groupOnly_;

   /**
    * PropertyTableData constructor
    *
    * @param core
    * @param groupName
    * @param presetName
    * @param PropertyValueColumn
    * @param PropertyUsedColumn
    * @param groupOnly - indicates that only properties included in the group
    * should be read
    */
   public PropertyTableData(CMMCore core, String groupName, String presetName,
           int PropertyValueColumn, int PropertyUsedColumn, boolean groupOnly) {
      core_ = core;
      groupName_ = groupName;
      presetName_ = presetName;
      PropertyNameColumn_ = 0;
      PropertyValueColumn_ = PropertyValueColumn;
      PropertyUsedColumn_ = PropertyUsedColumn;
      groupOnly_ = groupOnly;
   }

   public ArrayList<PropertyItem> getProperties() {
      return propList_;
   }

   public String findMatchingPreset() {
      // find selected rows
      ArrayList<PropertyItem> selectedItems = new ArrayList<PropertyItem>();
      for (PropertyItem item : propList_) {
         if (item.confInclude) {
            selectedItems.add(item);
         }
      }

      for (int i = 0; i < groupData_.length; i++) {
         int matchCount = 0;
         for (PropertyItem selectedItem : selectedItems) {
            PropertySetting ps = new PropertySetting(selectedItem.device, selectedItem.name, selectedItem.value);
            if (groupData_[i].isSettingIncluded(ps)) {
               matchCount++;
            }
         }
         if (matchCount == selectedItems.size()) {
            return presetNames_[i];
         }
      }

      return null;
   }

   public PropertyItem getItem(String device, String propName) {
      for (PropertyItem item : propList_) {
         if ((item.device.contentEquals(device)) && (item.name.contentEquals(propName))) {
            return item;
         }
      }
      return null; // Failed to find the item.
   }

   public boolean verifyPresetSignature() {
      return true;
   }

   public void deleteConfig(String group, String config) {
      try {
         core_.deleteConfig(group, config);
      } catch (Exception e) {
         handleException(e);
      }
   }

   public StrVector getAvailableConfigGroups() {
      return core_.getAvailableConfigGroups();
   }

   @Override
   public int getRowCount() {
      return propListVisible_.size();
   }

   @Override
   public int getColumnCount() {
      return columnNames_.length;
   }

   public boolean isEditingGroup() {
      return true;
   }

   @Override
   public PropertyItem getPropertyItem(int row) {
      return propListVisible_.get(row);
   }

   @Override
   public Object getValueAt(int row, int col) {

      PropertyItem item = propListVisible_.get(row);
      if (col == PropertyNameColumn_) {
         return item.device + "-" + item.name;
      } else if (col == PropertyValueColumn_) {
         return item.value;
      } else if (col == PropertyUsedColumn_) {
         return item.confInclude;
      }

      return null;
   }

   public void setValueInCore(PropertyItem item, Object value) {
      ReportingUtils.logMessage(item.device + "/" + item.name + ":" + value);
      try {
         if (item.isInteger()) {
            core_.setProperty(item.device, item.name, NumberUtils.intStringDisplayToCore(value));
         } else if (item.isFloat()) {
            core_.setProperty(item.device, item.name, NumberUtils.doubleStringDisplayToCore(value));
         } else {
            core_.setProperty(item.device, item.name, value.toString());
         }
         item.value = value.toString();
         core_.waitForDevice(item.device);
      } catch (Exception e) {
         handleException(e);
      }

   }

   @Override
   public void setValueAt(Object value, int row, int col) {
      PropertyItem item = propListVisible_.get(row);
      ReportingUtils.logMessage("Setting value " + value + " at row " + row);
      if (col == PropertyValueColumn_) {
         if (item.confInclude) {
            setValueInCore(item, value);
            core_.updateSystemStateCache();
            refresh(true);
            gui_.refreshGUIFromCache();
         }
      } else if (col == PropertyUsedColumn_) {
         item.confInclude = ((Boolean) value).booleanValue();
      }
      fireTableCellUpdated(row, col);
   }

   @Override
   public String getColumnName(int column) {
      return columnNames_[column];
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == PropertyValueColumn_) {
         if (nCol == 2) // do not allow editing in the group editor view
         {
            return false;
         } else {
            return !propListVisible_.get(nRow).readOnly;
         }
      } else if (nCol == PropertyUsedColumn_) {
         if (!isEditingGroup()) {
            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   StrVector getAvailableConfigs(String group) {
      return core_.getAvailableConfigs(group);
   }

   public void refresh(boolean fromCache) {
      try {
         update(fromCache);
         this.fireTableDataChanged();
      } catch (Exception e) {
         handleException(e);
      }
   }

   public void update(boolean fromCache) {
      update(flags_, groupName_, presetName_, fromCache);
   }

   public void setShowReadOnly(boolean showReadOnly) {
      showReadOnly_ = showReadOnly;
   }

   public void update(ShowFlags flags, String groupName, String presetName,
           boolean fromCache) {
      try {
         StrVector devices = core_.getLoadedDevices();
         propList_.clear();

         Configuration cfg = core_.getConfigGroupState(groupName);

         boolean liveMode = gui_.isLiveModeOn();
         gui_.enableLiveMode(false);

         setUpdating(true);

         for (int i = 0; i < devices.size(); i++) {

            if (showDevice(flags, devices.get(i))) {

               StrVector properties = core_.getDevicePropertyNames(devices.get(i));
               for (int j = 0; j < properties.size(); j++) {
                  PropertyItem item = new PropertyItem();
                  if (!groupOnly_ || cfg.isPropertyIncluded(devices.get(i), properties.get(j))) {
                     item.readFromCore(core_, devices.get(i), properties.get(j), fromCache);
                     if ((!item.readOnly || showReadOnly_) && !item.preInit) {
                        if (cfg.isPropertyIncluded(item.device, item.name)) {
                           item.confInclude = true;
                           item.setValueFromCoreString(cfg.getSetting(item.device, item.name).getPropertyValue());
                        } else {
                           item.confInclude = false;
                           item.setValueFromCoreString(core_.getProperty(devices.get(i), properties.get(j)));
                        }
                        propList_.add(item);
                     }
                  }
               }

            }
         }

         setUpdating(false);

         updateRowVisibility(flags);

         gui_.enableLiveMode(liveMode);
      } catch (Exception e) {
         handleException(e);
      }
      this.fireTableStructureChanged();

   }

   public void updateRowVisibility(ShowFlags flags) {
      propListVisible_.clear();

      boolean showDevice;

      for (PropertyItem item : propList_) {
         // select which devices to display

         showDevice = showDevice(flags, item.device);;

         if (showUnused_ == false && item.confInclude == false) {
            showDevice = false;
         }

         if (showDevice) {
            propListVisible_.add(item);
         }
      }

      this.fireTableStructureChanged();
      this.fireTableDataChanged();
   }

   public Boolean showDevice(ShowFlags flags, String deviceName) {
      DeviceType dType = null;
      try {
         dType = core_.getDeviceType(deviceName);
      } catch (Exception e) {
         handleException(e);
      }

      Boolean showDevice = false;
      if (dType == DeviceType.SerialDevice) {
         showDevice = false;
      } else if (dType == DeviceType.CameraDevice) {
         showDevice = flags.cameras_;
      } else if (dType == DeviceType.ShutterDevice) {
         showDevice = flags.shutters_;
      } else if (dType == DeviceType.StageDevice) {
         showDevice = flags.stages_;
      } else if (dType == DeviceType.XYStageDevice) {
         showDevice = flags.stages_;
      } else if (dType == DeviceType.StateDevice) {
         showDevice = flags.state_;
      } else {
         showDevice = flags.other_;
      }

      return showDevice;
   }

   public void setColumnNames(String col0, String col1, String col2) {
      columnNames_[0] = col0;
      columnNames_[1] = col1;
      columnNames_[2] = col2;
   }

   private void handleException(Exception e) {
      ReportingUtils.showError(e);
   }

   public void setGUI(ScriptInterface gui) {
      gui_ = gui;
   }

   public void setFlags(ShowFlags flags) {
      flags_ = flags;
   }

   public void setShowUnused(boolean showUnused) {
      showUnused_ = showUnused;
   }

   public ArrayList<PropertyItem> getPropList() {
      return propList_;
   }

   public void setUpdating(boolean updating) {
      updating_ = updating;
   }

   public boolean updating() {
      return updating_;
   }
}
