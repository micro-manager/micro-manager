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

import java.io.IOException;
import java.util.Collection;
import org.micromanager.Album;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.ReportingUtils;

public final class DefaultAlbum implements Album {
   private static final DefaultAlbum STATIC_INSTANCE;
   static {
      STATIC_INSTANCE = new DefaultAlbum();
   }

   private Datastore store_;
   private Integer curTime_ = null;

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public boolean addImage(Image image) throws IOException {
      MMStudio studio = MMStudio.getInstance();
      boolean mustCreateNew = (store_ == null || store_.isFrozen());
      String curChannel = "";
      try {
         curChannel = studio.core().getCurrentConfig(
               studio.core().getChannelGroup());
      }
      catch (Exception e) {
         studio.logs().logError(e, "Error getting current channel name");
      }
      if (store_ != null) {
         String oldChannel = store_.getSummaryMetadata().getSafeChannelName(0);
         if (!oldChannel.contentEquals(curChannel)) {
            mustCreateNew = true;
         }
      }
      if (mustCreateNew) {
         // Need to create a new album.
         store_ = studio.data().createRAMDatastore();
         try {
            SummaryMetadata.Builder smb = studio.acquisitions().
                    generateSummaryMetadata().copyBuilder();
            // TODO: can there be other axes than T?
            smb.channelNames(new String[] {curChannel}).axisOrder(Coords.T);
            store_.setSummaryMetadata(smb.build());
         }
         catch (DatastoreFrozenException e) {
            // This should never happen!
            studio.logs().logError(e, "Unable to set summary of newly-created datastore");
         }
         catch (DatastoreRewriteException e) {
            // This should also never happen!
            studio.logs().logError(e, "Unable to set summary of newly-created datastore");
         }
         studio.displays().manage(store_);
         DisplayWindow display = studio.displays().createDisplay(store_);
         display.setCustomTitle("Album");
         curTime_ = null;
      }
      
      Coords newCoords = createAlbumCoords(image);

      try {
         store_.putImage(image.copyAtCoords(newCoords));
      }
      catch (DatastoreFrozenException e) {
         ReportingUtils.showError(e, "Album datastore is locked.");
      }
      catch (DatastoreRewriteException e) {
         // This should never happen.
         ReportingUtils.showError(e, "Unable to add image at " + newCoords + 
                 " to album as another image with those coords already exists.");
      }
      return mustCreateNew;
   }
   
   public Coords createAlbumCoords(Image image) throws IOException {
      // We want to add new images to the next timepoint, or to the current
      // timepoint if there's no image for this channel at the current
      // timepoint.
      if (curTime_ == null) {
         curTime_ = 0;
      }
      else {
         // Try to find images at this timepoint and channel, which would mean
         // we need to move to the next timepoint.
         Coords matcher = Coordinates.builder()
            .channel(image.getCoords().getChannel())
            .t(curTime_)
            .build();
         if (store_.getImagesMatching(matcher).size() > 0) {
            // Have an image at this time/channel pair already.
            curTime_++;
         }
      }
      return image.getCoords().copy().time(curTime_).build();
   }

   @Override
   public boolean addImages(Collection<Image> images) throws IOException {
      boolean result = false;
      for (Image image : images) {
         // Watch out for boolean logic short-circuiting here!
         boolean tmp = addImage(image);
         result = result || tmp;
      }
      return result;
   }

   public static DefaultAlbum getInstance() {
      return STATIC_INSTANCE;
   }
}
