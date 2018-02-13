///////////////////////////////////////////////////////////////////////////////
//FILE:          ZProjectorPluginFrame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ZProjector plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2017
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

   public ZProjectorPluginExecutor(Studio studio, DisplayWindow window) {
      studio_ = studio;

      // Not sure if this is needed, be safe for now
      DataProvider dp = window.getDataProvider();
      if (dp instanceof Datastore) {
         if (!((Datastore) dp).isFrozen()) {
            studio_.logs().showMessage("Can not Z-Project ongoing acquisitions",
                    window.getWindow());
            return;
         }
      }
      
      // TODO: provide UI to other projection methods and z-ranges
      project(window, window.getName() + "Z-Projection", ZProjector.MAX_METHOD);

   }

   /**
    * Performs the actual creation of a new image with reduced content
    *
    * @param theWindow - original window to be copied
    * @param newName - name for the copy
    * @param projectionMethod ZProjector method
    */
   public final void project(final DisplayWindow theWindow,
           final String newName, final int projectionMethod) {

      class ZProjectTask extends SwingWorker<Void, Void> {

         ZProjectTask() {
         }

         @Override
         public Void doInBackground() throws Exception {  // TODO use Exceptions

            // TODO: provide options for disk-backed datastores
            Datastore newStore = studio_.data().createRAMDatastore();

            DataProvider oldStore = theWindow.getDataProvider();
            Coords oldSizeCoord = oldStore.getMaxIndices();
            CoordsBuilder newSizeCoordsBuilder = oldSizeCoord.copyBuilder();
            newSizeCoordsBuilder.z(1);
            SummaryMetadata metadata = oldStore.getSummaryMetadata();

            metadata = metadata.copyBuilder()
                    .intendedDimensions(newSizeCoordsBuilder.build())
                    .build();
            Coords.CoordsBuilder cb = Coordinates.builder();
            try {
               newStore.setSummaryMetadata(metadata);
               DisplayWindow copyDisplay = studio_.displays().createDisplay(newStore);
               copyDisplay.setCustomTitle(newName);
               copyDisplay.setDisplaySettings(
                       theWindow.getDisplaySettings().copyBuilder().build());
               studio_.displays().manage(newStore);
               // HACK: Micro-Manager deals very poorly with Coords that are 
               // absent, so included axes lengths set to 0 (even though that is 
               // physically impossible)
               int nrPos = Math.max(oldStore.getAxisLength(Coords.STAGE_POSITION), 1);
               int nrT = Math.max(oldStore.getAxisLength(Coords.T), 1);
               int nrC = Math.max (oldStore.getAxisLength(Coords.CHANNEL), 1);
               for (int p = 0; p < nrPos; p++) {
                  for (int t = 0; t < nrT; t++) {
                     for (int c = 0; c < nrC; c++) {
                        Coords.CoordsBuilder cbz = cb.stagePosition(p).time(t).channel(c);
                        Image tmpImg = oldStore.getImage(cbz.z(0).build());
                        ImageStack stack = new ImageStack(
                                tmpImg.getWidth(), tmpImg.getHeight());
                        Metadata imgMetadata = tmpImg.getMetadata();
                        for (int z = 0; z < oldStore.getAxisLength(Coords.Z); z++) {
                           Image img = oldStore.getImage(cbz.z(z).build());
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
                                projection.getProcessor(), cbz.z(0).build(),
                                imgMetadata.copyBuilderWithNewUUID().build());
                        newStore.putImage(outImg);
                     }
                  }
               }

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
}
