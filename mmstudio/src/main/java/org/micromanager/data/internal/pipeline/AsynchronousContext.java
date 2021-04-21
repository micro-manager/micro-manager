///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     Data API
// -----------------------------------------------------------------------------
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
import org.micromanager.data.Processor;
import org.micromanager.internal.utils.ReportingUtils;

public final class AsynchronousContext extends BaseContext {
  private boolean isFlushed_ = false;
  private LinkedBlockingQueue<ImageWrapper> inputQueue_ = null;

  public AsynchronousContext(Processor processor, Datastore store, DefaultPipeline parent) {
    super(processor, store, parent);
    inputQueue_ = new LinkedBlockingQueue<ImageWrapper>(1);
    // Create a new thread to do processing in.
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                monitorQueue();
              }
            },
            "Processor context for " + processor_)
        .start();
  }

  /**
   * This method runs in a separate thread, and pulls images from the input queue, to feed into the
   * processor. It only runs when the pipeline is in asynchronous mode; in synchronous mode, the
   * processor is invoked directly by insertImage().
   */
  private void monitorQueue() {
    while (true) {
      ImageWrapper wrapper = null;
      try {
        wrapper = inputQueue_.poll(1000, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        // Ignore it.
        Thread.currentThread().interrupt();
      }
      if (wrapper == null) {
        // Queue is empty.
        if (isFlushed_) {
          // All done.
          return;
        } else {
          // Go back to polling.
          continue;
        }
      }
      if (wrapper.getImage() == null) {
        // Flushing the queue; cleanup the processor and pass the empty
        // wrapper along.
        processor_.cleanup(this);
        if (sink_ != null) {
          sink_.insertImage(wrapper);
        }
        isFlushed_ = true;
        if (flushLatch_ != null) {
          flushLatch_.countDown();
        }
      } else {
        // Non-null image: process it.
        isFlushed_ = false;
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

  /**
   * Process an image. If the input ImageWrapper has a null image, then we flush the pipeline
   * instead, passing the null along to the next context.
   */
  public void insertImage(ImageWrapper wrapper) {
    try {
      inputQueue_.put(wrapper);
    } catch (InterruptedException e) {
      ReportingUtils.logError(e, "Interrupted while passing image along pipeline");
    }
  }
}
