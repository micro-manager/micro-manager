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

package org.micromanager.acquisition;

import org.micromanager.MMEvent;
import org.micromanager.data.Datastore;

/**
 * This class signals that an acquisition is starting, and provides access to
 * the Datastore that images from the acquisition will be put into. Third-party
 * code may subclass this event to provide notifications of their own
 * acquisitions. If they do so, they should also publish AcquisitionEndedEvents
 * when their acquisitions cease.
 *
 * The default implementation of this event posts on the Studio event bus,
 * so subscribe to this event using {@link org.micromanager.events.EventManager}.
 */
public interface AcquisitionStartedEvent extends MMEvent {
   /**
    * Return the Datastore into which images will be inserted during the
    * acquisition.
    */
   Datastore getDatastore();

   /**
    * Return an Object used to identify the entity in charge of the
    * acquisition. This can be used by recipients to distinguish different
    * types of acquisitions. You must re-use the same object for the
    * AcquisitionEndedEvent that you post.
    */
   Object getSource();
}
