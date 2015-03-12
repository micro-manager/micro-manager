///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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
//

package org.micromanager.internal;

import java.util.Collection;

import org.micromanager.Album;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

public class DefaultAlbum implements Album {
   private static final DefaultAlbum staticInstance_;
   static {
      staticInstance_ = new DefaultAlbum();
   }

   private Datastore store_;

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public boolean addImage(Image image) {
      MMStudio studio = MMStudio.getInstance();
      boolean didCreateNew = false;
      if (store_ == null || store_.getIsFrozen()) {
         // Need to create a new album.
         store_ = studio.data().createRAMDatastore();
         studio.displays().manage(store_);
         studio.displays().createDisplay(store_);
         didCreateNew = true;
      }
      // Adjust image coordinates to be at the N+1th timepoint, except for the
      // first timepoint of course.
      int time = store_.getAxisLength(Coords.TIME);
      if (time > 0) {
         time++;
      }
      Coords newCoords = image.getCoords().copy().time(time).build();
      try {
         store_.putImage(image.copyAtCoords(newCoords));
      }
      catch (DatastoreFrozenException e) {
         ReportingUtils.showError(e, "Album datastore is locked.");
      }
      return didCreateNew;
   }

   @Override
   public boolean addImages(Collection<Image> images) {
      boolean result = false;
      for (Image image : images) {
         result = result || addImage(image);
      }
      return result;
   }

   public static DefaultAlbum getInstance() {
      return staticInstance_;
   }
}
