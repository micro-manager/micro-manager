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
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;

import javax.swing.SwingWorker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author nico
 */
public class DuplicatorExecutor extends SwingWorker <Void, Void> {
   private final Studio studio_;
   private final DisplayWindow theWindow_;
   private final String newName_;
   private final Map<String, Integer> mins_;
   private final Map<String, Integer> maxes_;
   
   /**
    * Performs the actual creation of a new image with reduced content
    * 
    * @param studio - instance of Micro-Manager Studio
    * @param theWindow - original window to be copied
    * @param newName - name for the copy
    * @param mins - Map with new (or unchanged) minima for the given axis
    * @param maxes - Map with new (or unchanged) maxima for the given axis
    */
   public DuplicatorExecutor(final Studio studio, 
           final DisplayWindow theWindow, 
           final String newName, 
           final Map<String, Integer> mins,
           final Map<String, Integer> maxes) {
      studio_ = studio;
      theWindow_ = theWindow;
      newName_ = newName;
      mins_ = mins;
      maxes_ = maxes;
   }

   @Override
   protected Void doInBackground() {
      
       // TODO: provide options for disk-backed datastores
      Datastore newStore = studio_.data().createRAMDatastore();
      
      DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
      copyDisplay.setCustomTitle(newName_);
      copyDisplay.setDisplaySettings(
              theWindow_.getDisplaySettings().copyBuilder().build());
      
      // TODO: use Overlays instead
      Roi roi = theWindow_.getImagePlus().getRoi();
      
      DataProvider oldStore = theWindow_.getDataProvider();
      Coords.CoordsBuilder newSizeCoordsBuilder = studio_.data().getCoordsBuilder();
      for (String axis: oldStore.getAxes()) {
         newSizeCoordsBuilder.index(axis, oldStore.getNextIndex(axis) - 1 );
      }
      SummaryMetadata metadata = oldStore.getSummaryMetadata();
      List<String> channelNames = metadata.getChannelNameList();
      if (mins_.containsKey(Coords.CHANNEL)) {
         int min = mins_.get(Coords.CHANNEL);
         int max = maxes_.get(Coords.CHANNEL);
         List<String> chNameList = new ArrayList<>();
         for (int index = min; index <= max; index++) {
            if (channelNames == null || index >= channelNames.size()) {
               chNameList.add("channel " + index);
            } else {
               chNameList.add(channelNames.get(index));
            }  
         }
         channelNames = chNameList;
      }
      newSizeCoordsBuilder.channel(channelNames.size());
      float  nrToBeCopied = 1;
      for (String axis : oldStore.getAxes()) {
         if (mins_.containsKey(axis)) {
            int min = mins_.get(axis);
            int max = maxes_.get(axis);
            newSizeCoordsBuilder.index(axis, max - min);
            nrToBeCopied *= (max - min + 1);
         }
      }

      metadata = metadata.copyBuilder()
              .channelNames(channelNames)
              .intendedDimensions(newSizeCoordsBuilder.build())
              .build();
      try {
         newStore.setSummaryMetadata(metadata);

         Iterable<Coords> unorderedImageCoords = oldStore.getUnorderedImageCoords();
         int nrCopied = 0;
         for (Coords oldCoord : unorderedImageCoords) {
            boolean copy = true;
            for (String axis : oldStore.getAxes()) {
               if (mins_.containsKey(axis) && maxes_.containsKey(axis)) {
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
                  if (mins_.containsKey(axis)) {
                     newCoordBuilder.index(axis, oldCoord.getIndex(axis) - mins_.get(axis) );
                  }
               }
               Image img = oldStore.getImage(oldCoord);
               Coords newCoords = newCoordBuilder.build();
               Image newImgShallow = img.copyAtCoords(newCoords);
               if (roi != null) {
                  ImageProcessor ip = studio_.data().ij().createProcessor(img);
                  /*
                  ImageProcessor ip = null;
                  if (img.getImageJPixelType() == ImagePlus.GRAY8) {
                     ip = new ByteProcessor(
                          img.getWidth(), img.getHeight(), (byte[]) img.getRawPixels());
                  } else 
                     if (img.getImageJPixelType() == ImagePlus.GRAY16) {
                        ip = new ShortProcessor(
                        img.getWidth(), img.getHeight() );
                        ip.setPixels((short[]) img.getRawPixels());
                  }

                   */
                  if (ip != null) {
                     ip.setRoi(roi);
                     ImageProcessor copyIp = ip.crop();
                     newImgShallow = studio_.data().createImage(copyIp.getPixels(), 
                             copyIp.getWidth(), copyIp.getHeight(), 
                             img.getBytesPerPixel(), img.getNumComponents(), 
                             newCoords, newImgShallow.getMetadata());
                  } else {
                     throw new DuplicatorException("Unsupported pixel type.  Can only copy 8 or 16 bit images.");
                  }
               }
               newStore.putImage(newImgShallow);
               nrCopied++;
               try {
                  setProgress( (int) ( nrCopied / nrToBeCopied * 100.0) );
               } catch (IllegalArgumentException iae) {
                  System.out.println ("Value was: " + (int) (nrCopied / nrToBeCopied * 100.0));
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