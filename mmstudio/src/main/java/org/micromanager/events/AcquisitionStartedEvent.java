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
 * the Datastore that images from the acquisition will be put into, as well
 * as the parameters governing how the acquisition will run.
 */
public interface AcquisitionStartedEvent {
   /**
    * Return the Datastore into which images will be inserted during the
    * acquisition.
    */
   public Datastore getDatastore();

   /**
    * Return the SequenceSettings used to control the parameters of the
    * acquisition. Note that the images in the datastore may not necessarily
    * match these parameters (with respect to number of Z-slices, etc.) due
    * to the actions of image processors in the data processing pipeline.
    */
   public SequenceSettings getSettings();
}
