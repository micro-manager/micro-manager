/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package propsandcovariants;

import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;
import surfacesandregions.SurfaceManager;

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
