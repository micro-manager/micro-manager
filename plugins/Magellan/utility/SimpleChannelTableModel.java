/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utility;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.prefs.Preferences;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import mmcorej.CMMCore;
import org.micromanager.MMOptions;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.TooltipTextMaker;

/**
 *
 * @author Henry
 */
public class SimpleChannelTableModel extends AbstractTableModel implements TableModelListener {

      private ArrayList<ChannelSpec> channels_;
      private final CMMCore core_;
      public final String[] COLUMN_NAMES = new String[]{
         "Configuration",
         "Exposure",
         "Color"
      };

      public SimpleChannelTableModel() {
         core_ = MMStudio.getInstance().getCore();
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
         return COLUMN_NAMES.length;
      }

      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }

     @Override
   public Object getValueAt(int rowIndex, int columnIndex) {
      if (channels_ != null && rowIndex < channels_.size()) {
         if (columnIndex == 0) {
            return channels_.get(rowIndex).config;
         } else if (columnIndex == 1) {
            return channels_.get(rowIndex).exposure;
         } else if (columnIndex == 3) {
            return channels_.get(rowIndex).color;
         }
      }
      return null;
   }

   @Override
   public Class getColumnClass(int c) {
      return getValueAt(0, c).getClass();
   }

   @Override
   public void setValueAt(Object value, int row, int col) {
      if (row >= channels_.size() || value == null) {
         return;
      }

      ChannelSpec channel = channels_.get(row);
      if (col == 0) {
         channel.config = value.toString();
      } else if (col == 1) {
         channel.exposure = ((Double) value);
      } else if (col == 2) {
         channel.color = (Color) value;
      }
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      return true;
   }

   /*
    * TODO: remember colors?
    */
   @Override
   public void tableChanged(TableModelEvent e) {
//      int row = e.getFirstRow();
//      if (row < 0) {
//         return;
//      }
//      int col = e.getColumn();
//      if (col < 0) {
//         return;
//      }
//      ChannelSpec channel = channels_.get(row);
//      TableModel model = (TableModel) e.getSource();
//      if (col == 6) {
//         Color color = (Color) model.getValueAt(row, col);
//         colorPrefs_.putInt("Color_" + acqEng_.getChannelGroup() + "_" + channel.config, color.getRGB());
//      }
   }


      public void addNewChannel() {
         ChannelSpec channel = new ChannelSpec();
         channel.config = "";
         String[] configs = core_.getAvailableConfigs(core_.getChannelGroup()).toArray();        
         if (configs.length > 0) {
            for (String config : configs) {
               boolean unique = true;
               for (ChannelSpec chan : channels_) {
                  if (config.contentEquals(chan.config)) {
                     unique = false;
                  }
               }
               if (unique) {
                  channel.config = config;
                  break;
               }
            }
            if (channel.config.length() == 0) {
               ReportingUtils.showMessage("No more channels are available\nin this channel group.");
            } else {
               channels_.add(channel);
            }
         }
      }

      public void removeChannel(int chIndex) {
         if (chIndex >= 0 && chIndex < channels_.size()) {
            channels_.remove(chIndex);
         }
      }

      /**
       * Updates the exposure time in the given preset
       *
       * @param channelGroup - if it does not match current channelGroup, no
       * action will be taken
       *
       * @param channel - preset for which to change exposire time
       * @param exposure - desired exposure time
       */
      public void setChannelExposureTime(String channelGroup, String channel,
              double exposure) {
         if (!channelGroup.equals(core_.getChannelGroup())) {
            return;
         }
         for (ChannelSpec ch : channels_) {
            if (ch.config.equals(channel)) {
               ch.exposure = exposure;
               this.fireTableDataChanged();
            }

         }
      }
   }

