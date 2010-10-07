/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.util.concurrent.LinkedBlockingQueue;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class SequenceGenerator extends Thread {

   private LinkedBlockingQueue<ImageRequest> engineRequestSequence_;
   private double exposure_;
   private SequenceSettings sequence_;
   private Engine eng_;
   private ImageRequest stopRequest_ = new ImageRequest();

   public SequenceGenerator() {
      engineRequestSequence_ = new LinkedBlockingQueue<ImageRequest>(100);
      stopRequest_.stop = true;
   }

   public LinkedBlockingQueue<ImageRequest> generateSequence(Engine eng, SequenceSettings settings, double exposure) {
      this.sequence_ = settings;
      this.exposure_ = exposure;
      this.eng_ = eng;
      stopRequest_.FrameIndex = -1;
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

      int numPositions = Math.max(1, (int) sequence_.positions.size());
      int numFrames = Math.max(1, (int) sequence_.numFrames);
      int numChannels = Math.max(1, (int) sequence_.channels.size());
      int numSlices = Math.max(1, (int) sequence_.slices.size());
      int numImages = numPositions * numFrames * numChannels * numSlices;

      for (int imageIndex = 0; imageIndex < (1 + numImages); ++imageIndex) {
         ImageRequest imageRequest = new ImageRequest();
         imageRequest.UsePosition = (sequence_.positions.size() > 0);
         imageRequest.UseFrame = (sequence_.numFrames > 0);
         imageRequest.UseChannel = (sequence_.channels.size() > 0);
         imageRequest.UseSlice = (sequence_.slices.size() > 0);

         imageRequest.relativeZSlices = sequence_.relativeZSlice;
         imageRequest.zReference = sequence_.zReference;
         imageRequest.exposure = exposure_;

         skipImage = false;
         imageRequest.CloseShutter = true;

         if (sequence_.slicesFirst) {
            imageRequest.SliceIndex = imageIndex % numSlices;
            imageRequest.ChannelIndex = (imageIndex / numSlices) % numChannels;
         } else { // channels first
            imageRequest.ChannelIndex = imageIndex % numChannels;
            imageRequest.SliceIndex = (imageIndex / numChannels) % numSlices;
         }

         if (sequence_.timeFirst) {
            imageRequest.FrameIndex = (imageIndex / (numChannels * numSlices)) % numFrames;
            imageRequest.PositionIndex = (imageIndex / (numChannels * numSlices * numFrames)) % numPositions;
         } else { // time first
            imageRequest.PositionIndex = (imageIndex / (numChannels * numSlices)) % numPositions;
            imageRequest.FrameIndex = (imageIndex / (numChannels * numSlices * numPositions)) % numFrames;
         }

         if (imageRequest.UseFrame && imageRequest.FrameIndex > 0 && imageRequest.PositionIndex <= 0 // &&
                 && imageRequest.ChannelIndex <= 0 && imageRequest.SliceIndex <= 0) {
            imageRequest.WaitTime = sequence_.intervalMs;
         } else {
            imageRequest.WaitTime = 0;
         }

         if (imageRequest.UsePosition) {
            imageRequest.Position = sequence_.positions.get(imageRequest.PositionIndex);
         }

         if (imageRequest.UseSlice) {
            imageRequest.SlicePosition = sequence_.slices.get(imageRequest.SliceIndex);
         }

         if (imageRequest.UseChannel) {
            imageRequest.Channel = sequence_.channels.get(imageRequest.ChannelIndex);
            if (0 != (imageRequest.FrameIndex % (imageRequest.Channel.skipFactorFrame_ + 1))) {
               skipImage = true;
            }
         }

         if (imageRequest.UseChannel && imageRequest.UseSlice) {
            if (!imageRequest.Channel.doZStack_ && (imageRequest.SliceIndex != (sequence_.slices.size() - 1) / 2)) {
               skipImage = true;
            }
         }

         imageRequest.AutoFocus = sequence_.useAutofocus &&
                 ((lastImageRequest.FrameIndex != imageRequest.FrameIndex)
                  || (lastImageRequest.PositionIndex != imageRequest.PositionIndex));

         if (imageRequest.UseFrame) {
            imageRequest.AutoFocus = imageRequest.AutoFocus
                    && (0 == (imageRequest.FrameIndex % (1 + sequence_.skipAutofocusCount)));
         }

         if (imageIndex > 0) {
            if (imageRequest.FrameIndex == lastImageRequest.FrameIndex
                    && imageRequest.PositionIndex == lastImageRequest.PositionIndex) {
               if (sequence_.keepShutterOpenChannels
                       && !sequence_.keepShutterOpenSlices) {
                  if (imageRequest.SliceIndex == lastImageRequest.SliceIndex) {
                     lastImageRequest.CloseShutter = false;
                  }
               }

               if (sequence_.keepShutterOpenSlices
                       && !sequence_.keepShutterOpenChannels) {
                  if (imageRequest.ChannelIndex == lastImageRequest.ChannelIndex) {
                     lastImageRequest.CloseShutter = false;
                  }
               }

               if (sequence_.keepShutterOpenSlices
                       && sequence_.keepShutterOpenChannels) {
                  lastImageRequest.CloseShutter = false;
               }
            }

            if (imageRequest.WaitTime > 0) {
               lastImageRequest.NextWaitTime = imageRequest.WaitTime;
            }
            if (!skipLastImage) {
               putRequest(lastImageRequest);
            }
         }

         if (imageIndex == 0 || !skipImage) {
            lastImageRequest = imageRequest;
         }
         skipLastImage = skipImage;
      }
      putRequest(stopRequest_);
   }

   private void putRequest(ImageRequest request) {
      try {
         engineRequestSequence_.put(request);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
   }
}
