/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.util.concurrent.BlockingQueue;
import org.micromanager.api.EngineTask;
import org.micromanager.utils.GentleLinkedBlockingQueue;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class SequenceGenerator extends Thread {

   private BlockingQueue<EngineTask> engineRequestSequence_;
   private double exposure_;
   private SequenceSettings sequenceSettings_;
   private ImageRequest stopRequest_ = new ImageRequest();

   public SequenceGenerator(SequenceSettings settings, double exposure) {
      engineRequestSequence_ = new GentleLinkedBlockingQueue<EngineTask>(100);
      stopRequest_.stop = true;
      sequenceSettings_ = settings;
      exposure_ = exposure;
      stopRequest_.FrameIndex = -1;
   }

   public BlockingQueue<EngineTask> begin() {
      start();
      return engineRequestSequence_;
   }

   @Override
   public void run() {
      ImageRequest lastImageRequest = new ImageRequest();
      lastImageRequest.PositionIndex = -1;
      lastImageRequest.SliceIndex = -1;
      lastImageRequest.ChannelIndex = -1;
      lastImageRequest.FrameIndex = -1;

      boolean skipImage;
      boolean skipLastImage = true;

      int numPositions = Math.max(1, (int) sequenceSettings_.positions.size());
      int numFrames = Math.max(1, (int) sequenceSettings_.numFrames);
      int numChannels = Math.max(1, (int) sequenceSettings_.channels.size());
      int numSlices = Math.max(1, (int) sequenceSettings_.slices.size());
      int numImages = numPositions * numFrames * numChannels * numSlices;

      for (int imageIndex = 0; imageIndex < (1 + numImages); ++imageIndex) {
         ImageRequest imageRequest = new ImageRequest();
         imageRequest.UsePosition = (sequenceSettings_.positions.size() > 0);
         imageRequest.UseFrame = (sequenceSettings_.numFrames > 0);
         imageRequest.UseChannel = (sequenceSettings_.channels.size() > 0);
         imageRequest.UseSlice = (sequenceSettings_.slices.size() > 0);

         imageRequest.relativeZSlices = sequenceSettings_.relativeZSlice;
         imageRequest.zReference = sequenceSettings_.zReference;
         imageRequest.exposure = exposure_;

         skipImage = false;
         imageRequest.CloseShutter = true;

         if (sequenceSettings_.slicesFirst) {
            imageRequest.SliceIndex = imageIndex % numSlices;
            imageRequest.ChannelIndex = (imageIndex / numSlices) % numChannels;
         } else { // channels first
            imageRequest.ChannelIndex = imageIndex % numChannels;
            imageRequest.SliceIndex = (imageIndex / numChannels) % numSlices;
         }

         if (sequenceSettings_.timeFirst) {
            imageRequest.FrameIndex = (imageIndex / (numChannels * numSlices)) % numFrames;
            imageRequest.PositionIndex = (imageIndex / (numChannels * numSlices * numFrames)) % numPositions;
         } else { // time first
            imageRequest.PositionIndex = (imageIndex / (numChannels * numSlices)) % numPositions;
            imageRequest.FrameIndex = (imageIndex / (numChannels * numSlices * numPositions)) % numFrames;
         }

         if (imageRequest.UseFrame && imageRequest.FrameIndex > 0 && imageRequest.PositionIndex <= 0 // &&
                 && imageRequest.ChannelIndex <= 0 && imageRequest.SliceIndex <= 0) {
            imageRequest.WaitTime = sequenceSettings_.intervalMs;
         } else {
            imageRequest.WaitTime = 0;
         }

         if (imageRequest.UsePosition) {
            imageRequest.Position = sequenceSettings_.positions.get(imageRequest.PositionIndex);
         }

         if (imageRequest.UseSlice) {
            imageRequest.SlicePosition = sequenceSettings_.slices.get(imageRequest.SliceIndex);
         }

         if (imageRequest.UseChannel) {
            imageRequest.Channel = sequenceSettings_.channels.get(imageRequest.ChannelIndex);
            if (0 != (imageRequest.FrameIndex % (imageRequest.Channel.skipFactorFrame_ + 1))) {
               skipImage = true;
            }
         }

         if (imageRequest.UseChannel && imageRequest.UseSlice) {
            if (!imageRequest.Channel.doZStack_ && (imageRequest.SliceIndex != (sequenceSettings_.slices.size() - 1) / 2)) {
               skipImage = true;
            }
         }

         imageRequest.AutoFocus = sequenceSettings_.useAutofocus &&
                 ((lastImageRequest.FrameIndex != imageRequest.FrameIndex)
                  || (lastImageRequest.PositionIndex != imageRequest.PositionIndex));

         if (imageRequest.UseFrame) {
            imageRequest.AutoFocus = imageRequest.AutoFocus
                    && (0 == (imageRequest.FrameIndex % (1 + sequenceSettings_.skipAutofocusCount)));
         }

         if (imageIndex > 0) {
            if (imageRequest.FrameIndex == lastImageRequest.FrameIndex
                    && imageRequest.PositionIndex == lastImageRequest.PositionIndex) {
               if (sequenceSettings_.keepShutterOpenChannels
                       && !sequenceSettings_.keepShutterOpenSlices) {
                  if (imageRequest.SliceIndex == lastImageRequest.SliceIndex) {
                     lastImageRequest.CloseShutter = false;
                  }
               }

               if (sequenceSettings_.keepShutterOpenSlices
                       && !sequenceSettings_.keepShutterOpenChannels) {
                  if (imageRequest.ChannelIndex == lastImageRequest.ChannelIndex) {
                     lastImageRequest.CloseShutter = false;
                  }
               }

               if (sequenceSettings_.keepShutterOpenSlices
                       && sequenceSettings_.keepShutterOpenChannels) {
                  lastImageRequest.CloseShutter = false;
               }
            }

            if (imageRequest.WaitTime > 0) {
               lastImageRequest.NextWaitTime = imageRequest.WaitTime;
            }
            if (!skipLastImage) {
               putTask(new ImageTask(lastImageRequest));
            }
         }

         if (imageIndex == 0 || !skipImage) {
            lastImageRequest = imageRequest;
         }
         skipLastImage = skipImage;
      }
      putTask(new StopTask());
   }

   private void putTask(EngineTask request) {
      try {
         engineRequestSequence_.put(request);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
   }
}
