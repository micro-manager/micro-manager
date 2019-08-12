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
import org.micromanager.display.DisplayWindow;

/**
 *
 * @author nico
 */
public class ZProjectorPluginExecutor {

   private final Studio studio_;
   private final DisplayWindow window_;
   private final DataProvider oldStore_;

   public ZProjectorPluginExecutor(Studio studio, DisplayWindow window) {
      studio_ = studio;
      window_ = window;
      oldStore_ = window.getDataProvider();      
   }

   /**
    * Performs the actual creation of a new image with reduced content
    *
    * @param newName - name for the copy
    * @param projectionAxis
    * @param firstFrame
    * @param lastFrame
    * @param projectionMethod ZProjector method
    */
   public final void project(final String newName, final String projectionAxis, 
           final int firstFrame, final int lastFrame, final int projectionMethod) {

      class ZProjectTask extends SwingWorker<Void, Void> {

         ZProjectTask() {
         }

         @Override
         public Void doInBackground() throws Exception {  // TODO use Exceptions

            // TODO: provide options for disk-backed datastores
            Datastore newStore = studio_.data().createRAMDatastore();

            Coords oldSizeCoord = oldStore_.getMaxIndices();
            CoordsBuilder newSizeCoordsBuilder = oldSizeCoord.copyBuilder();
            newSizeCoordsBuilder.z(1);
            SummaryMetadata metadata = oldStore_.getSummaryMetadata();

            metadata = metadata.copyBuilder()
                    .intendedDimensions(newSizeCoordsBuilder.build())
                    .build();
            Coords.CoordsBuilder cb = Coordinates.builder();
            // HACK: Micro-Manager deals very poorly with Coords that are 
            // absent, so included axes lengths set to 0 (even though that is 
            // physically impossible)
            cb.time(1).channel(1).stagePosition(1).z(1);
            try {
               newStore.setSummaryMetadata(metadata);
               DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
               copyDisplay.setCustomTitle(newName);
               copyDisplay.setDisplaySettings(
                       window_.getDisplaySettings().copyBuilder().build());
               studio_.displays().manage(newStore);
               
               List<String> axes = oldStore_.getAxes();
               axes.remove(projectionAxis);
               findAllProjections( newStore, axes, cb, projectionAxis, 
                       firstFrame, lastFrame,  projectionMethod);
               
            } catch (DatastoreFrozenException ex) {
               studio_.logs().showError("Can not add data to frozen datastore");
            } catch (DatastoreRewriteException ex) {
               studio_.logs().showError("Can not overwrite data");
            }

            newStore.freeze();
            return null;
         }

         @Override
         public void done() {

         }
      }

      (new ZProjectTask()).execute();
   }
   
   private void findAllProjections(Datastore newStore, List<String> remainingAxes, 
           Coords.CoordsBuilder cbp, String projectionAxis, int min, int max, 
           int projectionMethod) throws IOException {
      String currentAxis = remainingAxes.get(0);
      List<String> rcAxes = new ArrayList(remainingAxes);
      rcAxes.remove(currentAxis);
      for (int i = 0; i < oldStore_.getAxisLength(currentAxis); i++) {
         cbp.index(currentAxis, i);
         if (rcAxes.isEmpty()) {
            executeProjection(newStore, cbp, projectionAxis, min, max, projectionMethod);
         } else {
            findAllProjections(newStore, rcAxes, cbp, projectionAxis,
                    min, max, projectionMethod);
         }
      }
   }
   
   private void executeProjection(Datastore newStore, Coords.CoordsBuilder cbp, 
           String projectionAxis, int min, int max, int projectionMethod) 
           throws IOException {
      cbp.index(projectionAxis, min);
      Image tmpImg = oldStore_.getImage(cbp.build());
      ImageStack stack = new ImageStack(
               tmpImg.getWidth(), tmpImg.getHeight());
      Metadata imgMetadata = tmpImg.getMetadata();
      for (int i = min; i <= max; i++) {
         Image img = oldStore_.getImage(cbp.index(projectionAxis, i).build());
         ImageProcessor ip
            = studio_.data().getImageJConverter().createProcessor(img);
         stack.addSlice(ip);
      }
      ImagePlus tmp = new ImagePlus("tmp", stack);
      ZProjector zp = new ZProjector(tmp);
      zp.setMethod(projectionMethod);
      zp.doProjection();
      ImagePlus projection = zp.getProjection();
      Image outImg = studio_.data().getImageJConverter().createImage(
            projection.getProcessor(), cbp.index(projectionAxis, 0).build(),
                  imgMetadata.copyBuilderWithNewUUID().build());
      newStore.putImage(outImg);
   }
   
}
