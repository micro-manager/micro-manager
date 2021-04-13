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

/**
 * This class signifies that an image has been deleted in the Datastore.
 */
public interface ImageDeletedEvent {
   /**
    * Provides the Image that was deleted.
    * @return the Image that was just deleted from the Datastore.
    */
   Image getImage();

   /**
    * Provides the Datastore this image was added to; potentially useful for
    * code that listens to events from multiple Datastores.
    * @return the Datastore this image was added to.
    */
   Datastore getDatastore();
}
