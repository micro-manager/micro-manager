///////////////////////////////////////////////////////////////////////////////
//FILE:          ConfigGroupPad.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005

//COPYRIGHT:    University of California, San Francisco, 2006

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

//CVS:          $Id$

package org.micromanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.prefs.Preferences;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.*;



/**
 * Preset panel.
 * Displays a list of groups and lets the user select a preset.
 */
public class ConfigGroupPad extends JScrollPane{

   private static final long serialVersionUID = 1L;
   private JTable table_;
   private StateTableData data_;
   private ScriptInterface parentGUI_;
   private ArrayList<ChannelSpec> channels_;
   Preferences prefs_;
   private String COLUMN_WIDTH = "group_col_width";
   public PresetEditor presetEditor_ = null;
   public String groupName_ = "";

   
   public ConfigGroupPad() {
      super();
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/PresetPad");
      channels_ = new ArrayList<ChannelSpec>();
   }

   private void handleException (Exception e) {
      ReportingUtils.showError(e);
   }

   public void setCore(CMMCore core){
      table_ = new JTable();
      table_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      table_.setAutoCreateColumnsFromModel(false);
      table_.setRowSelectionAllowed(true);
      setViewportView(table_);
      
      data_ = new StateTableData(core);
      table_.setModel(data_);


      table_.addColumn(new TableColumn(0,200,new StateGroupCellRenderer(),null));
      table_.addColumn(new TableColumn(1,200,new StatePresetCellRenderer(), new StatePresetCellEditor()));
      
      int colWidth = prefs_.getInt(COLUMN_WIDTH , 0);
      if (colWidth > 0) {
         table_.getColumnModel().getColumn(0).setPreferredWidth(colWidth);
      }
   }
   
   public void saveSettings() {
      if (prefs_ != null && table_ != null)
         prefs_.putInt(COLUMN_WIDTH, table_.getColumnModel().getColumn(0).getWidth());
   }

   public void setParentGUI(ScriptInterface parentGUI) {
      parentGUI_ = parentGUI;
   }

   public void refreshStructure() {
      if (data_ != null) {
         data_.updateStatus();
         data_.fireTableStructureChanged();
         table_.repaint();
      }
   }

   public void refreshGroup(String groupName, String configName) {
      if (data_ != null) {
         data_.refreshGroup(groupName, configName);
         data_.fireTableStructureChanged();
         table_.repaint();
      }
   }

   public String getGroup() {
      int idx = table_.getSelectedRow();
      if (idx<0 || data_.getRowCount()<=0) {
         return "";
      } else {
         return (String) data_.getValueAt(idx, 0);
      }
   }

   public void setGroup(String groupName) {
      for (int i=0;i<data_.getRowCount();i++) {
         if(data_.getValueAt(i,0).toString().contentEquals(groupName)) {
            table_.setRowSelectionInterval(i,i);
         }
      }
   }
   
   public String getPreset() {
      int idx = table_.getSelectedRow();
      if (idx<0 || data_.getRowCount()<=0) {
         return "";
      } else {
         try {
            return data_.core_.getCurrentConfig((String) data_.getValueAt(idx, 0));
         } catch (Exception e) {
            // TODO Auto-generated catch block
            ReportingUtils.logError(e);
            return null;
         }
      }
   }



   ////////////////////////////////////////////////////////////////////////////
   /**
    * Property table data model, representing state devices
    */
   public final class StateTableData extends AbstractTableModel {
      private static final long serialVersionUID = -6584881796860806078L;
      final public String columnNames_[] = {
            "Group",
            "Preset"
      };
      ArrayList<StateItem> groupList_ = new ArrayList<StateItem>();
      private CMMCore core_ = null;
      private boolean configDirty_;

      public StateTableData(CMMCore core) {
         core_ = core;
         updateStatus();
         configDirty_ = false;
      }
      public int getRowCount() {
         return groupList_.size();
      }

      public int getColumnCount() {
         return columnNames_.length;
      }

      public StateItem getPropertyItem(int row) {
         return groupList_.get(row);
      }

      public Object getValueAt(int row, int col) {
         StateItem item = groupList_.get(row);
         if (col == 0)
            return item.group;
         else if (col == 1)
            return item.config;

         return null;
      }

      
      @Override
      public void setValueAt(Object value, int row, int col) {
         StateItem item = groupList_.get(row);
         if (col == 1) {
            try {
               if (value != null && value.toString().length() > 0)
               {
                 boolean restartLive = parentGUI_.isLiveModeOn();
                 if (restartLive)
                    parentGUI_.enableLiveMode(false);
                 
                  if (item.singleProp) {
                     if (item.hasLimits && item.isInteger()) {
                        core_.setProperty(item.device, item.name, NumberUtils.intStringDisplayToCore(value));
                     } else if (item.hasLimits && !item.isInteger()) {
                        core_.setProperty(item.device, item.name, NumberUtils.doubleStringDisplayToCore(value));
                     } else  {
                        core_.setProperty(item.device, item.name, value.toString());
                     }
                     core_.waitForDevice(item.device);
                  } else {
                     core_.setConfig(item.group, value.toString());
                     core_.waitForConfig(item.group, value.toString());
                  }
                  refreshStatus();
                  repaint();
                  if (parentGUI_ != null)
                     parentGUI_.updateGUI(false);

                  // if the channel was changed, adjust the exposure and contrast settings
                  if (item.group.equals(MMCoreJ.getG_Keyword_Channel())) {
                     ChannelSpec csFound = null;
                     // >>>> this should be a hash table
                     for (int i=0; i<channels_.size(); i++) {
                        ChannelSpec cs = channels_.get(i);
                        if (cs != null) {
                           if (cs.name_.equals(item.config)) {
                              csFound = cs;
                              break;
                           }
                        }
                     }

                     if (csFound == null) {
                        // channel was not found -> add it to the list
                        csFound = new ChannelSpec();
                        csFound.name_ = item.config;
                        csFound.exposure_ = core_.getExposure();
                        if (parentGUI_ != null) {
                           ContrastSettings ctr = parentGUI_.getContrastSettings();
                           if (ctr != null) 
                              csFound.contrast_ = ctr;

                           channels_.add(csFound);
                        }
                     } else {
                        // channel was found - apply settings
                        // >>> DISABLED - not working properly
//                      core_.setExposure(csFound.exposure_);
//                      if (parentGUI_ != null)
//                      parentGUI_.applyContrastSettings(csFound.contrast8_, csFound.contrast16_);
                     }
                  }
                  if (restartLive)
                       parentGUI_.enableLiveMode(true);
               }
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
         if (nCol == 0)
            return false;
                  
         return true;
      }

      private boolean containsValue(String strs[], String theValue) {
          for (String str:strs) {
              if (theValue.equals(str))
                  return true;
          }
          return false;
      }

       public void updateStatus() {
           try {
               StrVector groups = core_.getAvailableConfigGroups();
               HashMap<String,String> oldGroupHash = new HashMap<String,String>();
               for (StateItem group : groupList_) {
                   oldGroupHash.put(group.group, group.config);
               }
               groupList_.clear();

               for (String group:groups) {
                   StateItem item = new StateItem();
                   item.group = group;
                   item.config = core_.getCurrentConfig(item.group);
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
                           boolean itemHasAllowedValues = (0 < core_.getAllowedPropertyValues(item.device, item.name).size());
                           if (item.hasLimits || !itemHasAllowedValues) {
                               item.singleProp = true;
                               item.type = core_.getPropertyType(item.device, item.name);
                               item.setValueFromCoreString(core_.getProperty(item.device, item.name));
                               item.config = item.value;
                               item.lowerLimit = core_.getPropertyLowerLimit(item.device, item.name);
                               item.upperLimit = core_.getPropertyUpperLimit(item.device, item.name);
                               item.singlePropAllowed = core_.getAllowedPropertyValues(item.device, item.name).toArray();
                           }

                       }
                   }


                   groupList_.add(item);
               }
           } catch (Exception e) {
               handleException(e);
           }
       }


       public void refreshStatus() {
          try {
             for (int i=0; i<groupList_.size(); i++){
                StateItem item = groupList_.get(i);
                if (item.singleProp) {
                   item.config = core_.getProperty(item.device, item.name); 
                } else {
                   item.config = core_.getCurrentConfig(item.group);
                   // set descr to current situation so that Tooltips get updated
                   if (item.config.length() > 0) {
                       Configuration curCfg = core_.getConfigData(item.group, item.config);
                       item.descr = curCfg.getVerbose();
                   } else {
                       item.descr = "";
                   }
                }
             }
          } catch (Exception e) {
             handleException(e);
          }
       }

       public void refreshGroup(String groupName, String configName) {
          try {
             for (int i=0; i<groupList_.size(); i++) {
                StateItem item = groupList_.get(i);
                if (item.group.equals(groupName))
                   item.config = configName;
             }
          } catch (Exception e) {
             handleException(e);
          }
       }

       public boolean isConfigDirty() {
          return configDirty_;
       }

   }


}
