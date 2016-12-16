/**
 * Model for the data shown in the Data window in Localization microscopy
 * plugin
 * 
 */
package edu.valelab.gaussianfit.internal.tabledisplay;

import edu.valelab.gaussianfit.data.RowData;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author nico
 */
public class DataTableModel extends AbstractTableModel {

   private final String[] columnNames_ = {"ID", "Image", "Nr of spots",
      "2C Reference", "Ch.", "X", "Y", "stdX", "stdY", "nrPhotons"};

   private final ArrayList<RowData> rowData_;

   public DataTableModel() {
      rowData_ = new ArrayList<RowData>();
   }

   @Override
   public String getColumnName(int col) {
      return columnNames_[col];
   }

   @Override
   public int getRowCount() {
      if (rowData_ == null) {
         return 0;
      }
      return rowData_.size();
   }

   @Override
   public int getColumnCount() {
      return columnNames_.length;
   }

   @Override
   public Object getValueAt(int row, int col) {
      if (col == 0 && rowData_ != null) {
         return rowData_.get(row).ID_;
      } else if (col == 1 && rowData_ != null) {
         return rowData_.get(row).name_;
      } else if (col == 2) {
         return rowData_.get(row).spotList_.size();
      } else if (col == 3) {
         return rowData_.get(row).colCorrRef_;
      } else if (col == 4) {
         if (rowData_.get(row).isTrack_) {
            return "" + rowData_.get(row).spotList_.get(0).getChannel();
         } else {
            return null;
         }
      } else if (col == 5) {
         if (rowData_.get(row).isTrack_) {
            return String.format("%.2f", rowData_.get(row).spotList_.get(0).getXCenter());
         } else {
            return null;
         }
      } else if (col == 6) {
         if (rowData_.get(row).isTrack_) {
            return String.format("%.2f", rowData_.get(row).spotList_.get(0).getYCenter());
         } else {
            return null;
         }
      } else if (col == 7) {
         if (rowData_.get(row).isTrack_) {
            return String.format("%.2f", rowData_.get(row).stdX_);
         } else {
            return null;
         }
      } else if (col == 8) {
         if (rowData_.get(row).isTrack_) {
            return String.format("%.2f", rowData_.get(row).stdY_);
         } else {
            return null;
         }
      } else if (col == 9) {
         if (rowData_.get(row).isTrack_) {
            return String.format("%.2f", rowData_.get(row).totalNrPhotons_);
         } else {
            return null;
         }
      }

      return getColumnName(col);

   }

   @Override
   public boolean isCellEditable(int row, int col) {
      return col == 1;
   }

   @Override
   public Class getColumnClass(int col) {
      switch (col) {
         case 1:
            return String.class;
         case 0:
         case 2:
            return Integer.class;
         default:  // even though all others are Doubles, they have empty 
            // values, which causes issues elsewhere.  Probably need 
            // to write my own sorter
            return String.class;
      }
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
      if (col == 1) {
         rowData_.get(row).name_ = (String) value;
      }
      fireTableCellUpdated(row, col);
   }
}
