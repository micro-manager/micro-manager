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

package org.micromanager.magellan.internal.misc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.filechooser.FileSystemView;
import org.micromanager.UserProfile;
import org.micromanager.magellan.internal.gui.GUI;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 *
 * @author Henry
 */
public class GlobalSettings {

   private static final String SAVING_DIR = "SAVING DIRECTORY";
   private static final String CHANNEL_OFFSET_PREFIX = "CHANNEL_OFFSET_";
   
   MutablePropertyMapView prefs_;
   private int[] chOffsets_ = new int[8];

   public GlobalSettings(UserProfile profile) {
      prefs_ = profile.getSettings(GlobalSettings.class);

      //load channel offsets
      try {
         for (int i = 0; i < 6; i++) {
            chOffsets_[i] = prefs_.getInteger(CHANNEL_OFFSET_PREFIX
                  + Magellan.getCore().getCurrentPixelSizeConfig() + i, 0);
         }
      } catch (Exception ex) {
         Log.log("couldnt get pixel size config", true);
      }
   }

   /**
    * Provide access to prefs_.
    */
   public MutablePropertyMapView getPrefs() {
      return prefs_;
   }

   public void storeSavingDirectory(String dir) {
      prefs_.putString(SAVING_DIR, dir);
   }
   
   public String getStoredSavingDirectory() {
      return prefs_.getString(SAVING_DIR, FileSystemView.getFileSystemView()
            .getHomeDirectory().getAbsolutePath());
   }

}