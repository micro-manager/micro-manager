///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.pipelinesaver;

import java.io.File;
import java.io.IOException;
import org.micromanager.Studio;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;

public class SaverProcessor implements Processor {
   private Studio studio_;
   private Datastore store_;
   private final String format_;
   private final String savePath_;

   public SaverProcessor(Studio studio, String format, String savePath,
                         boolean shouldDisplay) {
      studio_ = studio;
      format_ = format;
      // Update save path to account for duplicates -- append a numerical
      // suffix that's max of all suffices + 1.
      savePath_ = findUniqueSavePath(savePath);
      try {
         if (format.equals(SaverPlugin.MULTIPAGE_TIFF)) {
            // TODO: hardcoded whether or not to split positions.
            store_ = studio_.data().createMultipageTIFFDatastore(savePath_,
                  true, true);
         } else if (format.equals(SaverPlugin.SINGLEPLANE_TIFF_SERIES)) {
            store_ = studio.data().createSinglePlaneTIFFSeriesDatastore(savePath_);
         } else if (format.equals(SaverPlugin.RAM)) {
            store_ = studio.data().createRewritableRAMDatastore();
         } else {
            studio_.logs().logError("Unrecognized save format " + format);
         }
      } catch (IOException e) {
         studio_.logs().showError(e, "Error creating datastore at " + savePath_);
      }

      studio_.displays().manage(store_);

      if (shouldDisplay) {
         studio_.displays().createDisplay(store_);
      }
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      try {
         store_.putImage(image);
      } catch (DatastoreFrozenException e) {
         // Weird that we can not query the store if it is frozen but have to rely
         // on an exception...
         if (format_.equals(SaverPlugin.RAM)) {
            store_ = studio_.data().createRewritableRAMDatastore();
            studio_.displays().manage(store_);
            studio_.displays().createDisplay(store_);
            try {
               store_.putImage(image);
            } catch (IOException ioe) {
               studio_.logs().logError(ioe, "Unable to show Data: IoException");
            }
         } else {
            studio_.logs().logError(e, "Unable to save data: datastore is frozen");
         }
      } catch (DatastoreRewriteException e) {
         studio_.logs()
               .logError(e, "Unable to save data: image already exists at " + image.getCoords());
      } catch (IOException e) {
         studio_.logs().logError(e, "Unable to save data: IOException");
      }
      context.outputImage(image);
   }

   @Override
   public void cleanup(ProcessorContext context) {
      try {
         store_.freeze();
      } catch (IOException ioe) {
         studio_.logs().logError(ioe, "SaverProcessor ran into trouble");
      }
      if (!format_.equals(SaverPlugin.RAM)) {
         store_.setSavePath(savePath_);
      }
   }

   /**
    * Given a path including a final directory name, ensure that the path is
    * unique, appending a numerical suffix if necessary.
    */
   private String findUniqueSavePath(String savePath) {
      File dir = new File(savePath);
      if (!(dir.exists())) {
         // Path is already unique
         return savePath;
      }
      // Not unique; figure out what suffix to apply.
      int maxSuffix = 1;
      String name = dir.getName();
      for (String item : (new File(dir.getParent())).list()) {
         if (item.startsWith(name)) {
            try {
               String[] fields = item.split("_");
               maxSuffix = Math.max(maxSuffix,
                     Integer.parseInt(fields[fields.length - 1]));
            } catch (NumberFormatException e) {
               // No suffix available to use.
            }
         }
      }
      String result = savePath + "_" + (maxSuffix + 1);
      if (new File(result).exists()) {
         studio_.logs().logError("Unable to find unique save path at " + savePath);
         return null;
      }
      return result;
   }
}
