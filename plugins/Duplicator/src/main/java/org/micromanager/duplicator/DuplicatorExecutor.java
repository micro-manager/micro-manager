///////////////////////////////////////////////////////////////////////////////
//FILE:          DuplicatorExecutor.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Duplicator plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2016-2019
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

package org.micromanager.duplicator;

import ij.gui.Roi;
import ij.process.ImageProcessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingWorker;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

/**
 * Does the actual Duplication.
 *
 * @author nico
 */
public class DuplicatorExecutor extends SwingWorker<Void, Void> {
   private final Studio studio_;
   private final DisplayWindow theWindow_;
   private final String newName_;
   private final Map<String, Integer> mins_;
   private final Map<String, Integer> maxes_;
   private final LinkedHashMap<String, Boolean> channels_;
   private final Datastore.SaveMode saveMode_;
   private final String filePath_;
   
   /**
    * Performs the actual creation of a new image with reduced content.
    *
    * @param studio - instance of Micro-Manager Studio
    * @param theWindow - original window to be copied
    * @param newName - name for the copy
    * @param mins - Map with new (or unchanged) minima for the given axis
    * @param maxes - Map with new (or unchanged) maxima for the given axis
    * @param channels - Map with names of channels and flag whether or not they should be duplicated
    */
   public DuplicatorExecutor(final Studio studio, 
           final DisplayWindow theWindow, 
           final String newName, 
           final Map<String, Integer> mins,
           final Map<String, Integer> maxes,
           final LinkedHashMap<String, Boolean> channels,
           final Datastore.SaveMode saveMode,
           final String filePath) {

      studio_ = studio;
      theWindow_ = theWindow;
      newName_ = newName;
      mins_ = mins;
      maxes_ = maxes;
      channels_ = channels;
      saveMode_ = saveMode;
      filePath_ = filePath;
   }

   @Override
   protected Void doInBackground() {
      
      // TODO: provide options for disk-backed datastores
      DataProvider oldStore = theWindow_.getDataProvider();
      Datastore tmpStore = null;
      try {
         if (saveMode_ == null) {
            tmpStore = studio_.data().createRAMDatastore();
         } else if (saveMode_ == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
            tmpStore = studio_.data().createSinglePlaneTIFFSeriesDatastore(filePath_);
         } else if (saveMode_ == Datastore.SaveMode.MULTIPAGE_TIFF) {
            // TODO: read options for parameters here
            tmpStore = studio_.data().createMultipageTIFFDatastore(filePath_, true, true);
         } else if (saveMode_ == Datastore.SaveMode.ND_TIFF) {
            tmpStore = studio_.data().createNDTIFFDatastore(filePath_);
         }
      } catch (IOException ioe) {
         studio_.logs().showError(ioe, "Failed to open new datastore on disk");
         return null;
      }

      final Datastore newStore = tmpStore;
      final DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
      copyDisplay.setCustomTitle(newName_);
      final DisplaySettings originalDisplaySettings = theWindow_.getDisplaySettings();
      final DisplaySettings.Builder newDisplaySettingsBuilder =
            theWindow_.getDisplaySettings().copyBuilder();

      // TODO: use Overlays instead
      Roi roi = theWindow_.getImagePlus().getRoi();
      
      Coords.CoordsBuilder newSizeCoordsBuilder = studio_.data().coordsBuilder();
      for (String axis : oldStore.getAxes()) {
         newSizeCoordsBuilder.index(axis, oldStore.getNextIndex(axis) - 1);
      }
      SummaryMetadata oldMetadata = oldStore.getSummaryMetadata();
      List<String> channelNames = oldMetadata.getChannelNameList();
      if (channels_ != null) {
         List<ChannelDisplaySettings> channelDisplaySettings = new ArrayList<>();
         List<String> chNameList = new ArrayList<>();
         int index = 0;
         for (Map.Entry<String, Boolean> channel : channels_.entrySet()) {
            if (channel.getValue()) {
               chNameList.add(channel.getKey());
               channelDisplaySettings.add(originalDisplaySettings.getChannelSettings(index));
            }
            index++;
         }
         channelNames = chNameList;
         newDisplaySettingsBuilder.channels(channelDisplaySettings);
      }
      newSizeCoordsBuilder.channel(channelNames.size());
      float  nrToBeCopied = 1;
      for (String axis : oldStore.getAxes()) {
         if (channels_ != null && channels_.size() > 0) {
            nrToBeCopied *= channels_.size();
         }
         if (mins_.containsKey(axis)) {
            int min = mins_.get(axis);
            int max = maxes_.get(axis);
            newSizeCoordsBuilder.index(axis, max - min);
            nrToBeCopied *= (max - min + 1);
         }
      }

      SummaryMetadata metadata = oldMetadata.copyBuilder()
              .channelNames(channelNames)
              .intendedDimensions(newSizeCoordsBuilder.build())
              .build();
      copyDisplay.setDisplaySettings(newDisplaySettingsBuilder.build());

      try {
         newStore.setSummaryMetadata(metadata);

         Iterable<Coords> unorderedImageCoords = oldStore.getUnorderedImageCoords();
         int nrCopied = 0;
         for (Coords oldCoord : unorderedImageCoords) {
            List<String> oldAxes = oldStore.getAxes();
            boolean copy = !oldAxes.contains(Coords.CHANNEL);
            for (String axis : oldStore.getAxes()) {
               if (axis.equals(Coords.CHANNEL)) {
                  int index = 0;
                  for (Map.Entry<String, Boolean> channel : channels_.entrySet()) {
                     if (channel.getValue() && oldCoord.getIndex(axis) == index) {
                        copy = true;
                        continue;
                     }
                     index++;
                  }
               }
            }
            for (String axis : oldStore.getAxes()) {
               if (copy && mins_.containsKey(axis) && maxes_.containsKey(axis)) {
                  if (oldCoord.getIndex(axis) < (mins_.get(axis))) {
                     copy = false;
                  }
                  if (oldCoord.getIndex(axis) > maxes_.get(axis)) {
                     copy = false;
                  }
               }
            }
            if (copy) {
               Coords.CoordsBuilder newCoordBuilder = oldCoord.copyBuilder();
               for (String axis : oldCoord.getAxes()) {
                  if (axis.equals(Coords.CHANNEL)) {
                     if (channels_ != null && channels_.size() > 0) {
                        int chIndex = oldCoord.getIndex(axis);
                        if (chIndex < oldMetadata.getChannelNameList().size()) {
                           String channelName = oldMetadata.getChannelNameList().get(chIndex);
                           if (channelNames.contains(channelName)) {
                              int newIndex = channelNames.indexOf(channelName);
                              newCoordBuilder.index(axis, newIndex);
                           }
                        }
                     }
                  }
                  if (mins_.containsKey(axis)) {
                     newCoordBuilder.index(axis, oldCoord.getIndex(axis) - mins_.get(axis));
                  }
               }
               Image img = oldStore.getImage(oldCoord);
               Coords newCoords = newCoordBuilder.build();
               Image newImgShallow = img.copyAtCoords(newCoords);
               if (roi != null) {
                  ImageProcessor ip = studio_.data().ij().createProcessor(img);
                  if (ip != null) {
                     ip.setRoi(roi);
                     ImageProcessor copyIp = ip.crop();
                     newImgShallow = studio_.data().createImage(copyIp.getPixels(), 
                             copyIp.getWidth(), copyIp.getHeight(), 
                             img.getBytesPerPixel(), img.getNumComponents(), 
                             newCoords, newImgShallow.getMetadata());
                  } else {
                     throw new DuplicatorException(
                           "Unsupported pixel type.  Can only copy 8 or 16 bit images.");
                  }
               }
               newStore.putImage(newImgShallow);
               nrCopied++;
               try {
                  setProgress((int) (nrCopied / nrToBeCopied * 100.0));
               } catch (IllegalArgumentException iae) {
                  System.out.println("Value was: " + (int) (nrCopied / nrToBeCopied * 100.0));
               }
               
            }
         }

      } catch (DatastoreFrozenException ex) {
         studio_.logs().showError("Can not add data to frozen datastore");
      } catch (DatastoreRewriteException ex) {
         studio_.logs().showError(ex, "Can not overwrite data");
      } catch (DuplicatorException ex) {
         studio_.logs().showError(ex.getMessage());
      } catch (IOException ioe) {
         studio_.logs().showError(ioe, "IOException in Duplicator plugin");
      }
      
      try {
         newStore.freeze();
      } catch (IOException ioe) {
         studio_.logs().showError(ioe, "IOException freezing store in Duplicator plugin");
      }
      studio_.displays().manage(newStore);
      return null;
   }
   
   @Override
   public void done() {
      setProgress(100);
      studio_.alerts().postAlert("Finished duplicating", this.getClass(), newName_);
   }
   
}