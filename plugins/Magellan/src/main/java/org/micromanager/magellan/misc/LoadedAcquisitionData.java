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

package org.micromanager.magellan.misc;

import org.micromanager.magellan.acq.MagellanImageCache;
import org.micromanager.magellan.datasaving.MultiResMultipageTiffStorage;
import org.micromanager.magellan.imagedisplay.MagellanDisplay;
import java.io.IOException;
import org.micromanager.magellan.misc.Log;

/**
 * 
 */
public class LoadedAcquisitionData {
   
   public LoadedAcquisitionData(String dir) {
      try {
         MultiResMultipageTiffStorage storage = new MultiResMultipageTiffStorage(dir);
         MagellanImageCache imageCache = new MagellanImageCache(storage);
         imageCache.setSummaryMetadata(storage.getSummaryMetadata());
         new MagellanDisplay(imageCache, null, storage.getSummaryMetadata(), storage, storage.getDisplaySettings());
      } catch (IOException ex) {
         Log.log("Couldn't open acquisition", true);
      }
   }
   
   
}
