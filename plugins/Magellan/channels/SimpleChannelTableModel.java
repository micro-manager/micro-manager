/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package channels;

import gui.SettingsDialog;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import misc.GlobalSettings;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class SimpleChannelTableModel extends AbstractTableModel implements TableModelListener {

   
      private ArrayList<ChannelSetting> channels_ = new ArrayList<ChannelSetting>();
      private final CMMCore core_;
      private String channelGroup_ = null;
      private final boolean exploreTable_;
      public final String[] COLUMN_NAMES = new String[]{
         "Use",
         "Configuration",
         "Exposure",
          "Color"
   };

   public SimpleChannelTableModel(boolean exploreTable) {
      exploreTable_ = exploreTable;
      core_ = MMStudio.getInstance().getCore();
      refreshChannels();
   }
   
   public boolean anyChannelsActive() {
      for (ChannelSetting c : channels_) {
         if (c.use_) {
            return true;
         }
      }
      return false;
   }

   public void setChannelGroup(String group) {
      channelGroup_ = group;
   }

   public void refreshChannels() {
      channels_ = ChannelUtils.getAvailableChannels(channelGroup_);
   }
      
   public String[] getActiveChannelNames() {
      int count = 0;
      for (ChannelSetting c : channels_) {
         count += c.use_ ? 1 : 0;
      }
      
      String[] channelNames = new String[count];
      for (int i = 0; i < channelNames.length; i++) {
         channelNames[i] = channels_.get(i).name_;
      }
      return channelNames;
   }

   @Override
   public int getRowCount() {
      if (channels_ == null) {
         return 0;
      } else {
         return channels_.size();
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
         return channels_.get(rowIndex).use_;
      } else if (columnIndex == 1) {
         return channels_.get(rowIndex).name_;
      } else if (columnIndex == 2) {
         return channels_.get(rowIndex).exposure_;
      } else {
         return channels_.get(rowIndex).color_;
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
      } else {
         return Color.class;
      }
   }

   @Override
   public void setValueAt(Object value, int row, int columnIndex) {
      //use name exposure, color  
      if (columnIndex == 0) {
         channels_.get(row).use_ = (Boolean) value;
         //same for all other channels of the same camera_
         if (core_.getNumberOfCameraChannels() > 1) {
            for (int i = (int) (row - row % core_.getNumberOfCameraChannels()); i < (row /core_.getNumberOfCameraChannels() + 1) * core_.getNumberOfCameraChannels();row++ ) {
               channels_.get(row).use_ = (Boolean) value;
            }
         }       
      } else if (columnIndex == 1) {       
         channels_.get(row).name_ = (String) value;
      } else if (columnIndex == 2) {
         channels_.get(row).exposure_ = (Double) value;
         //same for all other channels of the same camera_
         if (core_.getNumberOfCameraChannels() > 1) {
            for (int i = (int) (row - row % core_.getNumberOfCameraChannels()); i < (row / core_.getNumberOfCameraChannels() + 1) * core_.getNumberOfCameraChannels(); row++) {
               channels_.get(row).exposure_ = (Double) value;
            }
         }
      } else {
         channels_.get(row).color_ = (Color) value;
      }
      ChannelUtils.storeChannelInfo(channels_);
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      return nCol != 1;
   }

   @Override
   public void tableChanged(TableModelEvent e) {
   }

 }

