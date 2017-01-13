/* 
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * 
 * Copyright UCSF, 2017
 * 
 * Licensed under BSD license version 2.0
 * 
 */
package edu.valelab.gaussianfit.internal.tabledisplay;

import java.util.Comparator;
import java.util.List;
import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;

/**
 *
 * @author nico
 */
public class DataTableRowSorter extends TableRowSorter<DataTableModel> {
   private final DataTableModel model_;
   
   public DataTableRowSorter (DataTableModel model) {
      super (model);
      model_ = model;
   }
   
   @Override
   public Comparator<?> getComparator(final int column) {
      Class columnClass = model_.getColumnClass(column);
      if (columnClass == String.class) {
         return super.getComparator(column);
      }
      
      // Special Comparator that always keeps rows with empty
      // content (indicated by instances of NullClass on top
      Comparator c = new Comparator() {
         @Override
         public int compare(Object t, Object t1) {
            boolean ascending = true;
            List<? extends SortKey> sortKeys = getSortKeys();
            for (SortKey key : sortKeys) {
               if (key.getColumn() == column) {
                  ascending = key.getSortOrder() == SortOrder.ASCENDING;
               }
            }
            if (t == DataTableModel.NULLINSTANCE && t1 == DataTableModel.NULLINSTANCE) {
               return 0;
            }
            if (t == DataTableModel.NULLINSTANCE) {
               return ascending ? -1 : 1;
            }
            if (t1 == DataTableModel.NULLINSTANCE) {
               return ascending ? 1 : -1;
            }
            
            return ( (Comparable<Object>) t).compareTo(t1); 
         }
      };
              
      return c;
   }
              
              
}
