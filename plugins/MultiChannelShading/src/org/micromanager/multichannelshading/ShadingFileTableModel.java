
package org.micromanager.multichannelshading;

import javax.swing.table.AbstractTableModel;

/**
 *
 * @author nico
 */
public class ShadingFileTableModel extends AbstractTableModel {
   public final String[] COLUMN_NAMES = new String[] {
         "Preset",
         "Image File",
         ""
   };
   
   
   public int getRowCount() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public int getColumnCount() {
      return COLUMN_NAMES.length;
   }

   public Object getValueAt(int i, int i1) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }
   
}
