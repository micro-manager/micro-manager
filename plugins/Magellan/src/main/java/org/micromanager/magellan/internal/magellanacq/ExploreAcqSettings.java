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
package org.micromanager.magellan.internal.magellanacq;

import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.magellan.internal.misc.GlobalSettings;

/**
 * Container for settings specific to explore acquisition
 * @author Henry
 */
public class ExploreAcqSettings  extends MagellanGenericAcquisitionSettings {
   
   private static final String EXPLORE_NAME_PREF = "Explore acq name";
   private static final String EXPLORE_DIR_PREF = "Explore acq dir";
   private static final String EXPLORE_Z_STEP = "Explore acq zStep";
   private static final String EXPLORE_TILE_OVERLAP = "Explore tile overlap";

   
   public ExploreAcqSettings(String dir, String name, String cGroup,  double zStep, double tileOverlap) {
      super(dir, name, cGroup, cGroup.equals("") ? null : new ChannelGroupSettings(cGroup), zStep, tileOverlap, false);

      //now that explore acquisition is being run, store values
      GlobalSettings.getInstance().storeStringInPrefs(EXPLORE_DIR_PREF, dir);
      GlobalSettings.getInstance().storeStringInPrefs(EXPLORE_NAME_PREF, name);
      GlobalSettings.getInstance().storeDoubleInPrefs(EXPLORE_Z_STEP, zStep_);
      GlobalSettings.getInstance().storeDoubleInPrefs(EXPLORE_TILE_OVERLAP, tileOverlap);
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
