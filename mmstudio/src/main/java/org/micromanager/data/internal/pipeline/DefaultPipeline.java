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
import org.micromanager.data.Processor;

import org.micromanager.internal.utils.ReportingUtils;

public class DefaultPipeline implements Pipeline {
   
   private List<Processor> processors_;
   private List<DefaultProcessorContext> contexts_;
   private Datastore store_;
   private boolean isSynchronous_;

   public DefaultPipeline(List<Processor> processors, Datastore store,
         boolean isSynchronous) {
      processors_ = processors;
      store_ = store;
      contexts_ = new ArrayList<DefaultProcessorContext>();
      for (Processor processor : processors_) {
         contexts_.add(new DefaultProcessorContext(
                  processor, store_, isSynchronous));
      }
      // Chain the contexts together. The last one goes to the Datastore by
      // default as it has no sink.
      for (int i = 0; i < contexts_.size() - 1; ++i) {
         contexts_.get(i).setSink(contexts_.get(i + 1));
      }
      isSynchronous_ = isSynchronous;
   }

   @Override
   public void insertImage(Image image) {
      if (contexts_.size() > 0) {
         contexts_.get(0).insertImage(image);
      }
      else {
         // Empty "pipeline".
         try {
            store_.putImage(image);
         }
         catch (DatastoreFrozenException e) {
            ReportingUtils.logError(e, "Unable to add image to frozen store.");
         }
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
      for (DefaultProcessorContext context : contexts_) {
         context.halt();
      }
   }
}
