/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package channels;

import gui.SettingsDialog;
import java.awt.Color;
import java.util.ArrayList;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import misc.GlobalSettings;
import mmcorej.CMMCore;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class SimpleChannelTableModel extends AbstractTableModel implements TableModelListener {

      private ArrayList<ChannelSettings> channels_ = new ArrayList<ChannelSettings>();
      private final CMMCore core_;
      public final String[] COLUMN_NAMES = new String[]{
         "Configuration",
         "Color"
      };

      public SimpleChannelTableModel() {
         core_ = MMStudio.getInstance().getCore();
      }
      
      //TODO: freeze channelspec
      public String[] getActiveChannelNames() {
         int numCameraChannels = (int) ( GlobalSettings.getDemoMode() ? 6 :
                        MMStudio.getInstance().getCore().getNumberOfCameraChannels());
         int numActive = 0;
         
         for (ChannelSettings c : channels_) {
            numActive += c.useChannel ? 1 : 0;
         }
         if (numActive == 0) {
            if (numCameraChannels == 1) {
               return new String[]{"no channels active"};
            } else {
               //multichannel camera channels only
               if (GlobalSettings.getDemoMode()) {
                  return new String[] {"Violet","Blue","Green","Yellow","Red","FarRed"};
               }
               String[] names = new String[numCameraChannels];
               for (int i = 0; i < numCameraChannels; i++) {
                  names[i] = MMStudio.getInstance().getCore().getCameraChannelName(i);
               }
               return names;
            }
         } else {
            if (numCameraChannels == 1) {
               String[] names = new String[numActive];
               for (int i = 0; i < names.length; i++) {
                  names[i]  = channels_.get(i).config;
               }
               return names;
            } else {
               //tODO: multichannelcam with multiple channels, how are names generated?
               return null;
            }           
         }
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

      ChannelSettings channel = channels_.get(row);
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
         ChannelSettings channel = new ChannelSettings();
         channel.config = "";
         String[] configs = core_.getAvailableConfigs(core_.getChannelGroup()).toArray();        
         if (configs.length > 0) {
            for (String config : configs) {
               boolean unique = true;
               for (ChannelSettings chan : channels_) {
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
         for (ChannelSettings ch : channels_) {
            if (ch.config.equals(channel)) {
               ch.exposure = exposure;
               this.fireTableDataChanged();
            }

         }
      }
   }

