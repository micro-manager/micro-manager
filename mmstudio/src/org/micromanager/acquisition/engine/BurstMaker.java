/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import java.util.LinkedList;
import org.micromanager.api.DataProcessor;

/**
 *
 * @author arthur
 */
public class BurstMaker extends DataProcessor<ImageRequest> {
   private ImageRequest lastRequest_ = null;
   private LinkedList<ImageRequest> requestBank_ = new LinkedList<ImageRequest>();
   
   @Override
   protected void process() {
      ImageRequest thisRequest = this.poll();
      if (lastRequest_ != null) {
         accumulateRequest(lastRequest_);
         if (thisRequest.stop || !burstValid(lastRequest_, thisRequest)) {
            produceRequests();
         }
      }
      lastRequest_ = thisRequest;
      
      if (thisRequest.stop) {
         produce(thisRequest);
         lastRequest_ = null;
         requestStop();
      }
   }

   private void accumulateRequest(ImageRequest request) {
      requestBank_.add(request);
   }

   private void produceRequests() {
      int n = requestBank_.size();
      if (n > 1) {
         ImageRequest firstRequest = requestBank_.getFirst();
         firstRequest.startBurstN = n;
      }
      for (ImageRequest request:requestBank_) {
         if (n > 1)
            request.collectBurst = true;
         produce(request);
      }
      requestBank_.clear();
   }

   private boolean onlyCamerasDifferent(ImageRequest requestA, ImageRequest requestB) {
      return requestA.Channel.name_.contentEquals(requestB.Channel.name_);
   }

   private boolean burstValid(ImageRequest aRequest, ImageRequest nextRequest) {
      return
              ((aRequest.exposure == nextRequest.exposure)
           && (aRequest.Position == nextRequest.Position)
           && (aRequest.SliceIndex  == nextRequest.SliceIndex)
           && (aRequest.ChannelIndex == nextRequest.ChannelIndex)
           && (nextRequest.WaitTime <= lastRequest_.exposure)
           && (nextRequest.AutoFocus == false));
   }

}
