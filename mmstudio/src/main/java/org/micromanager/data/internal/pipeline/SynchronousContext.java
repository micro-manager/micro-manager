///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.data.internal.pipeline;

import org.micromanager.data.Datastore;
import org.micromanager.data.Processor;
import org.micromanager.internal.utils.ReportingUtils;

public final class SynchronousContext extends BaseContext {
   public SynchronousContext(Processor processor, Datastore store,
                             DefaultPipeline parent) {
      super(processor, store, parent);
   }

   /**
    * Process an image. If the input ImageWrapper has a null image, then we
    * flush the pipeline instead, passing the null along to the next context.
    */
   public void insertImage(ImageWrapper wrapper) {
      if (wrapper.getImage() == null) {
         // Flushing the pipeline. Cleanup the processor, then pass the flush
         // along.
         processor_.cleanup(this);
         if (sink_ != null) {
            sink_.insertImage(wrapper);
         }
         if (flushLatch_ != null) {
            flushLatch_.countDown();
         }
      } else {
         try {
            processor_.processImage(wrapper.getImage(), this);
         } catch (Exception e) {
            ReportingUtils.logError(e, "Processor failed to process image");
            // Pass the exception to our parent.
            parent_.exceptionOccurred(e);
         }
      }
   }
}
