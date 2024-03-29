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

package org.micromanager.acquisition.internal;

import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.data.Datastore;

/**
 * This event signifies that an acquisition has been ended.
 *
 * <p>This default implementation of this event is posted on the studio event bus,
 * i.e. subscribe to this event using {@link org.micromanager.events.EventManager}
 */
public final class DefaultAcquisitionEndedEvent implements AcquisitionEndedEvent {
   private Datastore store_;
   private Object source_;

   public DefaultAcquisitionEndedEvent(Datastore store, Object source) {
      store_ = store;
      source_ = source;
   }

   /**
    * Return the Datastore into which images were placed during the
    * acquisition.
    */
   @Override
   public Datastore getStore() {
      return store_;
   }

   /**
    * Return an Object used to identify the entity in charge of the
    * acquisition. This can be used by recipients to distinguish different
    * types of acquisitions. This object must be the same object that published
    * the corresponding AcquisitionStartedEvent to this acquisition.
    */
   @Override
   public Object getSource() {
      return source_;
   }
}
