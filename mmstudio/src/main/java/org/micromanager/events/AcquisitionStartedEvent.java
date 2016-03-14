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

package org.micromanager.events;

import org.micromanager.data.Datastore;
import org.micromanager.SequenceSettings;

/**
 * This class signals that an acquisition is starting, and provides access to
 * the Datastore that images from the acquisition will be put into. Third-party
 * code may subclass this event to provide notifications of their own
 * acquisitions.
 */
public interface AcquisitionStartedEvent {
   /**
    * Return the Datastore into which images will be inserted during the
    * acquisition.
    */
   public Datastore getDatastore();

   /**
    * Return an Object used to identify the entity in charge of the
    * acquisition. This can be used by recipients to distinguish different
    * types of acquisitions. If you have code that publishes
    * AcquisitionStartedEvents, then that code should re-use the same object
    * for each event.
    */
   public Object getSource();
}
