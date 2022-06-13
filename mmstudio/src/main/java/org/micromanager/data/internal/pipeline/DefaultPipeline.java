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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.Processor;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.utils.ReportingUtils;

public final class DefaultPipeline implements Pipeline {

   private final List<Processor> processors_;
   private final List<BaseContext> contexts_;
   private final Datastore store_;
   private final boolean isSynchronous_;
   private boolean haveInsertedImages_ = false;
   private boolean amHalting_ = false;
   private boolean isHalted_ = false;
   private final ArrayList<Exception> exceptions_;

   @SuppressWarnings("LeakingThisInConstructor")
   public DefaultPipeline(List<Processor> processors, Datastore store,
                          boolean isSynchronous) {
      processors_ = processors;
      store_ = store;
      contexts_ = new ArrayList<BaseContext>();
      exceptions_ = new ArrayList<Exception>();
      for (Processor processor : processors_) {
         if (isSynchronous) {
            contexts_.add(new SynchronousContext(processor, store_, this));
         } else {
            contexts_.add(new AsynchronousContext(processor, store_, this));
         }
      }
      // Chain the contexts together. The last one goes to the Datastore by
      // default as it has no sink.
      for (int i = 0; i < contexts_.size() - 1; ++i) {
         contexts_.get(i).setSink(contexts_.get(i + 1));
      }
      isSynchronous_ = isSynchronous;
   }

   @Override
   public void insertSummaryMetadata(SummaryMetadata summary)
         throws IOException, PipelineErrorException {
      if (amHalting_) {
         throw new PipelineErrorException(
               "Attempted to pass summary metadata through pipeline after it has been halted.");
      } else if (haveInsertedImages_) {
         throw new PipelineErrorException(
               "Attempted to pass summary metadata through pipeline after it has "
                     + "started image processing.");
      }
      if (contexts_.isEmpty()) {
         // Insert directly.
         store_.setSummaryMetadata(summary);
      } else {
         contexts_.get(0).insertSummaryMetadata(summary);
      }
   }

   @Override
   public synchronized void insertImage(Image image) throws IOException, PipelineErrorException {
      if (amHalting_) {
         // Ignore it.
         return;
      }
      if (exceptions_.size() > 0) {
         for (Exception ex : exceptions_) {
            ReportingUtils.logError(ex);
         }
         // Currently in an error state.
         throw new PipelineErrorException();
      }
      haveInsertedImages_ = true;
      // Manually check for frozen; otherwise for asynchronous pipelines,
      // there's no way for the caller to be informed when we later try to
      // insert the image into the datastore.
      if (store_.isFrozen()) {
         throw new DatastoreFrozenException();
      }
      if (contexts_.size() > 0) {
         contexts_.get(0).insertImage(new ImageWrapper(image));
      } else {
         // Empty "pipeline".
         store_.putImage(image);
      }
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public boolean isSynchronous() {
      return isSynchronous_;
   }

   @Override
   public synchronized void halt() {
      amHalting_ = true;
      if (contexts_.isEmpty()) {
         // Automatically done waiting.
         isHalted_ = true;
         return;
      }
      // Flush the pipeline, so we know that there aren't any more images ready
      // to be added to the datastore. We provide a latch for the contexts to
      // count down as they finish flushing themselves; when the latch hits
      // zero we can return.
      CountDownLatch latch = new CountDownLatch(contexts_.size());
      for (BaseContext context : contexts_) {
         context.setFlushLatch(latch);
      }
      contexts_.get(0).insertImage(new ImageWrapper(null));
      try {
         latch.await();
      } catch (InterruptedException e) {
         ReportingUtils.logError("Interrupted while waiting for flush to complete.");
      }
      isHalted_ = true;
   }

   @Override
   public boolean isHalted() {
      return isHalted_;
   }

   @Override
   public List<Exception> getExceptions() {
      return exceptions_;
   }

   @Override
   public void clearExceptions() {
      exceptions_.clear();
   }

   @Override
   public List<Processor> getProcessors() {
      return processors_;
   }

   public void exceptionOccurred(Exception e) {
      exceptions_.add(e);
   }
}
