///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.internal.positionlist;

import javax.swing.table.AbstractTableModel;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.micromanager.events.internal.DefaultNewPositionListEvent;

class PositionTableModel extends AbstractTableModel {
   private static final long serialVersionUID = 1L;
   public final String[] COLUMN_NAMES = new String[] {
         "Label",
         "Position [um]"
   };
   private Studio studio_;
   private PositionList posList_;
   private MultiStagePosition curMsp_;

   public PositionTableModel(Studio studio) {
      studio_ = studio;
   }

   public void setData(PositionList pl) {
      posList_ = pl;
   }

   public PositionList getPositionList() {
      return posList_;
   }

   @Override
   public int getRowCount() {
      return posList_.getNumberOfPositions() + 1;
   }
   @Override
   public int getColumnCount() {
      return COLUMN_NAMES.length;
   }
   @Override
   public String getColumnName(int columnIndex) {
      return COLUMN_NAMES[columnIndex];
   }
   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      MultiStagePosition msp;
      if (rowIndex == 0) {
         msp = curMsp_;
      }
      else {
         msp = posList_.getPosition(rowIndex -1);
      }
      if (columnIndex == 0) {
         return msp.getLabel();
      } else if (columnIndex == 1) {
         StringBuilder sb = new StringBuilder();
         for (int i=0; i<msp.size(); i++) {
            StagePosition sp = msp.get(i);
            if (i!=0) {
               sb.append(";");
            }
            sb.append(sp.getVerbose());
         }
         return sb.toString();
      } else {
         return null;
      }
   }
   @Override
   public boolean isCellEditable(int rowIndex, int columnIndex) {
      if (rowIndex == 0) {
         return false;
      }
      if (columnIndex == 0) {
         return true;
      }
      return false;
   }
   @Override
   public void setValueAt(Object value, int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
         MultiStagePosition msp = posList_.getPosition(rowIndex - 1);
         if (msp != null) {
            msp.setLabel(((String) value).replaceAll("[^0-9a-zA-Z_]", "-"));
         }
      }
   }

   public void setCurrentMSP(MultiStagePosition msp) {
      curMsp_ = msp;
   }

   @Override
   public void fireTableDataChanged() {
      super.fireTableDataChanged();
      studio_.events().post(new DefaultNewPositionListEvent(posList_));
   }
}
