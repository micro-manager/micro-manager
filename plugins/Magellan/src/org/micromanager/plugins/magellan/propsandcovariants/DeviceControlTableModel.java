///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.plugins.magellan.propsandcovariants;

import org.micromanager.plugins.magellan.propsandcovariants.PropertyAndGroupUtils;
import java.util.LinkedList;
import java.util.prefs.Preferences;
import javax.swing.table.AbstractTableModel;
import org.micromanager.plugins.magellan.main.Magellan;
import org.micromanager.plugins.magellan.misc.Log;
import org.micromanager.plugins.magellan.misc.NumberUtils;
import mmcorej.CMMCore;


/**
 *
 * @author Henry
 */
public class DeviceControlTableModel extends AbstractTableModel   {
     
   
   private LinkedList<SinglePropertyOrGroup> storedGroupsAndProps_;
   
   private CMMCore core_;
   private Preferences prefs_;

   
   public DeviceControlTableModel(Preferences prefs) {
      core_ = Magellan.getCore();
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
         Magellan.getScriptInterface().refreshGUIFromCache();
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
         Log.log(e);         
      }

   }

   public SinglePropertyOrGroup getPropertyItem(int rowIndex) {
      return storedGroupsAndProps_.get(rowIndex);
   }

}
