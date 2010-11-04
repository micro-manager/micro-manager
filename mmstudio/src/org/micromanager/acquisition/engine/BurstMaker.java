/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import java.util.LinkedList;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.EngineTask;

/**
 *
 * @author arthur
 */
public class BurstMaker extends DataProcessor<EngineTask> {
   private EngineTask lastTask_ = null;
   private LinkedList<ImageTask> requestBank_ = new LinkedList<ImageTask>();
   
   @Override
   protected void process() {
      EngineTask thisTask = this.poll();
      if (lastTask_ != null) {
         if (lastTask_ instanceof ImageTask) {
            accumulateTask((ImageTask) lastTask_);
         }
         if ((thisTask instanceof StopTask) || !burstValid(lastTask_, thisTask)) {
            produceRequests();
         }
      }
      lastTask_ = thisTask;
      
      if (thisTask instanceof StopTask) {
         produce(thisTask);
         lastTask_ = null;
         requestStop();
      }
   }

   private void accumulateTask(ImageTask task) {
      requestBank_.add(task);
   }

   private void produceRequests() {
      int n = requestBank_.size();
      if (n > 1) {
         ImageTask firstRequest = requestBank_.getFirst();
         firstRequest.imageRequest_.startBurstN = n;
      }
      for (ImageTask task:requestBank_) {
         if (n > 1)
            task.imageRequest_.collectBurst = true;
         produce(task);
      }
      requestBank_.clear();
   }

   private boolean burstValid(EngineTask aTask, EngineTask nextTask) {
      if (aTask instanceof ImageTask && nextTask instanceof ImageTask) {
         ImageRequest aRequest = ((ImageTask) aTask).imageRequest_;
         ImageRequest nextRequest = ((ImageTask) nextTask).imageRequest_;

      
          if ((aRequest.exposure == nextRequest.exposure)
           && (aRequest.Position == nextRequest.Position)
           && (aRequest.SliceIndex  == nextRequest.SliceIndex)
           && (aRequest.ChannelIndex == nextRequest.ChannelIndex)
           && (nextRequest.WaitTime <= aRequest.exposure)
           && (nextRequest.AutoFocus == false))
             return true;
      }
      return false;
   }

}
