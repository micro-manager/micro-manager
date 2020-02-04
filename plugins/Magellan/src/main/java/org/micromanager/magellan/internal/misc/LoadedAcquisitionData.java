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

import org.micromanager.magellan.internal.imagedisplay.MagellanImageCache;
import java.io.IOException;
import java.util.List;
import org.micromanager.magellan.internal.imagedisplay.DisplaySettings;
import org.micromanager.magellan.internal.imagedisplay.MagellanDisplayController;
import org.micromanager.magellan.internal.misc.Log;

/**
 * 
 */
public class LoadedAcquisitionData {
   
   public LoadedAcquisitionData(String dir) {
      try {
         MagellanImageCache imageCache = new MagellanImageCache(dir);
         int minZ = imageCache.getMinZIndexLoadedData();
         int maxZ = imageCache.getMaxZIndexLoadedData();
         List<String> channelNames = imageCache.getChannelNames();
         int nFrames = imageCache.getNumChannels();
         MagellanDisplayController controller = new MagellanDisplayController(imageCache, new DisplaySettings(imageCache.getDisplayJSON()), null);
         controller.setLoadedDataScrollbarBounds(channelNames, nFrames, minZ, maxZ);
      } catch (IOException ex) {
         Log.log("Couldn't open acquisition", true);
      }
   }
   
   
}
