/**
 * Micro-Manager "Live Replay"
 * 
 * This plugin will copy all images in the Micro-Manager circular buffer
 * into a viewer window.  The counter of the circular buffer is set to 0 (i.e.
 * images in the circular buffer will be destroyed.
 * 
 * This is useful when you see something interesting in live mode and want to 
 * save this data.
 * 
 * 
 * Nico Stuurman, 2009(?)
 * 
 * Updated June 2013 to use ImageProcssor Queue, so that images from multiple 
 * cameras are displayed correctly and so that images are all processed by
 * the default image processing queue.
 * 
 * 
 * Copyright University of California
 * 
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.recall;

import java.util.concurrent.LinkedBlockingQueue;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.internal.utils.MMTags;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMScriptException;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class RecallPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Live Replay";
   public static final String tooltipDescription =
      "Recalls (live) images left over in the internal sequence buffer";
   private CMMCore core_;
   private MMStudio studio_;
   // TODO: assign this name to the viewer window once the api has that ability
   private final String ACQ_NAME = "Live Replay";
   private int multiChannelCameraNrCh_;
   private Datastore store_;
   
  
   @Override
   public void setContext(Studio studio) {
      studio_ = (MMStudio) studio;
      core_ = studio.getCMMCore();
   }

   @Override
   public void onPluginSelected() {
      store_ = studio_.data().createRAMDatastore();
      studio_.getDisplayManager().createDisplay(store_);
      studio_.getDisplayManager().manage(store_);

      int remaining = core_.getRemainingImageCount();
      if (remaining < 1) {
         studio_.logs().showMessage("There are no Images in the Micro-Manager buffer");
         return;
      }
      
      multiChannelCameraNrCh_ = (int) core_.getNumberOfCameraChannels();
      String camera = core_.getCameraDevice();

      if (multiChannelCameraNrCh_ == 1) {
         int frameCounter = 0;
         for (int i = 0; i < remaining; i++) {
            try {
               TaggedImage tImg = core_.popNextTaggedImage();
               normalizeTags(tImg, frameCounter);
               frameCounter++;
               store_.putImage(studio_.data().convertTaggedImage(tImg));
            }
            catch (DatastoreFrozenException e) { // Can't add to datastore.
               studio_.logs().logError(e);
            }
            catch (JSONException e) { // Error in TaggedImage tags
               studio_.logs().logError(e);
            }
            catch (Exception e) { // Error in popNextTaggedImage
               studio_.logs().logError(e);
            }
         }
      } else if (multiChannelCameraNrCh_ > 1) {
         int[] frameCounters = new int[multiChannelCameraNrCh_];
         for (int i = 0; i < remaining; i++) {
            try {
               TaggedImage tImg = core_.popNextTaggedImage();
            
               if (!tImg.tags.has(camera + "-CameraChannelName")) {
                  continue;
               }
               String channelName = tImg.tags.getString(camera + "-CameraChannelName");
               tImg.tags.put("Channel", channelName);
               int channelIndex = tImg.tags.getInt(camera + "-CameraChannelIndex");
               tImg.tags.put("ChannelIndex", channelIndex);
               normalizeTags(tImg, frameCounters[channelIndex]);
               frameCounters[channelIndex]++;
               store_.putImage(studio_.data().convertTaggedImage(tImg));
            }
            catch (DatastoreFrozenException e) { // Can't add to datastore.
               studio_.logs().logError(e);
            }
            catch (JSONException e) { // Error in TaggedImage tags
               studio_.logs().logError(e);
            }
            catch (Exception e) { // Error in popNextTaggedImage
               studio_.logs().logError(e);
            }
         }
      }        
   }

   private void normalizeTags(TaggedImage ti, int frameIndex) {
      if (ti != TaggedImageQueue.POISON) {
         int channel = 0;
         try {
            if (ti.tags.has("Multi Camera-CameraChannelIndex")) {
               channel = ti.tags.getInt("Multi Camera-CameraChannelIndex");
            } else if (ti.tags.has("CameraChannelIndex")) {
               channel = ti.tags.getInt("CameraChannelIndex");
            } else if (ti.tags.has("ChannelIndex")) {
               channel = MDUtils.getChannelIndex(ti.tags);
            }
            ti.tags.put(MMTags.Image.CHANNEL_INDEX, channel);
            ti.tags.put(MMTags.Image.POS_INDEX, 0);
            ti.tags.put(MMTags.Image.SLICE_INDEX, 0);          
            ti.tags.put(MMTags.Image.FRAME, frameIndex);

         } catch (JSONException ex) {
            studio_.logs().logError(ex);
         }
      }
   }
   
   public void configurationChanged() {
   }

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getSubMenu() {
      return "";
   }

   @Override
   public String getHelpText() {
      return "Recalls live images remaining in internal buffer.  Set size of the buffer in options (under Tools menu). Note that the buffer loops periodically, erasing all history, so the number of recalled images is hard to predict.";
   }

   @Override
   public String getVersion() {
      return "V1.0";
   }
   
   @Override
   public String getCopyright() {
      return "University of California, 2010-2015";
   }
}
