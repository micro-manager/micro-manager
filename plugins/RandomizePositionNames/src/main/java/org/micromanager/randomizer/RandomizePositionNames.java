///////////////////////////////////////////////////////////////////////////////
//FILE:          RandomizePositionNames.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     RandomizePositionNames plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2019
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.display.DisplayWindow;

/**
 * Plugin that randomizes the position names to assist in blind scoring.
 * It requires data generated using the HCS plugin and will replace the 
 * well in the name with a random number.  The keys relating numbers back 
 * to wells are put in the summarymetadata, so do not look at those until
 * you are done scoring the images.
 * 
 * @author nico
 */
public class RandomizePositionNames {
   private final Studio studio_;
   private static final String KEYFILENAME = "Key.txt";
   
   public RandomizePositionNames (Studio studio, DisplayWindow window) {
      studio_ = studio;
      
      try {
         DataProvider dp = window.getDataProvider();
         int nrP = dp.getNextIndex(Coords.P);
         Coords coords = dp.getAnyImage().getCoords();

         Set<String> wellNames = new LinkedHashSet<>();
         for (int pos = 0; pos < nrP; pos++) {
            Coords newCoords = coords.copyBuilder().p(pos).build();
            Image img = dp.getImage(newCoords);
            if (img == null) {
               studio.alerts().postAlert("Randomizer", this.getClass(), "Missing position " + pos);
            } else {
               if (img.getMetadata().hasPositionName()) {
                  String posName = img.getMetadata().getPositionName("");
                  String well = posName.substring(0, posName.indexOf("-", 0));
                  wellNames.add(well);
               }
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
         // Add keys to UserData
         PropertyMap.Builder udb = dp.getSummaryMetadata().getUserData().copyBuilder();
         for (Map.Entry<Integer, String> entry : r2Well.entrySet()) {
            udb.putString("Key: " + entry.getKey(), entry.getValue());
         }
         newStore.setSummaryMetadata(dp.getSummaryMetadata().copyBuilder().
                 userData(udb.build()).build());
         // fill with images with scrambled positionname
         // and show them
         int newPos = -1;
         for (Integer r : r2Well.keySet()) {
            String well = r2Well.get(r);
            for (int pos = 0; pos < nrP; pos++) {
               Coords oldCoords = coords.copyBuilder().p(pos).build();
               Image img = dp.getImage(oldCoords);
               if (img != null && img.getMetadata().hasPositionName()) {
                  String posName = img.getMetadata().getPositionName("");
                  String wellImg = posName.substring(0, posName.indexOf("-", 0));
                  if (wellImg.equals(well)) {                     
                     newPos++; 
                     for (int c = 0; c < dp.getNextIndex(Coords.P); c++) {
                        for (int t = 0; t < dp.getNextIndex(Coords.T); t++) {
                           oldCoords = coords.copyBuilder().p(pos).c(c).t(t).build();
                           img = dp.getImage(oldCoords);
                           if (img != null && img.getMetadata().hasPositionName()) {
                              posName = img.getMetadata().getPositionName("");
                              wellImg = posName.substring(0, posName.indexOf("-", 0));
                              if (wellImg.equals(well)) {
                                 Metadata newMetadata = img.getMetadata().copyBuilderWithNewUUID().
                                         positionName("" + r + posName.substring(posName.indexOf("-", 0))).
                                         build();
                                 Coords newCoords = oldCoords.copyBuilder().p(newPos).build();
                                 Image newImg = img.copyWith(newCoords, newMetadata);
                                 newStore.putImage(newImg);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         DisplayWindow newDisplay = studio_.displays().createDisplay(newStore);
         newDisplay.setDisplaySettings(window.getDisplaySettings());
         studio.displays().manage(newStore);
         
         try {
            if (dp instanceof Datastore) {
               Datastore ds = (Datastore) dp;
               String savePath = ds.getSavePath();
               if (savePath != null && savePath.length() > 0) {
                  File keyFile = new File(savePath, KEYFILENAME);
                  if (keyFile.exists()) {
                     keyFile.delete();
                  }
                  try (BufferedWriter output = new BufferedWriter(new FileWriter(keyFile))) {
                     Set<Map.Entry<Integer, String>> entrySet = r2Well.entrySet();
                     for (Entry<Integer, String> entry : entrySet) {
                        output.append(entry.getKey().toString()).append(" - ").
                                append(entry.getValue()).append(System.getProperty("line.separator"));
                     }
                  }
               }
            }
         } catch (IOException ioe) {
            studio_.logs().showError("Key file not saved.  Be sure to check the Summarymetadata before closing");
         }

      } catch (IOException ioe) {
         studio_.logs().showError(ioe, "IO Error in Position randomizer plugin");
      }


      
   }
     
}