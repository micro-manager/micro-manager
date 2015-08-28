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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
   private DefaultPipeline parent_;
   private boolean isSynchronous_;
   private boolean isHalted_ = false;
   private boolean isFlushed_ = false;
   private LinkedBlockingQueue<ImageWrapper> inputQueue_ = null;

   public DefaultProcessorContext(Processor processor,
         Datastore store, boolean isSynchronous, DefaultPipeline parent) {
      processor_ = processor;
      store_ = store;
      isSynchronous_ = isSynchronous;
      parent_ = parent;
      if (!isSynchronous_) {
         inputQueue_ = new LinkedBlockingQueue<ImageWrapper>(1);
         // Create a new thread to do processing in.
         new Thread(new Runnable() {
            @Override
            public void run() {
               monitorQueue();
            }
         }, "Processor context for " + processor_).start();
      }
   }

   /**
    * Set the context that images output by our processor should be fed to.
    * If we have no sink, then we send images to the Datastore instead.
    */
   public void setSink(DefaultProcessorContext sink) {
      sink_ = sink;
   }

   /**
    * This method runs in a separate thread, and pulls images from the
    * input queue, to feed into the processor. It only runs when the pipeline
    * is in asynchronous mode; in synchronous mode, the processor is invoked
    * directly by insertImage().
    */
   private void monitorQueue() {
      while (true) {
         ImageWrapper wrapper = null;
         try {
            wrapper = inputQueue_.poll(1000, TimeUnit.MILLISECONDS);
         }
         catch (InterruptedException e) {
            // Ignore it.
            Thread.currentThread().interrupt();
         }
         if (wrapper == null) {
            // Queue is empty.
            if (isHalted_ && isFlushed_) {
               // All done.
               return;
            }
            else {
               // Go back to polling.
               continue;
            }
         }
         if (wrapper.getImage() == null) {
            // Flushing the queue; pass the empty wrapper along.
            if (sink_ != null) {
               sink_.insertImage(wrapper);
            }
            else {
               // No sink, i.e. we're the end of the pipeline, so we're done
               // flushing.
               parent_.flushComplete();
            }
            isFlushed_ = true;
         }
         else {
            // Non-null image: process it.
            isFlushed_ = false;
            try {
               processor_.processImage(wrapper.getImage(), this);
            }
            catch (Exception e) {
               ReportingUtils.logError(e, "Processor failed to process image");
               // Pass the exception to our parent.
               parent_.exceptionOccurred(e);
            }
         }
      }
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
    * Process an image. If the input ImageWrapper has a null image, then we
    * flush the pipeline instead, passing the null along to the next context.
    */
   public void insertImage(ImageWrapper wrapper) {
      if (isSynchronous_) {
         if (wrapper.getImage() == null) {
            // Flushing the pipeline; bypass the processor.
            if (sink_ != null) {
               sink_.insertImage(wrapper);
            }
         }
         else {
            try {
               processor_.processImage(wrapper.getImage(), this);
            }
            catch (Exception e) {
               ReportingUtils.logError(e, "Processor failed to process image");
               // Pass the exception to our parent.
               parent_.exceptionOccurred(e);
            }
         }
      }
      else { // Asynchronous mode
         try {
            inputQueue_.put(wrapper);
         }
         catch (InterruptedException e) {
            ReportingUtils.logError(e, "Interrupted while passing image along pipeline");
         }
      }
   }

   /**
    * Halt processing. No more images will be propagated along the chain.
    */
   public void halt() {
      isHalted_ = true;
   }
}
