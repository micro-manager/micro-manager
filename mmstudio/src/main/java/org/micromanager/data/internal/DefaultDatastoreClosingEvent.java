///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
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

package org.micromanager.data.internal;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreClosingEvent;

/**
 * This class signifies that a Datastore's close() method has been called, and
 * thus that all resources associated with that Datastore, and references to
 * the Datastore, should be removed so that it can be garbage collected.
 *
 *  This event is posted on the Studio event bus,
 *  so subscribe using {@link org.micromanager.events.EventManager}.
 */
public final class DefaultDatastoreClosingEvent implements DatastoreClosingEvent {
   private Datastore store_;

   public DefaultDatastoreClosingEvent(Datastore store) {
      store_ = store;
   }

   /**
    * @return Datastore that is closing
    */
   @Override
   public Datastore getDatastore() {
      return store_;
   }
}
