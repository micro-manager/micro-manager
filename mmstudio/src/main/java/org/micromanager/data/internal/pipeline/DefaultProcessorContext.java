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
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;

import org.micromanager.internal.utils.ReportingUtils;

public class DefaultProcessorContext implements ProcessorContext {

   private Processor processor_;
   private DefaultProcessorContext sink_ = null;
   private Datastore store_;
   private boolean isSynchronous_;
   private boolean isHalted_ = false;

   public DefaultProcessorContext(Processor processor,
         Datastore store, boolean isSynchronous) {
      processor_ = processor;
      store_ = store;
      isSynchronous_ = isSynchronous;
   }

   /**
    * Set the context that images output by our processor should be fed to.
    * If we have no sink, then we send images to the Datastore instead.
    */
   public void setSink(DefaultProcessorContext sink) {
      sink_ = sink;
   }

   /**
    * Receive an image from our processor and pass it along to the next
    * context.
    */
   @Override
   public void outputImage(Image image) {
      if (isHalted_) {
         // Nothing to be done.
         return;
      }
      if (sink_ == null) {
         // Send the image to the Datastore.
         try {
            store_.putImage(image);
         }
         catch (DatastoreFrozenException e) {
            ReportingUtils.logError(e, "Unable to store processed image.");
         }
      }
      else {
         // Send the image to the next context in the chain.
         sink_.insertImage(image);
      }
   }

   /**
    * Process an image. TODO: currently ignoring isSynchronous.
    */
   public void insertImage(Image image) {
      processor_.processImage(image, this);
   }

   /**
    * Halt processing. No more images will be propagated along the chain.
    */
   public void halt() {
      isHalted_ = true;
   }
}
