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
package org.micromanager.magellan.internal.gui;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import org.micromanager.magellan.internal.channels.MagellanChannelSpec;
import org.micromanager.magellan.internal.main.Magellan;
import mmcorej.CMMCore;

/**
 *
 * @author Henry
 */
public class SimpleChannelTableModel extends AbstractTableModel implements TableModelListener {

   private MagellanChannelSpec channels_;
   private final CMMCore core_;
   private final boolean exploreTable_;
   private boolean selectAll_ = true;
   public final String[] COLUMN_NAMES = new String[]{
      "Use",
      "Configuration",
      "Exposure",
      "Z-offset (um)",
      "Color",};

   public SimpleChannelTableModel(MagellanChannelSpec channels, boolean showColor) {
      exploreTable_ = !showColor;
      core_ = Magellan.getCore();
      channels_ = channels;
      Magellan.getStudio().getEventManager().registerForEvents(this);
   }

   public void selectAllChannels() {
      //Alternately select all channels or deselect channels
      channels_.setUseOnAll(selectAll_);
      selectAll_ = !selectAll_;
      fireTableDataChanged();
   }

   public void synchronizeExposures() {
      //Alternately select all channels or deselect channels
      channels_.synchronizeExposures();
      fireTableDataChanged();
   }

   public void shutdown() {
      Magellan.getStudio().getEventManager().unregisterForEvents(this);
   }

   public void setChannelGroup(String group) {
      if (channels_ != null) {
         channels_.updateChannelGroup(group);
      }
   }

   public void setChannels(MagellanChannelSpec channels) {
      channels_ = channels;
   }

   @Override
   public int getRowCount() {
      if (channels_ == null) {
         return 0;
      } else {
         return channels_.getNumChannels();
      }
   }

   @Override
   public int getColumnCount() {
      return COLUMN_NAMES.length - (exploreTable_ ? 1 : 0);
   }

   @Override
   public String getColumnName(int columnIndex) {
      return COLUMN_NAMES[columnIndex];
   }

   @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      //use name exposure, color
      if (columnIndex == 0) {
         return channels_.getChannelListSetting(rowIndex).use_;
      } else if (columnIndex == 1) {
         return channels_.getChannelListSetting(rowIndex).name_;
      } else if (columnIndex == 2) {
         return channels_.getChannelListSetting(rowIndex).exposure_;
      } else if (columnIndex == 3) {
         return channels_.getChannelListSetting(rowIndex).offset_;
      } else {
         return channels_.getChannelListSetting(rowIndex).color_;
      }
   }

   @Override
   public Class getColumnClass(int columnIndex) {
      if (columnIndex == 0) {
         return Boolean.class;
      } else if (columnIndex == 1) {
         return String.class;
      } else if (columnIndex == 2) {
         return Double.class;
      } else if (columnIndex == 3) {
         return Double.class;
      } else {
         return Color.class;
      }
   }

   @Override
   public void setValueAt(Object value, int row, int columnIndex) {
      //use name exposure, color  
      int numCamChannels = (int) core_.getNumberOfCameraChannels();

      if (columnIndex == 0) {
         channels_.getChannelListSetting(row).use_ = ((Boolean) value);
         //same for all other channels of the same camera_
         if (numCamChannels > 1) {
            for (int i = (row - row % numCamChannels); i < (row / numCamChannels + 1) * numCamChannels; i++) {
               channels_.getChannelListSetting(i).use_ = ((Boolean) value);
            }
            fireTableDataChanged();
         }
         GUI.getInstance().acquisitionSettingsChanged();
      } else if (columnIndex == 1) {
         //cant edit channel name
      } else if (columnIndex == 2) {
         double val = value instanceof String ? Double.parseDouble((String) value) : (Double) value;
         channels_.getChannelListSetting(row).exposure_ = val;
         //same for all other channels of the same camera_
         if (numCamChannels > 1) {
            for (int i = (row - row % numCamChannels); i < (row / numCamChannels + 1) * numCamChannels; i++) {
               channels_.getChannelListSetting(i).exposure_ = val;
            }
            fireTableDataChanged();
         }
      } else if (columnIndex == 3) {
         double val = value instanceof String ? Double.parseDouble((String) value) : (Double) value;
         channels_.getChannelListSetting(row).offset_ = val;
      } else {
         channels_.getChannelListSetting(row).color_ = ((Color) value);
      }
      //Store the newly selected value in preferences
      channels_.storeCurrentSettingsInPrefs();
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      return nCol != 1;
   }

   @Override
   public void tableChanged(TableModelEvent e) {
   }


}
