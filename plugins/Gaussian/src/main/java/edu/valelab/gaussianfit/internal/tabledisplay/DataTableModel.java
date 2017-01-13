/**
 *  
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * 
 * Copyright UCSF, 2017
 * 
 * Licensed under BSD license version 2.0
 * 
 * Model for the data shown in the Data window in Localization microscopy
 * plugin
 * 
 */
package edu.valelab.gaussianfit.internal.tabledisplay;

import edu.valelab.gaussianfit.data.RowData;
import java.util.ArrayList;
import javax.swing.table.AbstractTableModel;

/**
 * Model for the data shown in the Data window in Localization microscopy
 * plugin
 * 
 * @author nico
 */
public class DataTableModel extends AbstractTableModel {
   
   private final String[] columnNames_ = {"ID", "Image", "Nr of spots",
      "2C Reference", "Ch.", "X", "Y", "stdX", "stdY", "nrPhotons"};
   private final ArrayList<RowData> rowData_;
   public final static NullClass NULLINSTANCE = new NullClass();

   public DataTableModel() {
      rowData_ = new ArrayList<RowData>();
   }
   
   public void addRowData(RowData row) {
      rowData_.add(row);
   }
   
   public void fireRowInserted() {
      super.fireTableRowsInserted(rowData_.size()-1, rowData_.size() -1 );
   }
   
   public RowData getRow(int rowNr) {
      return rowData_.get(rowNr);
   }

   @Deprecated
   public ArrayList<RowData> getRowData() {
      return rowData_;
   }
   
   public void removeRow(int rowNr) {
      rowData_.remove(rowNr);
      // TODO: this may not work with sorted tables! 
      super.fireTableRowsDeleted(rowNr, rowNr);
   }
   
   
   /**
    * Return a dataset
    * @param ID with requested ID.
    * @return RowData with selected ID, or null if not found
    */
   public RowData getDataByID(int ID) {
      for (RowData row : rowData_) {
         if (row.ID_ == ID) {
            return row;
         }
      }
      return null;
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
            return rowData_.get(row).spotList_.get(0).getChannel();
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 5) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).spotList_.get(0).getXCenter();
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 6) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).spotList_.get(0).getYCenter();
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 7) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).stdX_;
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 8) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).stdY_;
         } else {
            return NULLINSTANCE;
         }
      } else if (col == 9) {
         if (rowData_.get(row).isTrack_) {
            return rowData_.get(row).totalNrPhotons_;
         } else {
            return NULLINSTANCE;
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
         case 3:
            return String.class;
         case 0:
         case 2:
         case 4:
            return Integer.class;
         default:  
            return Double.class;
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
