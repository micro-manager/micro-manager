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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import javax.swing.table.AbstractTableModel;
import org.micromanager.plugins.magellan.main.Magellan;
import mmcorej.CMMCore;

/**
 * Model for table that allows selection of properties
 * 3 columns--checkbox, property, value, nickname
 */
public class DeviceControlChooserTableModel  extends AbstractTableModel {

   
   
   private ArrayList<SinglePropertyOrGroup> allGroupsAndProps_;
   private LinkedList<SinglePropertyOrGroup> storedGroupsAndProps_;
   private TreeMap<String, String> propLabels_;
   
   private Preferences prefs_;

   
   public DeviceControlChooserTableModel(Preferences prefs) {
      allGroupsAndProps_ = PropertyAndGroupUtils.readConfigGroupsAndProperties(true);
      storedGroupsAndProps_ = PropertyAndGroupUtils.readStoredGroupsAndProperties(prefs);
      propLabels_ = new TreeMap<String, String>();
      for (SinglePropertyOrGroup item : storedGroupsAndProps_) {
         propLabels_.put(item.toString(), PropertyAndGroupUtils.getPropNickname(prefs, item));
      }
      prefs_ = prefs;
   }
   
   public void storeProperties() {
      PropertyAndGroupUtils.saveStoredProperties(prefs_, storedGroupsAndProps_, propLabels_); 
   }

   @Override
   public Class getColumnClass(int column) {
      if (column == 0) {
         return Boolean.class;
      }
      return String.class;
   }
   
   @Override
   public int getRowCount() {
     return allGroupsAndProps_.size();
   }

   @Override
   public int getColumnCount() {
      return 3;
   }
   
   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 0) {
         SinglePropertyOrGroup item = allGroupsAndProps_.get(row);
         if (propLabels_.containsKey(item.toString())) {
            propLabels_.remove(item.toString());
            storedGroupsAndProps_.remove(storedGroupsAndProps_.indexOf(item));
         } else {
            propLabels_.put(item.toString(), item.toString());
            storedGroupsAndProps_.add(item);
         }
         fireTableDataChanged();
      } else if (col == 2) {
         SinglePropertyOrGroup item = allGroupsAndProps_.get(row);
         propLabels_.put(item.toString(), (String) value);
      }
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      SinglePropertyOrGroup item = allGroupsAndProps_.get(rowIndex);
      if (columnIndex == 0) {
         //check box state
         return propLabels_.containsKey(item.toString());
      } else if (columnIndex == 1) {
         return item.toString();
      } else {
         if (propLabels_.containsKey(item.toString())) {
            return propLabels_.get(item.toString());
         } else {
            return null;
         }
      }
   }

    @Override
   public String getColumnName(int column) {
      if (column == 0) {
         return "Include";
      } else if (column == 1) {
         return "Property";
      } else {
         return "Nickname";
      }
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == 0) {
         return true;
      } else if (nCol == 2) {
         return propLabels_.containsKey(allGroupsAndProps_.get(nRow).device + "-" + allGroupsAndProps_.get(nRow).name);
      }
      return false;
   }
   
    
   
}
