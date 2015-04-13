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

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

/**
 * This class signifies that an image has been added to a Datastore.
 * TODO: should be renamed to DefaultNewImageEvent.
 */
public class NewImageEvent implements org.micromanager.data.NewImageEvent {
   private Image image_;
   private Datastore store_;

   public NewImageEvent(Image image, Datastore store) {
      image_ = image;
      store_ = store;
   }

   @Override
   public Image getImage() {
      return image_;
   }

   @Override
   public Coords getCoords() {
      return image_.getCoords();
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }
}
