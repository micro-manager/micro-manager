/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.channels;

import java.awt.Color;
import java.util.Random;
import org.micromanager.acqj.api.ChannelGroupSettings;
import org.micromanager.acqj.api.ChannelSetting;
import org.micromanager.magellan.internal.magellanacq.MagellanGUIAcquisitionSettings;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.GlobalSettings;
import org.micromanager.magellan.internal.misc.Log;

/**
 * This wraps the Acqusition engines channel group settings to enable
 * Storage/retrieval of dispaly preferences
 * @author henrypinkard
 */
public class MagellanChannelGroupSettings extends ChannelGroupSettings {

   private static final String PREF_EXPOSURE = "EXPOSURE";
   private static final String PREF_COLOR = "COLOR";
   private static final String PREF_USE = "USE";
   private static final String PREF_OFFSET = "OFFSET";
   private static final Color[] DEFAULT_COLORS = {new Color(160, 32, 240), Color.blue, Color.green, Color.yellow, Color.red, Color.pink};

   public MagellanChannelGroupSettings(String channelGroup) {
      super(channelGroup);
   }

   @Override
   public void updateChannelGroup(String channelGroup) {
      super.updateChannelGroup(channelGroup);
      for (ChannelSetting c : channels_) {
         setValuesFromPrefs(c);
      }
   }

   private void setValuesFromPrefs(ChannelSetting setting) {
      String prefix = MagellanGUIAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP"
              + setting.group_ + "CHANNELNAME" + setting.name_;
      setting.setProperty("Color",
              new Color(GlobalSettings.getInstance().getIntInPrefs(
                      prefix + PREF_COLOR, DEFAULT_COLORS[new Random().nextInt(DEFAULT_COLORS.length)].getRGB())));

      try {
         setting.exposure_ = GlobalSettings.getInstance().getDoubleInPrefs(prefix + PREF_EXPOSURE, Magellan.getCore().getExposure());
      } catch (Exception ex) {
         Log.log(ex);
      }

      setting.offset_ = GlobalSettings.getInstance().getDoubleInPrefs(prefix + PREF_OFFSET, 0.0);
      setting.use_ = GlobalSettings.getInstance().getBooleanInPrefs(prefix + PREF_USE, true);
   }

   public void storeValuesInPrefs() {
      for (ChannelSetting setting : channels_) {
         String prefix = MagellanGUIAcquisitionSettings.PREF_PREFIX
                 + "CHANNELGROUP" + setting.group_ + "CHANNELNAME" + setting.name_;
         GlobalSettings.getInstance().storeBooleanInPrefs(prefix + PREF_USE, setting.use_);
         GlobalSettings.getInstance().storeDoubleInPrefs(prefix + PREF_EXPOSURE, setting.exposure_);
         GlobalSettings.getInstance().storeIntInPrefs(prefix + PREF_COLOR,
                 ((Color) setting.getProperty("Color")).getRGB());
         GlobalSettings.getInstance().storeDoubleInPrefs(prefix + PREF_OFFSET, setting.offset_);
      }
   }

}
