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

import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;
import org.micromanager.plugins.magellan.surfacesandregions.SurfaceManager;

/**
 *
 * @author Henry
 */
public class EligibleCovariantTableModel extends AbstractTableModel {

   private ArrayList<SinglePropertyOrGroup> propsAndGroups_;
   private ArrayList<SurfaceData> surfaceData_;
   private boolean independent_;
   
   public EligibleCovariantTableModel(boolean includeStats) {
      propsAndGroups_ = PropertyAndGroupUtils.readConfigGroupsAndProperties(includeStats);
      surfaceData_ = includeStats ? SurfaceManager.getInstance().getSurfaceData() : new ArrayList<SurfaceData>();
      independent_ = includeStats;
   }

   @Override
   public String getColumnName(int index) {
      return independent_ ? "Independent variable" : "Dependent variable";
   }

   @Override
   public int getRowCount() {
      return surfaceData_.size() + propsAndGroups_.size();
   }

   @Override
   public int getColumnCount() {
      return 1;
   }

   @Override
   public boolean isCellEditable(int rowIndex, int colIndex) {
      return false;
   }
   
   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex < surfaceData_.size()) {
         return surfaceData_.get(rowIndex);
      }
      return propsAndGroups_.get(rowIndex - surfaceData_.size());
   }
   
   
}
