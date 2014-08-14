/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gui.propertytable;

import java.awt.Component;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.utils.PropertyTableData;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.ShowFlags;

/**
 *
 * @author Henry
 */
 public class PropertyEditorTableData extends PropertyTableData {
      public PropertyEditorTableData(CMMCore core, String groupName, String presetName,
         int PropertyValueColumn, int PropertyUsedColumn, Component parentComponent) {

         super(core, groupName, presetName, PropertyValueColumn, PropertyUsedColumn, false);
      }
   
      private static final long serialVersionUID = 1L;

      @Override
      public void setValueAt(Object value, int row, int col) {
         org.micromanager.utils.PropertyItem item = propListVisible_.get(row);
         ReportingUtils.logMessage("Setting value " + value + " at row " + row);
         if (col == PropertyValueColumn_) {
            setValueInCore(item,value);
         }
         core_.updateSystemStateCache();
         refresh(true);
         gui_.refreshGUIFromCache();
         fireTableCellUpdated(row, col);
      }

      public void update (String device, String propName, String newValue) {
         org.micromanager.utils.PropertyItem item = getItem(device, propName);
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

            boolean liveMode = gui_.isLiveModeOn();
            gui_.enableLiveMode(false);
            for (int i=0; i<devices.size(); i++) { 
               if (this.showDevice(flags, devices.get(i))) {
                  StrVector properties = core_.getDevicePropertyNames(devices.get(i));
                  for (int j=0; j<properties.size(); j++){
                     org.micromanager.utils.PropertyItem item = new org.micromanager.utils.PropertyItem();
                     item.readFromCore(core_, devices.get(i), properties.get(j), fromCache);

                     if ((!item.readOnly || showReadOnly_) && !item.preInit) {
                        propList_.add(item);
                     }
                  }
               }
            }

            updateRowVisibility(flags); 


            gui_.enableLiveMode(liveMode);
         } catch (Exception e) {
            ReportingUtils.showError(e.toString());
         }
         this.fireTableStructureChanged();

      }
   }