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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.micromanager.magellan.internal.magellanacq.MagellanDataManager;
import org.micromanager.magellan.internal.misc.Log;

/**
 *
 */
public class LoadedAcquisitionData {

   public LoadedAcquisitionData(String dir) {
      try {
         MagellanDataManager dataManager = new MagellanDataManager(dir);

         //Iterate through all image keys and expand scrollbars to appropriate sizes
         Set<HashMap<String, Integer>> axesList = dataManager.getAxesSet();
         HashSet<String> axesNames = new HashSet<String>();
         for (HashMap<String, Integer> ax : axesList) {
            axesNames.addAll(ax.keySet());
         }
         if (axesNames.contains(MagellanMD.POSITION_AXIS)) {
            axesNames.remove(MagellanMD.POSITION_AXIS);
         }
         HashMap<String, Integer> axisMins = new HashMap<String, Integer>();
         HashMap<String, Integer> axisMaxs = new HashMap<String, Integer>();
         for (String axis : axesNames) {
            for (HashMap<String, Integer> ax : axesList) {
               if (!axisMins.containsKey(axis)) {
                  axisMins.put(axis, ax.get(axis));
                  axisMaxs.put(axis, ax.get(axis));
               }
               axisMins.put(axis, Math.min(ax.get(axis), axisMins.get(axis)));
               axisMaxs.put(axis, Math.max(ax.get(axis), axisMaxs.get(axis)));
            }
         }
         dataManager.initializeViewerToLoaded(axisMins, axisMaxs);
                  
      } catch (IOException ex) {
         Log.log("Couldn't open acquisition", true);
      }
   }

}
