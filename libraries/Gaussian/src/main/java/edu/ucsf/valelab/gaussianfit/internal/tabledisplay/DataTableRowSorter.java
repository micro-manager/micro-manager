/* 
 * Nico Stuurman, nico.stuurman at ucsf.edu
 * 
 * @author - Nico Stuurman, 2017`
 * 
 * 
Copyright (c) 2017-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.internal.tabledisplay;

import java.util.Comparator;
import java.util.List;
import javax.swing.SortOrder;
import javax.swing.table.TableRowSorter;

/**
 * @author nico
 */
public class DataTableRowSorter extends TableRowSorter<DataTableModel> {

   private final DataTableModel model_;

   public DataTableRowSorter(DataTableModel model) {
      super(model);
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

            return ((Comparable<Object>) t).compareTo(t1);
         }
      };

      return c;
   }


}
