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
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.display.DisplayWindow;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.ReportingUtils;

public class DefaultAlbum implements Album {
   private static final DefaultAlbum staticInstance_;
   static {
      staticInstance_ = new DefaultAlbum();
   }

   private Datastore store_;
   private Integer curTime_ = null;

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
         DisplayWindow display = studio.displays().createDisplay(store_);
         display.setCustomTitle("Album");
         curTime_ = null;
         didCreateNew = true;
      }
      // We want to add new images to the next timepoint, or to the current
      // timepoint if there's no image for this channel at the current
      // timepoint.
      if (curTime_ == null) {
         curTime_ = 0;
      }
      else {
         // Try to find images at this timepoint and channel, which would mean
         // we need to move to the next timepoint.
         Coords matcher = studio.data().getCoordsBuilder()
            .channel(image.getCoords().getChannel())
            .time(curTime_)
            .build();
         if (store_.getImagesMatching(matcher).size() > 0) {
            // Have an image at this time/channel pair already.
            curTime_++;
         }
      }
      Coords newCoords = image.getCoords().copy().time(curTime_).build();
      try {
         image = image.copyAtCoords(newCoords);
         ((DefaultImage) image).splitMultiComponentIntoStore(store_);
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
         // Watch out for boolean logic short-circuiting here!
         boolean tmp = addImage(image);
         result = result || tmp;
      }
      return result;
   }

   public static DefaultAlbum getInstance() {
      return staticInstance_;
   }
}
