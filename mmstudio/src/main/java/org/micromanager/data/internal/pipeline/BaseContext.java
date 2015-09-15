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

import java.util.concurrent.CountDownLatch;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.internal.utils.ReportingUtils;

public abstract class BaseContext implements ProcessorContext {
   protected BaseContext sink_ = null;
   protected Processor processor_;
   protected Datastore store_;
   protected DefaultPipeline parent_;
   protected CountDownLatch flushLatch_;

   public BaseContext(Processor processor, Datastore store,
         DefaultPipeline parent) {
      processor_ = processor;
      store_ = store;
      parent_ = parent;
   }

   /**
    * Receive an image from our processor and pass it along to the next
    * context.
    */
   @Override
   public void outputImage(Image image) {
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
         sink_.insertImage(new ImageWrapper(image));
      }
   }

   /**
    * Set the context that images output by our processor should be fed to.
    * If we have no sink, then we send images to the Datastore instead.
    */
   public void setSink(BaseContext sink) {
      sink_ = sink;
   }

   /**
    * Set the CountDownLatch to count down when we flush ourselves.
    */
   public void setFlushLatch(CountDownLatch latch) {
      flushLatch_ = latch;
   }

   /**
    * Receive a new image for processing.
    */
   abstract public void insertImage(ImageWrapper wrapper);

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return store_.getSummaryMetadata();
   }

   @Override
   public void setSummaryMetadata(SummaryMetadata metadata) throws DatastoreFrozenException {
      store_.setSummaryMetadata(metadata);
   }
}
