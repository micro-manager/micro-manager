///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImageCache.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
// COPYRIGHT:    University of California, San Francisco, 2010
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
package org.micromanager.magellan.acq;

import java.lang.reflect.InvocationTargetException;
import org.micromanager.magellan.datasaving.MultiResMultipageTiffStorage;
import org.micromanager.magellan.imagedisplay.MagellanDisplay;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.magellan.misc.Log;

/**
 * MMImageCache: central repository of Images Holds pixels and metadata to be
 * used for display or save on disk
 *
 *
 * @author arthur
 */
public class MagellanImageCache {

   private MagellanDisplay display_;
   private MultiResMultipageTiffStorage imageStorage_;
   private final ExecutorService listenerExecutor_;
   private volatile boolean imageCacheFinished_ = false;

   public void setDisplay(MagellanDisplay d) {
      display_ = d;
   }

   public MagellanImageCache(MultiResMultipageTiffStorage imageStorage) {
      imageStorage_ = imageStorage;
      listenerExecutor_ = Executors.newFixedThreadPool(1);
   }

   /**
    * Called when images done arriving
    */
   public void finished() {
      if (!imageStorage_.isFinished()) {
         imageStorage_.finishedWriting();
      }
      final String path = getDiskLocation();
      try {
         SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
               display_.imagingFinished(path);
               listenerExecutor_.shutdown();
               imageCacheFinished_ = true;
            }
         });
      } catch (Exception ex) {
         throw new RuntimeException(ex);
      }

   }

   public boolean isFinished() {
      return imageStorage_.isFinished();
   }

   public String getDiskLocation() {
      return imageStorage_.getDiskLocation();
   }

   public JSONObject getDisplayAndComments() {
      return imageStorage_.getDisplaySettings();
   }

   /**
    * Call when display and acquisition both done
    */
   public void close() {
      if (imageCacheFinished_) {
         imageStorage_.close();
         display_ = null;
      } else {
         //keep resubmitting so that finish, which comes from a different thread, happens first
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               MagellanImageCache.this.close();
            }
         });
      }
   }

   public void putImage(final TaggedImage taggedImg) {
      imageStorage_.putImage(taggedImg);
      //let the display know theres a new image in town
      listenerExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            display_.imageReceived(taggedImg);
         }
      });

   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      return imageStorage_.getImage(channel, slice, frame, position);
   }

   public JSONObject getSummaryMetadata() {
      if (imageStorage_ == null) {
         Log.log("imageStorage_ is null in getSummaryMetadata", true);
         return null;
      }
      return imageStorage_.getSummaryMetadata();
   }

   public void setSummaryMetadata(JSONObject tags) {
      if (imageStorage_ == null) {
         Log.log("imageStorage_ is null in setSummaryMetadata", true);
         return;
      }
      imageStorage_.setSummaryMetadata(tags);
   }

}
