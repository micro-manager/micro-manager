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
import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class SimpleChannelTableModel extends AbstractTableModel implements TableModelListener {

      private static final String PREF_EXPOSURE = "EXPOSURE";
      private static final String PREF_COLOR = "COLOR";
      private static final String PREF_USE = "USE";
   
      private ArrayList<ChannelSetting> channels_ = new ArrayList<ChannelSetting>();
      private final CMMCore core_;
      private String channelGroup_ = null;
      public final String[] COLUMN_NAMES = new String[]{
         "Use",
         "Configuration",
         "Exposure",
      "Color"
   };

   public SimpleChannelTableModel() {
      core_ = MMStudio.getInstance().getCore();
      refreshChannels();
   }

   public void setChannelGroup(String group) {
      channelGroup_ = group;
   }

   private String[] getChannelNames() {
      if (channelGroup_ == null || channelGroup_.equals("")) {
         return new String[0];
      }
      StrVector configs = core_.getAvailableConfigs(channelGroup_);
      String[] names = new String[(int) configs.size()];
      for (int i = 0; i < names.length; i++) {
         names[i] = configs.get(i);
      }
      return names;
   }
   
   private void storeChannelInfo() {
      for (ChannelSetting c : channels_) {
         GlobalSettings.getInstance().storeDoubleInPrefs(PREF_EXPOSURE + c.name_, c.exposure_);
         GlobalSettings.getInstance().storeIntInPrefs(PREF_COLOR + c.name_, c.color_.getRGB());
         GlobalSettings.getInstance().storeBooleanInPrefs(PREF_USE + c.name_, c.use_);
      }
   }

   public void refreshChannels() {
      int numCameraChannels = (int) MMStudio.getInstance().getCore().getNumberOfCameraChannels();
      channels_ = new ArrayList<ChannelSetting>();
      if (numCameraChannels <= 1) {
         for (String name : getChannelNames()) {
            double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + name);
            exposure = exposure == 0 ? 10 : exposure;
            Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + name));
            boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + name);
            channels_.add(new ChannelSetting(name, exposure, color, use));
         }
      } else {
         for (int i = 0; i < numCameraChannels; i++) {
            String cameraChannelName = core_.getCameraChannelName(numCameraChannels);
            if (getChannelNames().length == 0) {
                  double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + cameraChannelName );
                  exposure = exposure == 0 ? 10 : exposure;
                  Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName ));
                  boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName);
                  channels_.add(new ChannelSetting(cameraChannelName, exposure, color, use));
            } else {
               for (String name : getChannelNames()) {
                  double exposure = GlobalSettings.getInstance().getDoubleInPrefs(PREF_EXPOSURE + cameraChannelName + "-" + name);
                  exposure = exposure == 0 ? 10 : exposure;
                  Color color = new Color(GlobalSettings.getInstance().getIntInPrefs(PREF_COLOR + cameraChannelName + "-" + name));
                  boolean use = GlobalSettings.getInstance().getBooleanInPrefs(PREF_USE + cameraChannelName + "-" + name);
                  channels_.add(new ChannelSetting(cameraChannelName + "-" + name, exposure, color, use));
               }
            }
         }
      }
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
      return COLUMN_NAMES.length;
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
      } else if (columnIndex == 1) {       
         channels_.get(row).name_ = (String) value;
      } else if (columnIndex == 2) {
         channels_.get(row).exposure_ = (Double) value;
      } else {
         channels_.get(row).color_ = (Color) value;
      }
      storeChannelInfo();
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      return nCol != 1;
   }

   @Override
   public void tableChanged(TableModelEvent e) {
   }

 }

