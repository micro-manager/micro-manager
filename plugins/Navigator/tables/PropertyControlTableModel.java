/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tables;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import javax.swing.table.AbstractTableModel;
import mmcorej.CMMCore;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMPropertyTableModel;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.PropertyItem;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class PropertyControlTableModel extends AbstractTableModel implements MMPropertyTableModel {
     
   
   private LinkedList<PropertyItem> storedProps_;
   
   private CMMCore core_;
   private ScriptInterface mmAPI_;
   private Preferences prefs_;

   
   public PropertyControlTableModel(Preferences prefs) {
      mmAPI_ = MMStudio.getInstance();
      core_ = mmAPI_.getMMCore();
      storedProps_ = PropertyManager.readStoredProperties(prefs);
      prefs_ = prefs;
   }
   
   public void updateStoredProps() {
      storedProps_ = PropertyManager.readStoredProperties(prefs_);      
   }
   
   @Override
   public int getRowCount() {
     return storedProps_.size();
   }

   @Override
   public int getColumnCount() {
      return 2;
   }
   
   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 1) {
         PropertyItem item = storedProps_.get(row);
         setValueInCore(item, value);
         core_.updateSystemStateCache();
         mmAPI_.refreshGUIFromCache();
         fireTableCellUpdated(row, col);
      }
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      PropertyItem item = storedProps_.get(rowIndex);
      if (columnIndex == 0) {
         //prop label
         return PropertyManager.getPropLabel(prefs_, storedProps_.get(rowIndex));
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
         return !storedProps_.get(nRow).readOnly;
      }
   }
  
   private void setValueInCore(PropertyItem item, Object value) {
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
         ReportingUtils.showError(e);         
      }

   }

   @Override
   public PropertyItem getPropertyItem(int rowIndex) {
      return storedProps_.get(rowIndex);
   }

}
