///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
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

package org.micromanager.events.internal;

import org.micromanager.data.Datastore;
import org.micromanager.events.AcquisitionStartedEvent;
import org.micromanager.SequenceSettings;

public class DefaultAcquisitionStartedEvent implements AcquisitionStartedEvent {
   private Datastore store_;
   private SequenceSettings settings_;

   public DefaultAcquisitionStartedEvent(Datastore store,
         SequenceSettings settings) {
      store_ = store;
      settings_ = settings;
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public SequenceSettings getSettings() {
      return settings_;
   }
}
