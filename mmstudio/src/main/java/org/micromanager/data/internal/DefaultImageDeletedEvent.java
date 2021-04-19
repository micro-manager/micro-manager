///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
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

package org.micromanager.data.internal;

import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.ImageDeletedEvent;

/**
 * This class signifies that an image has been deleted from a Datastore.
 *
 * This Event posts on the DataProvider bus.
 * Subscribe using {@link DataProvider#registerForEvents(Object)}.
 */
public final class DefaultImageDeletedEvent implements ImageDeletedEvent {
   private Image image_;
   private Datastore store_;

   public DefaultImageDeletedEvent(Image image, Datastore store) {
      image_ = image;
      store_ = store;
   }

   /**
    * Provides the Image that was deleted.
    * @return the Image that was just deleted from the Datastore.
    */
   @Override
   public Image getImage() {
      return image_;
   }

   /**
    * Provides the Datastore this image was added to; potentially useful for
    * code that listens to events from multiple Datastores.
    * @return the Datastore this image was added to.
    */
   @Override
   public Datastore getDatastore() {
      return store_;
   }
}
