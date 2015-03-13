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
   private ArrayList<SurfaceData> surfaceStats_;
   private boolean independent_;
   
   public EligibleCovariantTableModel(boolean includeStats) {
      propsAndGroups_ = PropertyAndGroupUtils.readConfigGroupsAndProperties(includeStats);
      surfaceStats_ = includeStats ? SurfaceManager.getInstance().getSurfaceStats() : new ArrayList<SurfaceData>();
      independent_ = includeStats;
   }

   @Override
   public String getColumnName(int index) {
      return independent_ ? "Independent variable" : "Dependent variable";
   }

   @Override
   public int getRowCount() {
      return surfaceStats_.size() + propsAndGroups_.size();
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
      if (rowIndex < surfaceStats_.size()) {
         return surfaceStats_.get(rowIndex);
      }
      return propsAndGroups_.get(rowIndex - surfaceStats_.size());
   }
   
   
}
