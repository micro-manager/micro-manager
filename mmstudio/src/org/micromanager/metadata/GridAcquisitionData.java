package org.micromanager.metadata;

import java.io.File;
import java.util.Hashtable;

public class GridAcquisitionData {
   private int rows_;
   private int cols_;
   private Hashtable<String, AcquisitionData> items_;
   
   public GridAcquisitionData() {
      items_ = new Hashtable<String, AcquisitionData>();
      rows_ = 0;
      cols_ = 0;
   }
   
   private String gridHash(int row, int col) {
      return new String(row + "-" + col);
   }
   
   /**
    * Loads all micro-manager acquisition data folders located within the specified root folder.
    * @param basePath - root folder path
    * @throws MMAcqDataException
    */
   public void load(String basePath) throws MMAcqDataException {

      // reset container
      rows_ = 0;
      cols_ = 0;
      items_.clear();
      
      File baseDir = new File(basePath);
      if (!baseDir.isDirectory())
         throw new MMAcqDataException("Base path for the grid data must be a directory.");
      
      // list all files
      File[] files = baseDir.listFiles();
      for (File f : files) {
         // act only on directories
         if (f.isDirectory()) {
            AcquisitionData ad = new AcquisitionData();
            ad.load(f.getAbsolutePath());
            
            // act only on items which have grid coordinates - ignore othrewise
            if (ad.hasSummaryValue(SummaryKeys.GRID_COLUMN) && ad.hasSummaryValue(SummaryKeys.GRID_ROW)) {
               int row = Integer.parseInt(ad.getSummaryValue(SummaryKeys.GRID_ROW));
               int col = Integer.parseInt(ad.getSummaryValue(SummaryKeys.GRID_COLUMN));
               items_.put(gridHash(row, col), ad);
               if (row + 1 > rows_)
                  rows_ = row + 1;
               if (col + 1 > cols_)
                  cols_ = col + 1;
            }
         }
      }
   }
   
   public int getColumns() {
      return cols_;
   }
   
   public int getRows() {
      return rows_;
   }
   
   /**
    * Returns acquistion data object for the specified row and column.
    * If the coordinate does not contain any valid data, it returns null. This is
    * not an exception because some grids may be sparse on purpose.
    */
   public AcquisitionData getAcquisitionData(int row, int col){
      String key = gridHash(row, col);
      if (items_.containsKey(key)) {
         return items_.get(key);
      }
      return null;
   }

}
