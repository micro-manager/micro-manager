///////////////////////////////////////////////////////////////////////////////
//FILE:          RandomizePositionNames.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Cropper plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2016
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

package org.micromanager.randomizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;

/**
 *
 * @author nico
 */
public class RandomizePositionNames {
   private final Studio studio_;
   
   public RandomizePositionNames (Studio studio, DisplayWindow window) {
      studio_ = studio;
      
      try {
         DataProvider dp = window.getDataProvider();
         int nrP = dp.getAxisLength(Coords.P);
         Coords coords = dp.getAnyImage().getCoords();

         Set<String> wellNames = new LinkedHashSet<>();
         for (int pos = 0; pos < nrP; pos++) {
            Coords newCoords = coords.copyBuilder().p(pos).build();
            Image img = dp.getImage(newCoords);
            if (img.getMetadata().hasPositionName()) {
               String posName = img.getMetadata().getPositionName("");
               String well = posName.substring(0, posName.indexOf("-", 0));
               wellNames.add(well);
            }
         }
         if (wellNames.size() < 2) {
            studio_.logs().showError("Found one or no well(s), no need to randomize");
            return;
         }
         List<String> wellNameList = new ArrayList<>(wellNames.size());
         for (String well : wellNames) {
            wellNameList.add(well);
         }
         Set<Integer> randomNumbers = new LinkedHashSet<>(wellNames.size());
         while (randomNumbers.size() < wellNames.size()) {
            randomNumbers.add((int) Math.floor(Math.random() * wellNames.size()));
         }
         List<Integer> randomNumberList = new ArrayList<>(randomNumbers.size());
         for (Integer r : randomNumbers) {
            randomNumberList.add(r);
         }
         Map<Integer, String> r2Well = new HashMap<>(wellNames.size());
         Map<String, Integer> well2r = new HashMap<>(wellNames.size());
         while (wellNameList.size() > 0) {
            int index = (int) Math.floor(Math.random() * wellNameList.size());
            String well = wellNameList.remove(index);
            Integer random = randomNumberList.remove(index);
            r2Well.put(random, well);
            well2r.put(well, random);
         }
         Datastore newStore = studio_.data().createRAMDatastore();
         newStore.setSummaryMetadata(dp.getSummaryMetadata().copyBuilder().build());
         // TODO: fill with images with scrambled positionname
         // and display
         newStore.close();

      } catch (IOException ioe) {
         studio_.logs().showError(ioe, "IO Error in Position randomizer plugin");
      }


      
   }
     
}