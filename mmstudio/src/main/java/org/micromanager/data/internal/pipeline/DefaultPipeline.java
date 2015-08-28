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
   private List<DefaultProcessorContext> contexts_;
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
      contexts_ = new ArrayList<DefaultProcessorContext>();
      exceptions_ = new ArrayList<Exception>();
      for (Processor processor : processors_) {
         contexts_.add(new DefaultProcessorContext(
                  processor, store_, isSynchronous, this));
      }
      // Chain the contexts together. The last one goes to the Datastore by
      // default as it has no sink.
      for (int i = 0; i < contexts_.size() - 1; ++i) {
         contexts_.get(i).setSink(contexts_.get(i + 1));
      }
      isSynchronous_ = isSynchronous;
   }

   @Override
   public void insertImage(Image image) throws DatastoreFrozenException, PipelineErrorException {
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
   public void halt() {
      isHalted_ = true;
      for (DefaultProcessorContext context : contexts_) {
         context.halt();
      }
      // Flush the pipeline, so we know that there aren't any more images ready
      // to be added to the datastore. If we're synchronous, then this will
      // block until the pipeline is flushed; otherwise, we need to wait for
      // our flushComplete method to be called.
      contexts_.get(0).insertImage(new ImageWrapper(null));
      if (!isSynchronous_) {
         // Wait for our flushComplete() method to be called.
         while (!isFlushComplete_) {
            try {
               Thread.sleep(1000);
            }
            catch (InterruptedException e) {
               ReportingUtils.logError(e, "Interrupted while waiting for flush to complete.");
               return;
            }
         }
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

   public void flushComplete() {
      isFlushComplete_ = true;
   }
}
