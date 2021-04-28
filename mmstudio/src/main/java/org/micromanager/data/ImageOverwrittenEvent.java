///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc 2016
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

package org.micromanager.data;

import org.micromanager.MMEvent;

/**
 * This class signifies that an image has been overwritten in the Datastore.
 *
 * The default implementation of this Event posts on the DataProvider
 * event bus.  Subscribe using {@link DataProvider#registerForEvents(Object)}.
 */
public interface ImageOverwrittenEvent extends MMEvent {
   /**
    * Provides the newly-added image.
    * @return the Image that was just added to the Datastore.
    */
   Image getNewImage();

   /**
    * Provides the image that was overwritten.
    * @return the Image that was just overwritten in the Datastore.
    */
   Image getOldImage();

   /**
    * Provides the Datastore this image was added to; potentially useful for
    * code that listens to events from multiple Datastores.
    * @return the Datastore this image was added to.
    */
   Datastore getDatastore();
}
