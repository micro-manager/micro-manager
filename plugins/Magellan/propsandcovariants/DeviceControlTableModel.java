/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import propsandcovariants.PropertyAndGroupUtils;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import javax.swing.table.AbstractTableModel;
import mmcorej.CMMCore;
import org.micromanager.MMStudio;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class DeviceControlTableModel extends AbstractTableModel implements MMListenerInterface {
     
   
   private LinkedList<SinglePropertyOrGroup> storedGroupsAndProps_;
   
   private CMMCore core_;
   private ScriptInterface mmAPI_;
   private Preferences prefs_;

   
   public DeviceControlTableModel(Preferences prefs) {
      mmAPI_ = MMStudio.getInstance();
      core_ = mmAPI_.getMMCore();
      storedGroupsAndProps_ = PropertyAndGroupUtils.readStoredGroupsAndProperties(prefs);
      prefs_ = prefs;
   }
   
   public void updateStoredProps() {
      storedGroupsAndProps_ = PropertyAndGroupUtils.readStoredGroupsAndProperties(prefs_);      
   }
   
   @Override
   public int getRowCount() {
     return storedGroupsAndProps_.size();
   }

   @Override
   public int getColumnCount() {
      return 2;
   }
   
   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 1) {
         SinglePropertyOrGroup item = storedGroupsAndProps_.get(row);
         setValueInCore(item, value);
         core_.updateSystemStateCache();
         mmAPI_.refreshGUIFromCache();
         fireTableCellUpdated(row, col);
      }
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      SinglePropertyOrGroup item = storedGroupsAndProps_.get(rowIndex);
      if (columnIndex == 0) {
         //prop label
         return PropertyAndGroupUtils.getPropNickname(prefs_, storedGroupsAndProps_.get(rowIndex));
      } else {
         //prop value
         return item.value;
      }
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == 0) {
         return false;
      } else {
         return !storedGroupsAndProps_.get(nRow).readOnly;
      }
   }
  
   private void setValueInCore(SinglePropertyOrGroup item, Object value) {
      ReportingUtils.logMessage(item.device + "/" + item.name + ":" + value);
      try {
         if (item.isGroup()) {
            core_.setConfig(item.name, value.toString());
         } else if (item.isInteger()) {
            core_.setProperty(item.device, item.name, NumberUtils.intStringDisplayToCore(value));
         } else if (item.isFloat()) {
            core_.setProperty(item.device, item.name, NumberUtils.doubleStringDisplayToCore(value));
         } else {
            core_.setProperty(item.device, item.name, value.toString());
         }
         item.value = value.toString();
         if (!item.isGroup()) {
            core_.waitForDevice(item.device);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);         
      }

   }

   public SinglePropertyOrGroup getPropertyItem(int rowIndex) {
      return storedGroupsAndProps_.get(rowIndex);
   }

   @Override
   public void propertiesChangedAlert() {
      
   }

   @Override
   public void propertyChangedAlert(String device, String property, String value) {
      for (int i = 0; i <storedGroupsAndProps_.size(); i++) {
         SinglePropertyOrGroup g = storedGroupsAndProps_.get(i);
         if (!g.isGroup() && g.getName().equals(device+"-"+property)) {
            g.value = value;
            fireTableRowsUpdated(i, i);
         }
      }
   }

   @Override
   public void configGroupChangedAlert(String groupName, String newConfig) {
      for (int i = 0; i <storedGroupsAndProps_.size(); i++) {
         SinglePropertyOrGroup g = storedGroupsAndProps_.get(i);
         if (g.isGroup() && g.getName().equals(SinglePropertyOrGroup.GROUP_PREFIX + groupName)) {
            g.value = newConfig;
            fireTableRowsUpdated(i, i);
         }
      }
   }

   @Override
   public void systemConfigurationLoaded() {
      fireTableDataChanged();
   }

   @Override
   public void pixelSizeChangedAlert(double newPixelSizeUm) {
   }

   @Override
   public void stagePositionChangedAlert(String deviceName, double pos) {
   }

   @Override
   public void xyStagePositionChanged(String deviceName, double xPos, double yPos) {
   }

   @Override
   public void exposureChanged(String cameraName, double newExposureTime) {
   }

   @Override
   public void slmExposureChanged(String slmName, double newExposureTime) {
   }

}
