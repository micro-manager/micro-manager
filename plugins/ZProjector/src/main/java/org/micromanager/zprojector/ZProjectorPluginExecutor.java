///////////////////////////////////////////////////////////////////////////////
//FILE:          ZProjectorPluginExecutor.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ZProjector plugin
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

package org.micromanager.zprojector;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingWorker;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Coords.CoordsBuilder;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.display.DisplayWindow;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ProgressBar;

/**
 *
 * @author nico
 */
public class ZProjectorPluginExecutor {

   private final Studio studio_;
   private final DisplayWindow window_;
   private final DataProvider oldStore_;
   private int nrProjections_;
   private int projectionNr_;
   private ProgressBar progressBar_;

   public ZProjectorPluginExecutor(Studio studio, DisplayWindow window) {
      studio_ = studio;
      window_ = window;
      oldStore_ = window.getDataProvider();      
   }

   /**
    * Performs the actual creation of a new image with reduced content
    *
    * @param save  True when projection should be saved automatically
    * @param newName  Name for the copy
    * @param projectionAxis Axis to be projected (often Z or T), see Coords
    * @param firstFrame  Frame nr (zero-based) of first frame to include in projection
    * @param lastFrame  Last frame (nr zero-based) to include in projection
    * @param projectionMethod ZProjector method see ImageJ code
    */
   public final void project(final boolean save, final String newName, final String projectionAxis, 
           final int firstFrame, final int lastFrame, final int projectionMethod) {
     

      class ZProjectTask extends SwingWorker<Void, Void> {

         ZProjectTask() {
         }

         @Override
         public Void doInBackground() throws Exception {  // TODO use Exceptions
            Datastore newStore;
            if (save) {
               File result = FileDialogs.openDir(null,
                           "Please choose a directory to save " + newName,
                           FileDialogs.MM_DATA_SET);
               if (result != null) {
                  try {
                  newStore = studio_.data().createMultipageTIFFDatastore(
                          result.getAbsolutePath() + File.separator + newName, true, 
                          StorageMultipageTiff.getShouldSplitPositions());
                  } catch (IOException ioe) {
                     studio_.logs().showError(ioe);
                     return null;
                  }
               } else {
                  return null;
               }
            } else {
               newStore = studio_.data().createRAMDatastore();
            }

            CoordsBuilder newSizeCoordsBuilder = studio_.data().getCoordsBuilder();
            for (String axis: oldStore_.getAxes()) {
               newSizeCoordsBuilder.index(axis, oldStore_.getNextIndex(axis) - 1 );
            }
            SummaryMetadata metadata = oldStore_.getSummaryMetadata();

            metadata = metadata.copyBuilder()
                    .intendedDimensions(newSizeCoordsBuilder.build())
                    .build();
            Coords.CoordsBuilder cb = Coordinates.builder();
            try {
               newStore.setSummaryMetadata(metadata);               
               List<String> axes = oldStore_.getAxes();
               axes.remove(projectionAxis);
               axes.sort( new CoordsComparator());
               if (!save) {
                  DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
                  copyDisplay.setCustomTitle(newName);
                  copyDisplay.setDisplaySettings(
                          window_.getDisplaySettings().copyBuilder().build());
                  studio_.displays().manage(newStore);
               } else {
                  nrProjections_ = 1;
                  for (String axis : axes) {
                     nrProjections_ *= oldStore_.getNextIndex(axis);
                  }
                  progressBar_ = new ProgressBar (window_.getWindow(), 
                                       "Projection Progress", 1, nrProjections_);
                  progressBar_.setVisible(true);
               }

               findAllProjections(newStore, axes, cb, projectionAxis,
                       firstFrame, lastFrame, projectionMethod);
               
            } catch (DatastoreFrozenException ex) {
               studio_.logs().showError("Can not add data to frozen datastore");
            } catch (DatastoreRewriteException ex) {
               studio_.logs().showError("Can not overwrite data");
            } finally {
               if (progressBar_ != null) {
                  progressBar_.setVisible(false);
               }
            }

            newStore.freeze();
            
            if (save) {
               DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
               copyDisplay.setCustomTitle(newName);
               copyDisplay.setDisplaySettings(
                       window_.getDisplaySettings().copyBuilder().build());
               studio_.displays().manage(newStore);
            }

            return null;
         }

         @Override
         public void done() {

         }
      }

      
      (new ZProjectTask()).execute();
   }
   
   
   /**
    * Recursively figures out which projections need to be performed
    * It does so by taking the first remaining axes, cycle through all positions
    * in that axes, and recursively calling this function (omitting that axis).
    * When no more axes are remining, the actual projection will be exected.
    * 
    * @param newStore Datastore to put the new projected images into
    * @param remainingAxes List with axes to look at
    * @param cbp      Coordinates build set to the correct position
    * @param projectionAxis   Axis that needs to be projected
    * @param min       lowest frame number to be included in the projection
    * @param max        highest frame number to be included in the projection
    * @param projectionMethod Projection method (as an ImageJ ZProjector int)
    * @throws IOException Can arise when saving to disk
    */
   private void findAllProjections(Datastore newStore, List<String> remainingAxes, 
           Coords.CoordsBuilder cbp, String projectionAxis, int min, int max, 
           int projectionMethod) throws IOException {
      String currentAxis = remainingAxes.get(0);
      List<String> rcAxes = new ArrayList<>(remainingAxes);
      rcAxes.remove(currentAxis);
      for (int i = 0; i < oldStore_.getNextIndex(currentAxis); i++) {
         cbp.index(currentAxis, i);
         if (rcAxes.isEmpty()) {
            executeProjection(newStore, cbp, projectionAxis, min, max, projectionMethod);
            projectionNr_++;
            if (progressBar_ != null) {
               progressBar_.setProgress(projectionNr_); 
            }
         } else {
            findAllProjections(newStore, rcAxes, cbp, projectionAxis,
                    min, max, projectionMethod);
         }
      }
   }
   
   /**
    * Do the actual projection
    * 
    * @param newStore Datastore to put the new projected images into
    * @param cbp Coordinates build set to the correct position
    * @param projectionAxis Axis that needs to be projected
    * @param min lowest frame number to be included in the projection
    * @param max Highest frame number to be included in the projection
    * @param projectionMethod Projection method (as an ImageJ ZProjector int)
    * @throws IOException Can arise when saving to disk
    */
   private void executeProjection(Datastore newStore, Coords.CoordsBuilder cbp, 
           String projectionAxis, int min, int max, int projectionMethod) 
           throws IOException {
      cbp.index(projectionAxis, min);
      Image tmpImg = oldStore_.getAnyImage();
      if (tmpImg== null) {
         studio_.alerts().postAlert("Projection problem", this.getClass(),
                 "No images found while projecting");
         return;
      }
      ImageStack stack = new ImageStack(
               tmpImg.getWidth(), tmpImg.getHeight());
      Metadata imgMetadata = null;
      for (int i = min; i <= max; i++) {
         Image img = oldStore_.getImage(cbp.index(projectionAxis, i).build());
         if (img != null) {  // null happens when this image was skipped
            if (imgMetadata == null) {
               imgMetadata = img.getMetadata().copyBuilderWithNewUUID().build();
            }
            ImageProcessor ip
                    = studio_.data().getImageJConverter().createProcessor(img);
            stack.addSlice(ip);
         }
      }
      if (stack.getSize() > 0 && imgMetadata != null) {
         ImagePlus tmp = new ImagePlus("tmp", stack);
         ZProjector zp = new ZProjector(tmp);
         zp.setMethod(projectionMethod);
         zp.doProjection();
         ImagePlus projection = zp.getProjection();
         if (projection.getBytesPerPixel() > 2) {
            if (tmp.getBytesPerPixel() == 1) {
               projection.setProcessor(projection.getProcessor().convertToByte(false));
            } else if (tmp.getBytesPerPixel() == 2) {
               projection.setProcessor(projection.getProcessor().convertToShort(false));
            }
         }
         Image outImg = studio_.data().getImageJConverter().createImage(
                 projection.getProcessor(), cbp.index(projectionAxis, 0).build(),
                 imgMetadata.copyBuilderWithNewUUID().build());
         newStore.putImage(outImg);
      } else {
         studio_.alerts().postAlert("Projection problem", this.getClass(), 
                                             "No images found while projecting");
      }
   }
   
}
