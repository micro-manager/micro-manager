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


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class RecallPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String MENU_NAME = "Live Replay";
   public static final String TOOL_TIP_DESCRIPTION =
      "Recalls (live) images left over in the internal sequence buffer";
   private CMMCore core_;
   private MMStudio studio_;
   // TODO: assign this name to the viewer window once the api has that ability
   private final String ACQ_NAME = "Live Replay";
   private Datastore store_;
   
  
   @Override
   public void setContext(Studio studio) {
      studio_ = (MMStudio) studio;
      core_ = studio.getCMMCore();
   }

   @Override
   public void onPluginSelected() {
      int remaining = core_.getRemainingImageCount();
      int numCameraChannels = (int) core_.getNumberOfCameraChannels();
      if (remaining < 1) {
         studio_.logs().showMessage("There are no Images in the Micro-Manager buffer");
         return;
      }
      if (numCameraChannels <= 0) {
         studio_.logs().showMessage("No core channels detected");
         return;
      }

      store_ = studio_.data().createRAMDatastore();
      // It is imperative to set the axis order, or animation will not work correctly
      SummaryMetadata.Builder metadataBuilder = store_.getSummaryMetadata().copyBuilder();
      List<String> orderedAxis = new ArrayList<String>();
      orderedAxis.add(Coords.C); 
      orderedAxis.add(Coords.T);
      // TODO: even though we do not use Z and P, not including them causes the
      // display animation to stop working....
      orderedAxis.add(Coords.Z);
      orderedAxis.add(Coords.P);
      String[] channelNames = new String[numCameraChannels];
      for (int i = 0; i < numCameraChannels; i++) {
         channelNames[i] = "ch" + i;
      }
      metadataBuilder.channelNames(channelNames);
      try {
         store_.setSummaryMetadata(metadataBuilder.axisOrder(orderedAxis).build());
      } catch (IOException ioe) {
         studio_.logs().logError(ioe, "SummaryMetadat Error in Recall plugin");
      }
      studio_.getDisplayManager().createDisplay(store_);
      studio_.getDisplayManager().manage(store_);

      String camera = core_.getCameraDevice();

      if (numCameraChannels == 1) {
         int frameCounter = 0;
         for (int i = 0; i < remaining; i++) {
            try {
               TaggedImage tImg = core_.popNextTaggedImage();
               
               Image convertedTaggedImage = studio_.data().convertTaggedImage(tImg);
               if (convertedTaggedImage != null) {
                  store_.putImage(normalizeTags(convertedTaggedImage, tImg, frameCounter));
               }
               frameCounter++;
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
      } else {
         int[] frameCounters = new int[numCameraChannels];
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
               Image convertedTaggedImage = studio_.data().convertTaggedImage(tImg);
               if (convertedTaggedImage != null) {
                  store_.putImage( normalizeTags(
                          convertedTaggedImage, tImg, frameCounters[channelIndex]));
               }
               frameCounters[channelIndex]++;
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

   private Image normalizeTags(Image convertedTaggedImage, TaggedImage ti, int frameIndex) {
      
      Coords.Builder coordsBuilder = convertedTaggedImage.getCoords().copyBuilder();
      if (ti != TaggedImageQueue.POISON) {
         int channel = 0;
         
         try {
            if (ti.tags.has("Multi Camera-CameraChannelIndex")) {
               channel = ti.tags.getInt("Multi Camera-CameraChannelIndex");
            } else if (ti.tags.has("CameraChannelIndex")) {
               channel = ti.tags.getInt("CameraChannelIndex");
            } else if (ti.tags.has("ChannelIndex")) {
               channel = ti.tags.getInt("ChannelIndex");
            }
            
            Coords coords = coordsBuilder.channel(channel).t(frameIndex).build();
            
            return convertedTaggedImage.copyAtCoords(coords);

         } catch (JSONException ex) {
            studio_.logs().logError(ex);
         }
      }
                  
      Coords coords = coordsBuilder.c(0).t(frameIndex).build();    
      return convertedTaggedImage.copyAtCoords(coords);
   }
   
   public void configurationChanged() {
   }

   @Override
   public String getName() {
      return MENU_NAME;
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
      return "University of California, 2010-2017";
   }
   
}
