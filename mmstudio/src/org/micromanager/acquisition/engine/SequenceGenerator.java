/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.util.ArrayList;

/**
 *
 * @author arthur
 */
public class SequenceGenerator {

   public static ArrayList<EngineTask> makeTaskSequence(Engine eng, ArrayList<ImageRequest> requestSequence) {
      ArrayList<EngineTask> imageTaskSequence = new ArrayList<EngineTask>();
      for (ImageRequest imageRequest:requestSequence) {
         imageTaskSequence.add(new ImageTask(eng, imageRequest));
      }
      return imageTaskSequence;
   }

   public static ArrayList<ImageRequest> generateSequence(SequenceSettings settings, double exposure) {
      ArrayList<ImageRequest> imageRequestList = new ArrayList<ImageRequest>();

      ImageRequest lastImageRequest = new ImageRequest();

      boolean skipImage;
      boolean skipLastImage = true;
     
      int numPositions = Math.max(1, (int) settings.positions.size());
      int numFrames = Math.max(1, (int) settings.numFrames);
      int numChannels = Math.max(1, (int) settings.channels.size());
      int numSlices = Math.max(1, (int) settings.slices.size());
      int numImages = numPositions * numFrames * numChannels * numSlices;

      for (int imageIndex = 0; imageIndex < (1 + numImages); ++imageIndex) {
         ImageRequest imageRequest = new ImageRequest();
         imageRequest.UsePosition = (settings.positions.size() > 0);
         imageRequest.UseFrame = (settings.numFrames > 0);
         imageRequest.UseChannel = (settings.channels.size() > 0);
         imageRequest.UseSlice = (settings.slices.size() > 0);

         imageRequest.relativeZSlices = settings.relativeZSlice;
         imageRequest.zReference = settings.zReference;
         imageRequest.exposure = exposure;

         skipImage = false;
         imageRequest.CloseShutter = true;

         if (settings.slicesFirst) {
            imageRequest.SliceIndex = imageIndex % numSlices;
            imageRequest.ChannelIndex = (imageIndex / numSlices) % numChannels;
         } else { // channels first
            imageRequest.ChannelIndex = imageIndex % numChannels;
            imageRequest.SliceIndex = (imageIndex / numChannels) % numSlices;
         }

         if (settings.timeFirst) {
            imageRequest.FrameIndex = (imageIndex / (numChannels * numSlices)) % numFrames;
            imageRequest.PositionIndex = (imageIndex / (numChannels * numSlices * numFrames)) % numPositions;
         } else { // time first
            imageRequest.PositionIndex = (imageIndex / (numChannels * numSlices)) % numPositions;
            imageRequest.FrameIndex = (imageIndex / (numChannels * numSlices * numPositions)) % numFrames;
         }

         if (imageRequest.UseFrame && imageRequest.FrameIndex > 0 && imageRequest.PositionIndex <= 0 // &&
                 && imageRequest.ChannelIndex <= 0 && imageRequest.SliceIndex <= 0) {
            imageRequest.WaitTime = settings.intervalMs;
         } else {
            imageRequest.WaitTime = 0;
         }

         if (imageRequest.UsePosition) {
            imageRequest.Position = settings.positions.get(imageRequest.PositionIndex);
         }

         if (imageRequest.UseSlice) {
            imageRequest.SlicePosition = settings.slices.get(imageRequest.SliceIndex);
         }

         if (imageRequest.UseChannel) {
            imageRequest.Channel = settings.channels.get(imageRequest.ChannelIndex);
            if (0 != (imageRequest.FrameIndex % (imageRequest.Channel.skipFactorFrame_ + 1))) {
               skipImage = true;
            }
         }

         if (imageRequest.UseChannel && imageRequest.UseSlice) {
            if (!imageRequest.Channel.doZStack_ && (imageRequest.SliceIndex != (settings.slices.size() - 1) / 2)) {
               skipImage = true;
            }
         }

         imageRequest.AutoFocus = settings.useAutofocus;
         if (imageRequest.UseFrame) {
            imageRequest.AutoFocus = imageRequest.AutoFocus
                    && (0 == (imageRequest.FrameIndex % (1 + settings.skipAutofocusCount)));
         }

         if (imageIndex > 0) {
            if (imageRequest.FrameIndex == lastImageRequest.FrameIndex
                    && imageRequest.PositionIndex == lastImageRequest.PositionIndex) {
               if (settings.keepShutterOpenChannels
                       && !settings.keepShutterOpenSlices) {
                  if (imageRequest.SliceIndex == lastImageRequest.SliceIndex) {
                     lastImageRequest.CloseShutter = false;
                  }
               }

               if (settings.keepShutterOpenSlices
                       && !settings.keepShutterOpenChannels) {
                  if (imageRequest.ChannelIndex == lastImageRequest.ChannelIndex) {
                     lastImageRequest.CloseShutter = false;
                  }
               }

               if (settings.keepShutterOpenSlices
                       && settings.keepShutterOpenChannels) {
                  lastImageRequest.CloseShutter = false;
               }
            }

            if (!skipLastImage) {
               imageRequestList.add(lastImageRequest);
            }
         }

         if (imageIndex == 0 || !skipImage) {
            lastImageRequest = imageRequest;
         }
         skipLastImage = skipImage;
      }
      
      imageRequestList.get(imageRequestList.size()-1).CloseShutter = true;
      return imageRequestList;
   }
   
}
