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

import com.google.common.eventbus.Subscribe;
import org.micromanager.Album;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.internal.utils.ReportingUtils;

public final class DefaultAlbum implements Album {
   private final Studio studio_;
   private Datastore store_;
   private Integer curTime_ = null;
   private Pipeline pipeline_;
   private final Object pipelineLock_ = new Object();
   
   public DefaultAlbum(Studio studio) {
      studio_ = studio;
      studio_.displays().registerForEvents(this);
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public boolean addImage(Image image) throws IOException {
      boolean mustCreateNew = (store_ == null || store_.isFrozen());

      // check if this image is the same size as the ones in the store
      if (store_ != null) {
         Image storedImage = store_.getAnyImage();
         if (storedImage != null) {
            if (image.getBytesPerPixel() != storedImage.getBytesPerPixel()
                    || image.getWidth() != storedImage.getWidth()
                    || image.getHeight() != storedImage.getHeight()
                    || image.getNumComponents() != storedImage.getNumComponents()) {
               mustCreateNew = true;
            }
         }
      }

      String curChannel = "";
      try {
         curChannel = studio_.core().getCurrentConfig(
               studio_.core().getChannelGroup());
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Error getting current channel name");
      }
      /*
       * This code determines if the channel has changed, and opens a new
       * new Album if so.  The motivation for this behavior is unclear, and 
       * it certainly bothers some people (including myself, NS), so remove
       * for now.  If the old behavior is desired by some, there can be 
       * an option added.
       *
      if (store_ != null) {
         String oldChannel = store_.getSummaryMetadata().getSafeChannelName(0);
         if (!oldChannel.contentEquals(curChannel)) {
            mustCreateNew = true;
         }
      }
      */
      if (mustCreateNew) {
         // Need to create a new album.
         
         store_ = studio_.data().createRAMDatastore();

         try {
            SummaryMetadata.Builder smb = studio_.acquisitions().
                    generateSummaryMetadata().copyBuilder();
            // TODO: can there be other axes than T?
            smb.channelNames(new String[] {curChannel}).axisOrder(
                    Coords.T, Coords.C, Coords.Z, Coords.P);
            store_.setSummaryMetadata(smb.build());
         }
         catch (DatastoreFrozenException | DatastoreRewriteException e) {
            // This should never happen!
            studio_.logs().logError(e, "Unable to set summary of newly-created datastore");
         }
         studio_.displays().manage(store_);
         DisplayWindow display = studio_.displays().createDisplay(store_);
         display.setCustomTitle("Album");
         curTime_ = null;
      }
      
      Coords newCoords = createAlbumCoords(image);

      try {
         synchronized (pipelineLock_) {
            if (pipeline_ != null) {
               pipeline_.halt();
            }
            // Renew the pipeline with every image to reflect changes made to 
            // the pipeline in the mean time.
            // This approach runs the risk that the new pipeline changes the image
            // size, which is bad and results in uncaught, unreported exceptions
            // TODO: at the very least report problems with image size to the user
            // 
            // When users press the Album button in the viewer, this code will 
            // send the image through the pipeline for a second time.  That can 
            // never be the intent of the user.  So, it would be best to have 
            // a "use pipeline" parameter in the addImage function.  At this point,
            // I do not want to touch the API.  As a work-around use the 
            // MDA pipeline ratehr than the LivePipeline.  That gives the user
            // the ability to uncouple the Live and Album pipelines (albeit in 
            // an obscure way.
            pipeline_ = studio_.data().copyApplicationPipeline(store_, true);
            try {
               pipeline_.insertImage(image.copyAtCoords(newCoords));
            } catch (DatastoreRewriteException e) {
               // This should never happen, because we use an erasable
               // Datastore.
               studio_.logs().showError(e,
                       "Unable to insert image into pipeline; this should never happen.");
            } catch (PipelineErrorException e) {
               // Notify the user, and halt live.
               studio_.logs().showError(e,
                       "An error occurred while processing images.");
               pipeline_.clearExceptions();
            }
         }
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
      return image.getCoords().copyBuilder().t(curTime_).build();
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

   @Subscribe
   public void onAlbumStoreClosing(DataViewerWillCloseEvent viewerWillCloseEvent) {
      if (viewerWillCloseEvent.getDataViewer().getDataProvider().equals(store_)) {
         store_ = null;
      }
   }

}