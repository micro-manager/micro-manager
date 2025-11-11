///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
//              modified by Nico Stuurman, 2012

//COPYRIGHT:    University of California, San Francisco, 2006, 2012

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

//CVS:          $Id$

package org.micromanager.internal;

import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.StrVector;
import org.micromanager.Studio;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.StateGroupCellRenderer;
import org.micromanager.internal.utils.StateItem;
import org.micromanager.internal.utils.StatePresetCellEditor;
import org.micromanager.internal.utils.StatePresetCellRenderer;


/**
 * Preset panel.
 * Displays a list of groups and lets the user select a preset.
 */
public final class ConfigGroupPad extends JScrollPane {

   private static final long serialVersionUID = 1L;
   private final Studio studio_;
   private JTable table_;
   private StateTableData data_;
   private static final String COLUMN_WIDTH = "group_col_width";


   public ConfigGroupPad(Studio studio) {
      super();
      studio_ = studio;
   }

   private void handleException(Exception e) {
      ReportingUtils.showError(e);
   }

   /**
    * Initialize config group pad.
    */
   public void initialize() {
      table_ = new DaytimeNighttime.Table();
      table_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      table_.setAutoCreateColumnsFromModel(false);
      table_.setRowSelectionAllowed(true);
      setViewportView(table_);

      data_ = new StateTableData(studio_.core());
      table_.setModel(data_);


      table_.addColumn(new TableColumn(0, 200,
            new StateGroupCellRenderer(studio_), null));
      table_.addColumn(new TableColumn(1, 200,
            new StatePresetCellRenderer(studio_), new StatePresetCellEditor()));

      int colWidth = studio_.profile().getSettings(this.getClass())
            .getInteger(COLUMN_WIDTH, 0);
      if (colWidth > 0) {
         table_.getColumnModel().getColumn(0).setPreferredWidth(colWidth);
      }
   }

   /**
    * Saves ConfigGroupPad settings to the user profile.
    */
   public void saveSettings() {
      if (table_ != null) {
         studio_.profile().getSettings(this.getClass()).putInteger(
               COLUMN_WIDTH, table_.getColumnModel().getColumn(0).getWidth());
      }
   }

   /**
    * Refreshes the structure of the ConfigGroupPad.
    *
    * @param fromCache If true, use the hardware state cache rather than enquiring
    *                  with the actual hardware.
    */
   public void refreshStructure(boolean fromCache) {
      if (data_ != null) {
         data_.rebuildModel(fromCache);
         data_.fireTableStructureChanged();
         if (table_.getCellEditor() != null) {
            table_.getCellEditor().stopCellEditing();
         }
         table_.repaint();
      }
   }

   /**
    * Sets the display to the provided group and config.
    *
    * @param groupName  ConfigGroup
    * @param configName ConfigName
    */
   public void refreshGroup(String groupName, String configName) {
      if (data_ != null) {
         data_.refreshGroup(groupName, configName);
         data_.fireTableStructureChanged();
         table_.repaint();
      }
   }

   /**
    * Returns the group selected by the user.
    *
    * @return Group selected by the user.
    */
   public String getSelectedGroup() {
      int idx = table_.getSelectedRow();
      if (idx < 0 || data_.getRowCount() <= 0) {
         return "";
      } else {
         return (String) data_.getValueAt(idx, 0);
      }
   }

   /**
    * Sets the UI to the provided group.
    *
    * @param groupName Group to show in the display.
    */
   public void setSelectedGroup(String groupName) {
      for (int i = 0; i < data_.getRowCount(); i++) {
         if (data_.getValueAt(i, 0).toString().contentEquals(groupName)) {
            table_.setRowSelectionInterval(i, i);
         }
      }
   }

   /**
    * TODO: describe.
    *
    * @return Preset for Selected Group
    */
   public String getPresetForSelectedGroup() {
      int idx = table_.getSelectedRow();
      if (idx < 0 || data_.getRowCount() <= 0) {
         return "";
      } else {
         try {
            return data_.core_.getCurrentConfig((String) data_.getValueAt(idx, 0));
         } catch (Exception e) {
            ReportingUtils.logError(e);
            return null;
         }
      }
   }


   ////////////////////////////////////////////////////////////////////////////

   /**
    * Property table data model, representing state devices.
    */
   public final class StateTableData extends AbstractTableModel {
      private static final long serialVersionUID = -6584881796860806078L;
      public final String[] columnNames_ = {
            "Group",
            "Preset"
      };
      ArrayList<StateItem> groupList_ = new ArrayList<>();
      private final CMMCore core_;

      public StateTableData(CMMCore core) {
         core_ = core;
         rebuildModel(false);
      }

      @Override
      public int getRowCount() {
         return groupList_.size();
      }

      @Override
      public int getColumnCount() {
         return columnNames_.length;
      }

      public StateItem getPropertyItem(int row) {
         return groupList_.get(row);
      }

      @Override
      public Object getValueAt(int row, int col) {
         if (row < groupList_.size()) {
            StateItem item = groupList_.get(row);
            if (col == 0) {
               return item.group;
            } else if (col == 1) {
               return item.config;
            }
         }
         return null;
      }


      @Override
      public void setValueAt(Object value, int row, int col) {
         StateItem item = groupList_.get(row);
         if (col == 1) {
            if (value != null && value.toString().length() > 0) {
               try {
                  studio_.live().setSuspended(true);
                  if (item.singleProp) {
                     if (item.hasLimits && item.isInteger()) {
                        core_.setProperty(item.device, item.name,
                              NumberUtils.intStringDisplayToCore(value));
                     } else if (item.hasLimits && !item.isInteger()) {
                        core_.setProperty(item.device, item.name,
                              NumberUtils.doubleStringDisplayToCore(value));
                     } else {
                        core_.setProperty(item.device, item.name, value.toString());
                     }
                     core_.waitForDevice(item.device);
                  } else {
                     core_.setConfig(item.group, value.toString());
                     core_.waitForConfig(item.group, value.toString());
                  }

                  // Associate exposure time with presets in current channel group
                  // only when syncing exposure time with MDA window.
                  if (item.group.equals(core_.getChannelGroup())) {
                     if (AcqControlDlg.getShouldSyncExposure()) {
                        core_.setExposure(
                                 studio_.app().getChannelExposureTime(
                                          item.group, value.toString(), core_.getExposure()));
                     }
                  }

                  // By updating the system cache here we are able to use it in 
                  // `refreshStatus` and when updating the GUI rather than needing 
                  // repeatedly query the same properties in both operations.
                  studio_.core().updateSystemStateCache();
                  refreshStatus();
                  table_.repaint();
                  // This is a little superfluous, but it is nice that we
                  // depend only on Studio, not MMStudio directly, so keep it that way.
                  if (studio_ instanceof MMStudio) {
                     // It appears important for performance that we use the
                     // non-config-pad-updating version of MMStudio.refreshGUI().
                     MMStudio parentGUI = (MMStudio) studio_;
                     parentGUI.uiManager().updateGUI(false, true);
                  } else {
                     studio_.app().refreshGUIFromCache();
                  }

               } catch (Exception e) {
                  handleException(e);
               } finally {
                  studio_.live().setSuspended(false);
               }
            }
         }
      }

      @Override
      public String getColumnName(int column) {
         return columnNames_[column];
      }

      @Override
      public boolean isCellEditable(int nRow, int nCol) {
         return nCol != 0;
      }

      /**
       * Rebuilds the model.
       *
       * @param fromCache Use cached hardware state if true.
       */
      public void rebuildModel(boolean fromCache) {
         try {
            ReportingUtils.logMessage("Rebuilding config group table");
            StrVector groups = core_.getAvailableConfigGroups();
            HashMap<String, String> oldGroupHash = new HashMap<>();
            for (StateItem group : groupList_) {
               oldGroupHash.put(group.group, group.config);
            }
            groupList_.clear();

            for (String group : groups) {
               StateItem item = new StateItem();
               item.group = group;
               if (fromCache) {
                  item.config = core_.getCurrentConfigFromCache(item.group);
               } else {
                  item.config = core_.getCurrentConfig(item.group);
               }
               item.allowed = core_.getAvailableConfigs(item.group).toArray();


               if (item.config.length() > 0) {
                  Configuration curCfg = core_.getConfigData(item.group, item.config);
                  item.descr = curCfg.getVerbose();
               } else {
                  item.descr = "";
               }

               if (item.allowed.length == 1) {
                  Configuration cfg = core_.getConfigData(item.group, item.allowed[0]);
                  if (cfg.size() == 1) {
                     item.device = cfg.getSetting(0).getDeviceLabel();
                     item.name = cfg.getSetting(0).getPropertyName();
                     item.hasLimits = core_.hasPropertyLimits(item.device, item.name);
                     boolean itemHasAllowedValues = (
                           0 < core_.getAllowedPropertyValues(item.device, item.name).size());
                     if (item.hasLimits || !itemHasAllowedValues) {
                        item.singleProp = true;
                        item.type = core_.getPropertyType(item.device, item.name);
                        if (fromCache) {
                           item.setValueFromCoreString(
                                 core_.getPropertyFromCache(item.device, item.name));
                        } else {
                           item.setValueFromCoreString(
                                 core_.getProperty(item.device, item.name));
                        }
                        item.config = item.value;
                        item.lowerLimit = core_.getPropertyLowerLimit(item.device, item.name);
                        item.upperLimit = core_.getPropertyUpperLimit(item.device, item.name);
                        item.singlePropAllowed = core_.getAllowedPropertyValues(
                              item.device, item.name).toArray();
                     }

                  }
               }


               groupList_.add(item);
            }
            ReportingUtils.logMessage("Finished rebuilding config group table");
         } catch (Exception e) {
            handleException(e);
         }
      }

      // Update the current presets for each config group, without updating
      // the list of config groups and presets. Note: This method relies on the property cache. 
      // If the cache is not up to date then it will not be accurate.
      private void refreshStatus() {
         try {
            ReportingUtils.logMessage("Refreshing config group table");
            for (StateItem item : groupList_) {
               if (item.singleProp) {
                  item.config = core_.getPropertyFromCache(item.device, item.name);
               } else {
                  item.config = core_.getCurrentConfigFromCache(item.group);
                  // set descr to current situation so that Tooltips get updated
                  if (item.config.length() > 0) {
                     Configuration curCfg = core_.getConfigData(item.group, item.config);
                     item.descr = curCfg.getVerbose();
                  } else {
                     item.descr = "";
                  }
               }
            }
            ReportingUtils.logMessage("Finished refreshing config group table");
         } catch (Exception e) {
            handleException(e);
         }
      }

      /**
       * TODO.
       *
       * @param groupName  Group name.
       * @param configName Configuration name.
       */
      public void refreshGroup(String groupName, String configName) {
         try {
            for (StateItem item : groupList_) {
               if (item.group.equals(groupName)) {
                  if (item.singleProp) {
                     item.value = core_.getPropertyFromCache(item.device, item.name);
                     item.config = item.value;
                  } else {
                     item.config = configName;
                  }
               }
            }
         } catch (Exception e) {
            handleException(e);
         }
      }
   }
}
