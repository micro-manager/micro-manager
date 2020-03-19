///////////////////////////////////////////////////////////////////////////////
//FILE:          ChannelSpec.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 10, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id: ChannelSpec.java 12081 2013-11-06 21:27:25Z nico $
//
package org.micromanager.magellan.internal.channels;
import java.awt.Color; 
import java.util.Random;
import org.micromanager.magellan.internal.acq.MagellanGUIAcquisitionSettings;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.GlobalSettings;
import org.micromanager.magellan.internal.misc.Log;

/**
 * Channel acquisition protocol. 
 */
public class ChannelSetting {

   private static final String PREF_EXPOSURE = "EXPOSURE";
   private static final String PREF_COLOR = "COLOR";
   private static final String PREF_USE = "USE";
   private static final String PREF_OFFSET = "OFFSET";
   private static final Color[] DEFAULT_COLORS = {new Color(160, 32, 240), Color.blue, Color.green, Color.yellow, Color.red, Color.pink};

    
   final public String group_;
   final public String config_; // Configuration setting name
   final public String name_; 
   public double exposure_; // ms
   public double offset_;
   public Color color_;
   public boolean use_ = true;
   final public boolean uniqueEvent_;

   public ChannelSetting(String group, String config, String name, boolean uniqueEvent) {       
      /**
       * Automatically load channel settings in preferences
       */
       String prefix = MagellanGUIAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP" + group + "CHANNELNAME" + name;
      group_ = group;
      color_ = new Color(GlobalSettings.getInstance().getIntInPrefs(prefix + PREF_COLOR, DEFAULT_COLORS[new Random().nextInt(DEFAULT_COLORS.length)].getRGB()));
      config_ = config;
      name_ = name;
       try {
           exposure_ = GlobalSettings.getInstance().getDoubleInPrefs(prefix + PREF_EXPOSURE, Magellan.getCore().getExposure());
       } catch (Exception ex) {
          Log.log(ex);
       }
      offset_ = GlobalSettings.getInstance().getDoubleInPrefs(prefix + PREF_OFFSET, 0.0);
      use_ = GlobalSettings.getInstance().getBooleanInPrefs(prefix + PREF_USE, true);
      uniqueEvent_ = uniqueEvent; // true for only first on multichannel camera
   }
   
    public void storeChannelInfoInPrefs() {
        String prefix = MagellanGUIAcquisitionSettings.PREF_PREFIX + "CHANNELGROUP" + group_ + "CHANNELNAME" + name_;
        GlobalSettings.getInstance().storeBooleanInPrefs(prefix + PREF_USE, use_);
        GlobalSettings.getInstance().storeDoubleInPrefs(prefix + PREF_EXPOSURE, exposure_);
        GlobalSettings.getInstance().storeIntInPrefs(prefix + PREF_COLOR, color_.getRGB());
        GlobalSettings.getInstance().storeDoubleInPrefs(prefix + PREF_OFFSET, offset_);
    }
}
