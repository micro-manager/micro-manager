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
package org.micromanager.magellan.internal.acq;

import org.micromanager.magellan.api.MagellanAcquisitionSettingsAPI;
import org.micromanager.magellan.internal.channels.MagellanChannelSpec;
import org.micromanager.magellan.internal.misc.GlobalSettings;

/**
 * Container for settings specific to explore acquisition
 * @author Henry
 */
public class ExploreAcqSettings  extends AcquisitionSettingsBase {
   
   private static final String EXPLORE_NAME_PREF = "Explore acq name";
   private static final String EXPLORE_DIR_PREF = "Explore acq dir";
   private static final String EXPLORE_Z_STEP = "Explore acq zStep";
   private static final String EXPLORE_TILE_OVERLAP = "Explore tile overlap";


   public ExploreAcqSettings(double zStep, double overlapPercent, String dir, String name, String channelGroup) {
      zStep_ = zStep;
      dir_ = dir;
      name_ = name;   
      tileOverlap_ = overlapPercent;
      //channels is all available channels for group
      if (channelGroup.equals("")) {
         channels_ = null;
      } else {
         channels_ = new MagellanChannelSpec(channelGroup);
      }
      
      //now that explore acquisition is being run, store values
      GlobalSettings.getInstance().storeStringInPrefs(EXPLORE_DIR_PREF, dir);
      GlobalSettings.getInstance().storeStringInPrefs(EXPLORE_NAME_PREF, name);
      GlobalSettings.getInstance().storeDoubleInPrefs(EXPLORE_Z_STEP, zStep_);
      GlobalSettings.getInstance().storeDoubleInPrefs(EXPLORE_TILE_OVERLAP, overlapPercent);
   }
   
   public static String getNameFromPrefs() {
      return GlobalSettings.getInstance().getStringInPrefs(EXPLORE_NAME_PREF, "Untitled Explore Acquisition" );
   } 
   
   public static double getZStepFromPrefs() {
      return GlobalSettings.getInstance().getDoubleInPrefs(EXPLORE_Z_STEP, 1);
   }

   public static double getExploreTileOverlapFromPrefs() {
      return GlobalSettings.getInstance().getDoubleInPrefs(EXPLORE_TILE_OVERLAP, 0);
   }


   
}
