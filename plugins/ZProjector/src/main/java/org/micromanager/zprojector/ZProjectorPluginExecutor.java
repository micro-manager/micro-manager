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
import java.util.SortedMap;
import java.util.TreeMap;
import javax.swing.SwingWorker;
import org.jfree.data.xy.XYSeries;
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
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;
import org.micromanager.imageprocessing.curvefit.Fitter;
import org.micromanager.imageprocessing.curvefit.PlotUtils;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ProgressBar;

/**
 * Executes the Projection.
 *
 * @author nico
 */
public class ZProjectorPluginExecutor {

   private final Studio studio_;
   private DisplayWindow window_;
   private final DataProvider oldProvider_;
   private int projectionNr_;
   private ProgressBar progressBar_;

   /**
    * Constructs this plugin's executor.
    *
    * @param studio Omnipresent Micro-Manager Studio object.
    * @param window DisplayWindow whose data we will project.
    */
   public ZProjectorPluginExecutor(Studio studio, DisplayWindow window) {
      studio_ = studio;
      window_ = window;
      oldProvider_ = window.getDataProvider();
   }

   /**
    * Constructs this plugin's executor.
    *
    * @param studio Omnipresent Micro-Manager Studio object.
    * @param oldProvider DataProvider to be projected.
    */
   public ZProjectorPluginExecutor(Studio studio, DataProvider oldProvider) {
      studio_ = studio;
      oldProvider_ = oldProvider;
   }

   /**
    * Performs the actual creation of a new image with reduced content.
    *
    * @param save  True when projection should be saved automatically
    * @param newName  Name for the copy
    * @param zpd ZProjectorData object with projection parameters
    */
   public final void project(final boolean save, final String newName, ZProjectorData zpd) {

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

            project(newStore,  true, newName, zpd);

            return null;
         }

         @Override
         public void done() {

         }
      }

      (new ZProjectTask()).execute();
   }

   /**
    * Directly performs the actual creation of a new image with reduced content.
    *
    * @param newStore Store to put data into, type of store determines if data will be auto-saved
    * @param show  True when projection should be displayed
    * @param newName  Name for the copy
    * @param zpd ZProjectorData object with projection parameters
    */
   public Datastore project(final Datastore newStore, final boolean show,
                            final String newName, final ZProjectorData zpd) throws Exception {
      CoordsBuilder newSizeCoordsBuilder = studio_.data().coordsBuilder();
      for (String axis : oldProvider_.getAxes()) {
         if (!axis.equals(zpd.projectionAxis_)) {
            newSizeCoordsBuilder.index(axis, oldProvider_.getNextIndex(axis));
         } else {
            newSizeCoordsBuilder.index(axis, 0);
         }
      }
      SummaryMetadata metadata = oldProvider_.getSummaryMetadata();

      metadata = metadata.copyBuilder()
            .intendedDimensions(newSizeCoordsBuilder.build())
            .build();
      Coords.CoordsBuilder cb = Coordinates.builder();
      try {
         newStore.setSummaryMetadata(metadata);
         List<String> axes = oldProvider_.getAxes();
         axes.remove(zpd.projectionAxis_);
         axes.sort(new CoordsComparator());
         if (show) {
            DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
            copyDisplay.setCustomTitle(newName);
            if (window_ != null) {
               copyDisplay.setDisplaySettings(
                     window_.getDisplaySettings().copyBuilder().build());
            }
            studio_.displays().manage(newStore);
         } else {
            int nrProjections = 1;
            for (String axis : axes) {
               nrProjections *= oldProvider_.getNextIndex(axis);
            }
            if (window_ != null) {
               progressBar_ = new ProgressBar(window_.getWindow(),
                     "Projection Progress", 1, nrProjections);
               progressBar_.setVisible(true);
            }
         }

         findAllProjections(newStore, axes, cb, zpd);

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

      return newStore;
   }

   /**
    * Recursively figures out which projections need to be performed
    * It does so by taking the first remaining axes, cycle through all positions
    * in that axes, and recursively calling this function (omitting that axis).
    * When no more axes are remaining, the actual projection will be executed.
    *
    * @param newStore Datastore to put the new projected images into
    * @param remainingAxes List with axes to look at
    * @param cbp      Coordinates builder set to the correct position
    * @param zpd ZProjectorData object with projection parameters
    * @throws IOException Can arise when saving to disk
    */
   private void findAllProjections(Datastore newStore, List<String> remainingAxes, 
           Coords.CoordsBuilder cbp, ZProjectorData zpd) throws IOException {
      String currentAxis = remainingAxes.get(0);
      List<String> rcAxes = new ArrayList<>(remainingAxes);
      rcAxes.remove(currentAxis);
      for (int i = 0; i < oldProvider_.getNextIndex(currentAxis); i++) {
         cbp.index(currentAxis, i);
         if (rcAxes.isEmpty()) {
            executeProjection(newStore, cbp, zpd);
            projectionNr_++;
            if (progressBar_ != null) {
               progressBar_.setProgress(projectionNr_); 
            }
         } else {
            findAllProjections(newStore, rcAxes, cbp, zpd);
         }
      }
   }
   
   /**
    * Do the actual projection.
    *
    * @param newStore Datastore to put the new projected images into
    * @param cbp Coordinates builder set to the correct position
    * @param zpd ZProjectorData object with projection parameters
    * @throws IOException Can arise when saving to disk
    */
   private void executeProjection(Datastore newStore, Coords.CoordsBuilder cbp,  ZProjectorData zpd)
           throws IOException {
      cbp.index(zpd.projectionAxis_, zpd.firstFrame_);
      Image tmpImg = oldProvider_.getAnyImage();
      if (tmpImg == null) {
         studio_.alerts().postAlert("Projection problem", this.getClass(),
                 "No images found while projecting");
         return;
      }
      ImageStack stack = new ImageStack(
               tmpImg.getWidth(), tmpImg.getHeight());
      Metadata imgMetadata = null;
      for (int i = zpd.firstFrame_; i <= zpd.lastFrame_; i++) {
         Image img = oldProvider_.getImage(cbp.index(zpd.projectionAxis_, i).build());
         if (img != null) {  // null happens when this image was skipped
            if (imgMetadata == null) {
               imgMetadata = img.getMetadata().copyBuilderWithNewUUID().build();
            }
            ImageProcessor ip
                    = studio_.data().getImageJConverter().createProcessor(img);
            stack.addSlice(ip);
         }
      }
      //ImagePlus s = new ImagePlus("Test", stack);
      //s.show();
      if (stack.getSize() > 0 && imgMetadata != null) {
         if (zpd.projectionMethod_ == ZProjectorPlugin.SHARPNESS_METHOD) {
            // need to get these from UI input

            ImgSharpnessAnalysis imgScoringFunction = new ImgSharpnessAnalysis();
            imgScoringFunction.setComputationMethod(zpd.sharpnessMethod_);
            imgScoringFunction.allowInPlaceModification(true);
            SortedMap<Integer, Double> focusScoreMap = new TreeMap<>();
            int nrSlices = stack.getSize();
            for (int i = 0; i < nrSlices; i++) {
               focusScoreMap.put(i, imgScoringFunction.compute(stack.getProcessor(i + 1)));
            }
            XYSeries xySeries = new XYSeries("Focus Score");
            focusScoreMap.forEach(xySeries::add);
            double[] guess = {(double) nrSlices / 2.0,
                    focusScoreMap.get(nrSlices / 2)};
            double[] fit = Fitter.fit(xySeries, Fitter.FunctionType.Gaussian, guess);
            int bestIndex  = (int) Math.round(Fitter.getXofMaxY(xySeries,
                    Fitter.FunctionType.Gaussian, fit));
            if (zpd.showGraph_) {
               XYSeries xySeriesFitted = Fitter.getFittedSeries(xySeries,
                       Fitter.FunctionType.Gaussian, fit);
               XYSeries[] data = {xySeries, xySeriesFitted};
               boolean[] shapes = {true, false};
               PlotUtils pu = new PlotUtils(studio_);
               pu.plotDataN("Focus Score", data, "z position", "Focus Score", shapes,
                       "", (double) bestIndex);
            }
            if (bestIndex < 0) {
               bestIndex = 0;
            } else if (bestIndex >= stack.size()) {
               bestIndex = stack.size() - 1;
            }
            Image img = oldProvider_.getImage(cbp.index(zpd.projectionAxis_, bestIndex).build());
            Image outImg = img.copyWith(cbp.index(zpd.projectionAxis_, 0).build(),
                    img.getMetadata().copyBuilderWithNewUUID().build());
            newStore.putImage(outImg);
         } else {
            ImagePlus tmp = new ImagePlus("tmp", stack);
            ZProjector zp = new ZProjector(tmp);
            zp.setMethod(zpd.projectionMethod_);
            zp.doProjection();
            ImagePlus projection = zp.getProjection();
            if (projection.getBytesPerPixel() > 2) {
               if (tmp.getBytesPerPixel() == 1) {
                  projection.setProcessor(projection.getProcessor().convertToByte(false));
               } else if (tmp.getBytesPerPixel() == 2) {
                  projection.setProcessor(projection.getProcessor().convertToShort(false));
               }
            }
            // TODO: adjust the metadata with the little knowledge we have about the
            // projection axis (for instance, if z, set z position to the mean of the
            // z positions of all images?
            Image outImg = studio_.data().getImageJConverter().createImage(
                    projection.getProcessor(), cbp.index(zpd.projectionAxis_, 0).build(),
                    imgMetadata.copyBuilderWithNewUUID().build());
            newStore.putImage(outImg);
         }
      } else {
         studio_.alerts().postAlert("Projection problem", this.getClass(), 
                                             "No images found while projecting");
      }
   }
   
}
