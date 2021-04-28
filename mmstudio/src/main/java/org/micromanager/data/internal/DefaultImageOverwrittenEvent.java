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
import org.micromanager.data.ImageOverwrittenEvent;

/**
 * This class signifies that an image in a Datastore has been overwritten by a
 * new image.
 *
 * This Event posts on the DataProvider bus.
 * Subscribe using {@link DataProvider#registerForEvents(Object)}.
 */
public final class DefaultImageOverwrittenEvent implements ImageOverwrittenEvent {
   private Image newImage_;
   private Image oldImage_;
   private Datastore store_;

   public DefaultImageOverwrittenEvent(Image newImage, Image oldImage,
         Datastore store) {
      newImage_ = newImage;
      oldImage_ = oldImage;
      store_ = store;
   }

   /**
    * Provides the newly-added image.
    * @return the Image that was just added to the Datastore.
    */
   @Override
   public Image getNewImage() {
      return newImage_;
   }

   /**
    * Provides the image that was overwritten.
    * @return the Image that was just overwritten in the Datastore.
    */
   @Override
   public Image getOldImage() {
      return oldImage_;
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
