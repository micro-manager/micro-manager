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

import java.io.IOException;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.Studio;

public class SaverProcessor implements Processor {
   private Studio studio_;
   private Datastore store_ = null;

   public SaverProcessor(Studio studio, String format, String savePath,
         boolean shouldDisplay) {
      studio_ = studio;
      if (format.equals(SaverPlugin.MULTIPAGE_TIFF)) {
         // TODO: hardcoded whether or not to split positions.
         try {
            store_ = studio_.data().createMultipageTIFFDatastore(savePath,
                  true, true);
         }
         catch (IOException e) {
            studio_.logs().showError(e, "Error creating datastore at " + savePath);
         }
      }
      else if (format.equals(SaverPlugin.SINGLEPLANE_TIFF_SERIES)) {
         store_ = studio.data().createSinglePlaneTIFFSeriesDatastore(savePath);
      }
      else if (format.equals(SaverPlugin.RAM)) {
         store_ = studio.data().createRAMDatastore();
      }
      else {
         studio_.logs().logError("Unrecognized save format " + format);
      }
      studio_.displays().manage(store_);

      if (shouldDisplay) {
         studio_.displays().createDisplay(store_);
      }
   }

   public void processImage(Image image, ProcessorContext context) {
      try {
         store_.putImage(image);
      }
      catch (DatastoreFrozenException e) {
         studio_.logs().logError(e, "Unable to save data: datastore is frozen");
      }
      context.outputImage(image);
   }
}
