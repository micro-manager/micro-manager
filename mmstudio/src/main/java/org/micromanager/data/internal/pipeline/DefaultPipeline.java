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

import java.util.HashSet;
import java.util.List;

import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.Processor;

public class DefaultPipeline implements Pipeline {
   
   private List<Processor> processors_;
   private HashSet<Datastore> stores_;
   public DefaultPipeline(List<Processor> processors) {
      processors_ = processors;
      stores_ = new HashSet<Datastore>();
   }

   @Override
   public void addDatastore(Datastore store) {
      stores_.add(store);
   }

   @Override
   public void removeDatastore(Datastore store) {
      if (stores_.contains(store)) {
         stores_.remove(store);
      }
   }

   @Override
   public void halt() {
   }
}
