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

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.List;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.Processor;

import org.micromanager.internal.utils.ReportingUtils;

public class DefaultPipeline implements Pipeline {
   
   private List<Processor> processors_;
   private List<BaseContext> contexts_;
   private Datastore store_;
   private boolean isSynchronous_;
   private boolean isHalted_ = false;
   private boolean isFlushComplete_ = false;
   private ArrayList<Exception> exceptions_;

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
         }
         else {
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
   public synchronized void insertImage(Image image) throws DatastoreFrozenException, PipelineErrorException {
      if (isHalted_) {
         // Ignore it.
         return;
      }
      if (exceptions_.size() > 0) {
         // Currently in an error state.
         throw new PipelineErrorException();
      }
      isFlushComplete_ = false;
      // Manually check for frozen; otherwise for asynchronous pipelines,
      // there's no way for the caller to be informed when we later try to
      // insert the image into the datastore.
      if (store_.getIsFrozen()) {
         throw new DatastoreFrozenException();
      }
      if (contexts_.size() > 0) {
         contexts_.get(0).insertImage(new ImageWrapper(image));
      }
      else {
         // Empty "pipeline".
         store_.putImage(image);
      }
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public boolean getIsSynchronous() {
      return isSynchronous_;
   }

   @Override
   public synchronized void halt() {
      isHalted_ = true;
      if (contexts_.size() == 0) {
         // Automatically done waiting.
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
      }
      catch (InterruptedException e) {
         ReportingUtils.logError("Interrupted while waiting for flush to complete.");
      }
   }

   @Override
   public List<Exception> getExceptions() {
      return exceptions_;
   }

   @Override
   public void clearExceptions() {
      exceptions_.clear();
   }

   public void exceptionOccurred(Exception e) {
      exceptions_.add(e);
   }
}
