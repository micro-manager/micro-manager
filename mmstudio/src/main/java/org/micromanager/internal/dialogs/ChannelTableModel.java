package org.micromanager.internal.dialogs;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import org.micromanager.Studio;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.display.internal.RememberedSettings;
import org.micromanager.events.internal.ChannelColorEvent;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TooltipTextMaker;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Data representation class for the channels list in the MDA dialog.
 */
public final class ChannelTableModel extends AbstractTableModel  {

   private static final long serialVersionUID = 3290621191844925827L;
   private final Studio studio_;
   private final AcquisitionEngine acqEng_;
   private final MutablePropertyMapView settings_;
   // Not sure why, but the acqEngine API requires an ArrayList rather than a List
   private final ArrayList<ChannelSpec> channels_;
   public final String[] COLUMN_NAMES = new String[]{
      "Use?",
      "Configuration",
      "Exposure",
      "Z-offset",
      "Z-stack",
      "Skip Fr.",
      "Color"
   };
   private final String[] TOOLTIPS = new String[]{
      "Toggle channel/group on/off",
      "Choose preset property values for channel or group",
      "Set exposure time in ms",
      TooltipTextMaker.addHTMLBreaksForTooltip("Set a Z offset specific to this channel/group (the main "
      + "object in one of the channels/groups is in a different focal plane from the other channels/groups"),
      "Collect images in multiple Z planes?",
      TooltipTextMaker.addHTMLBreaksForTooltip("Setting 'Skip Frame' to a number other than "
      + "0 will cause the acquisition to 'skip' taking images in "
      + "that channel (after taking the first image) for the indicated "
      + "number of time intervals. The 5D-Image Viewer will 'fill in' these skipped "
      + "frames with the previous image. In some situations it may be "
      + "desirable to acquire certain channels at lower sampling rates, "
      + "to reduce photo-toxicity and to save disk space. "),
      "Select channel/group color for display in viewer"};

   public String getToolTipText(int columnIndex) {
      return TOOLTIPS[columnIndex];
   }

   public ChannelTableModel(Studio studio, AcquisitionEngine eng) {
      studio_ = studio;
      acqEng_ = eng;
      settings_ = studio_.profile().getSettings(ChannelTableModel.class);
      channels_ = new ArrayList<>(12);
      cleanUpConfigurationList();
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
            return channels_.get(rowIndex).useChannel();
         } else if (columnIndex == 1) {
            return channels_.get(rowIndex).config();
         } else if (columnIndex == 2) {
            return channels_.get(rowIndex).exposure();
         } else if (columnIndex == 3) {
            return channels_.get(rowIndex).zOffset();
         } else if (columnIndex == 4) {
            return channels_.get(rowIndex).doZStack();
         } else if (columnIndex == 5) {
            return channels_.get(rowIndex).skipFactorFrame();
         } else if (columnIndex == 6) {
            return channels_.get(rowIndex).color();
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
      ChannelSpec.Builder cb = channel.copyBuilder();
      if (col == 0) {
         cb.useChannel((Boolean) value);
      } else if (col == 1) {
         cb.config (value.toString());
         ChannelSpec cs = ChannelSpec.fromJSONStream(
                 settings_.getString(channelProfileKey(acqEng_.getChannelGroup(),
                         value.toString()), ""));
         if (cs == null) {
            // Our fallback color is the colorblind-friendly color for our
            // current row index.
            cb.color( new Color(ColorPalettes.getFromDefaultPalette(row).getRGB()));
            cb.exposure(10.0);
         }
         else {
            cb.color(cs.color());
            cb.exposure(cs.exposure());
         }
         channels_.set(row, cb.build());
         this.fireTableCellUpdated(row, 2);
         this.fireTableCellUpdated(row, 6);
      } else if (col == 2) {
         cb.exposure (((Double) value));
         if (AcqControlDlg.getShouldSyncExposure()) {
            studio_.app().setChannelExposureTime(acqEng_.getChannelGroup(),
                    channel.config(), (Double) value);
         } else {
            this.setChannelExposureTime(acqEng_.getChannelGroup(),
                    channel.config(), (Double) value);
         }
      } else if (col == 3) {
         cb.zOffset((Double) value);
      } else if (col == 4) {
         cb.doZStack((Boolean) value);
      } else if (col == 5) {
         cb.skipFactorFrame((Integer) value);
      } else if (col == 6) {
         if (!channel.color().equals((Color) value)) {
            studio_.events().post(new ChannelColorEvent(
                    channel.channelGroup(), channel.config(), (Color) value));
         }
      }
      channel = cb.build();
      channels_.set(row, channel);

      acqEng_.setChannel(row, channel);
      this.fireTableChanged(new TableModelEvent(this));
   }

   @Override
   public boolean isCellEditable(int nRow, int nCol) {
      if (nCol == 4) {
         return acqEng_.isZSliceSettingEnabled();
      }

      return true;
   }

   public ArrayList<ChannelSpec> getChannels() {
      return channels_;
   }

   /**
    * Adds a new channel to the list in the MDA window
    */
   public void addNewChannel() {
      ChannelSpec.Builder cb = new ChannelSpec.Builder();
      if (acqEng_.getChannelConfigs().length > 0) {
         for (String config : acqEng_.getChannelConfigs()) {
            boolean unique = true;
            for (ChannelSpec chan : channels_) {
               if (config.contentEquals(chan.config())) {
                  unique = false;
               }
            }
            if (unique) {
               cb.config(config);
               break;
            }
         }
         String config = cb.build().config();
         if (config.length() == 0) {
            ReportingUtils.showMessage("No more channels are available\nin this channel group.");
         } else {
            // Pick a non-white default color if possible.
            Color defaultColor = ColorPalettes.getFromDefaultPalette(channels_.size());
            cb.channelGroup(acqEng_.getChannelGroup());
            cb.color(RememberedSettings.loadChannel(studio_,
                    acqEng_.getChannelGroup(), config, defaultColor).getColor());
            cb.exposure(this.getChannelExposureTime(
                  acqEng_.getChannelGroup(), config, 10.0));
            channels_.add(cb.build());
         }
      }
      storeChannels();
   }

   public void removeChannel(int chIndex) {
      if (chIndex >= 0 && chIndex < channels_.size()) {
         channels_.remove(chIndex);
      }
      storeChannels();
   }

   /**
    * Used to change the order of the channels in the MDA window
    * @param rowIdx Row nr (zero-based) of the row to be moved
    * @return row nr where the selected row ended up
    */
   public int rowDown(int rowIdx) {
      if (rowIdx >= 0 && rowIdx < channels_.size() - 1) {
         ChannelSpec channel = channels_.get(rowIdx);
         channels_.remove(rowIdx);
         channels_.add(rowIdx + 1, channel);
         return rowIdx + 1;
      }
      storeChannels();
      return rowIdx;
   }

   /**
    * Used to change the order of the channels in the Window
    * @param rowIdx Row nr (0-based) of the row to move up
    * @return Row nr where this row ended up
    */
   public int rowUp(int rowIdx) {
      if (rowIdx >= 1 && rowIdx < channels_.size()) {
         ChannelSpec channel = channels_.get(rowIdx);
         channels_.remove(rowIdx);
         channels_.add(rowIdx - 1, channel);
         return rowIdx - 1;
      }
      storeChannels();
      return rowIdx;
   }

   public String[] getAvailableChannels() {
      return acqEng_.getChannelConfigs();
   }

   /**
    * Remove all channels from the list which are not compatible with
    * the current acquisition settings
    */
   public void cleanUpConfigurationList() {
      String channelGroup = "";
      List<String> configNames = new ArrayList<>(channels_.size());
      for (Iterator<ChannelSpec> it = channels_.iterator(); it.hasNext(); ) {
         ChannelSpec cs = it.next();
         if (!cs.config().contentEquals("")) {
            channelGroup = cs.channelGroup();
            configNames.add(cs.config());
            // write this config to the profile
            settings_.putString(channelProfileKey(cs.channelGroup(), cs.config()),
                    ChannelSpec.toJSONStream(cs));
            if (!acqEng_.isConfigAvailable(cs.config())) {
               it.remove();
            }
         }
      }
      // Stores the config names that we had for the old channelGroup
      settings_.putStringList("CG:" + channelGroup, configNames);

      // Restore channels from profile
      String newChannelGroup = acqEng_.getChannelGroup();
      if (!channelGroup.equals(newChannelGroup)) {
         List<String> newConfigNames = settings_.getStringList("CG:" + newChannelGroup);
         for (String newConfig : newConfigNames) {
            ChannelSpec cs = ChannelSpec.fromJSONStream(
                    settings_.getString(channelProfileKey(newChannelGroup, newConfig), ""));
            channels_.add(cs);
         }
         acqEng_.setChannels(channels_);
         fireTableDataChanged();
      }
   }

   /**
    * reports if the same channel name is used twice
    * @return true when the list of channels contains the same channel name twice
    */
   public boolean duplicateChannels() {
      for (int i = 0; i < channels_.size() - 1; i++) {
         for (int j = i + 1; j < channels_.size(); j++) {
            if (channels_.get(i).config().equals(channels_.get(j).config())) {
               return true;
            }
         }
      }
      return false;
   }
   
   /**
    * Updates the exposure time in the given preset 
    * 
    * @param channelGroup - if it does not match current channelGroup, 
    * no action will be taken
    * 
    * @param channel - preset for which to change exposire time
    * @param exposure - desired exposure time
    */
   public void setChannelExposureTime(String channelGroup, String channel, 
           double exposure) {
      if (!channelGroup.equals(acqEng_.getChannelGroup()))
         return;
      for (int row = 0; row < channels_.size(); row++) {
         ChannelSpec cs = channels_.get(row);
         if (cs.config().equals(channel)) {
            channels_.set(row, cs.copyBuilder().exposure(exposure).build());
            this.fireTableCellUpdated(row, 2);
            return;
         }
      }
   }

   /**
    * Returns the exposure time of the given preset
    *
    * @param channelGroup - if it does not match current channelGroup,
    * default exposure will be returns
    * @param channel - preset for which to change exposure time
    * @param defaultExposure - return when no match was found
    */
   public double getChannelExposureTime(String channelGroup, String channel,
                                      double defaultExposure) {
      if (!channelGroup.equals(acqEng_.getChannelGroup()))
         return defaultExposure;
      for (int row = 0; row < channels_.size(); row++) {
         ChannelSpec cs = channels_.get(row);
         if (cs.config().equals(channel)) {
            return channels_.get(row).exposure();
         }
      }
      return defaultExposure;
   }

   /**
    * Updates the color of the specified channel
    *
    * @param channelGroup  Channelgroup of the channel
    * @param channelName   Name of the channel
    * @param color         New color of the channel
    */
   public void setChannelColor(String channelGroup, String channelName, Color color) {
      if (!channelGroup.equals(acqEng_.getChannelGroup()))
         return;
      for (int row = 0; row < channels_.size(); row++) {
         ChannelSpec cs = channels_.get(row);
         if (cs.config().equals(channelName)) {
            channels_.set(row, cs.copyBuilder().color(color).build());
            this.fireTableCellUpdated(row, 6);
            return;
         }
      }
      // not found, should be safe to ignore
   }

   public void storeChannels() {
      String channelGroup = acqEng_.getChannelGroup();
      List<String> configNames = new ArrayList<>(channels_.size());
      for (ChannelSpec cs : channels_) {
         if (!cs.config().contentEquals("")) {
            if (cs.channelGroup().isEmpty()) {
               cs = cs.copyBuilder().channelGroup(channelGroup).build();
            };
            configNames.add(cs.config());
            // write this config to the profile
            settings_.putString(channelProfileKey(cs.channelGroup(), cs.config()),
                    ChannelSpec.toJSONStream(cs));
         }
      }
      // Stores the config names that we had for the old channelGroup
      settings_.putStringList("CG:" + channelGroup, configNames);
   }

    private static String channelProfileKey(String channelGroup, String config) {
      return channelGroup + "-" + config;
   }
}
