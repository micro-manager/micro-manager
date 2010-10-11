/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import org.micromanager.api.DataProcessor;

/**
 *
 * @author arthur
 */
public class BurstMaker extends DataProcessor<ImageRequest> {
   ImageRequest lastRequest_ = null;
   boolean currentlyInBurst_ = false;
   @Override
   protected void process() {
      ImageRequest thisRequest = this.poll();
      if (lastRequest_ != null) {
         boolean burstValid
                 = ((lastRequest_.exposure == thisRequest.exposure)
                 && (lastRequest_.Position == thisRequest.Position)
                 && (lastRequest_.SliceIndex  == thisRequest.SliceIndex)
                 && (lastRequest_.ChannelIndex == thisRequest.ChannelIndex)
                 && (thisRequest.WaitTime <= lastRequest_.exposure)
                 && (thisRequest.AutoFocus == false));

         if (burstValid) {
            if (!currentlyInBurst_) {
               lastRequest_.startBurst = true;
               currentlyInBurst_ = true;
            }
            lastRequest_.collectBurst = true;
         }
         produce(lastRequest_);
      }
      lastRequest_ = thisRequest;
      if (thisRequest.stop)
         produce(thisRequest);
   }

}
