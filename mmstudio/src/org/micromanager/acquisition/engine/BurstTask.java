/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import mmcorej.CMMCore;
import org.micromanager.api.EngineTask;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
class BurstTask implements EngineTask {
   private final ImageRequest imageRequest_;
   private Engine eng_;
   private CMMCore core_;

   public BurstTask(ImageRequest request) {
      imageRequest_ = request;
   }

   public void requestStop() {
      // Do nothing.
   }

   public void requestPause() {
      // Do nothing.
   }

   public void requestResume() {
      // Do nothing.
   }

   public void run(Engine eng) {
      eng_ = eng;
      core_ = eng_.core_;

      try {
            if (imageRequest_.startBurstN > 0) {
               if (eng_.autoShutterSelected_)
                  core_.setAutoShutter(true);
               core_.startSequenceAcquisition(imageRequest_.startBurstN,
                       0, false);
               ReportingUtils.logMessage("started a burst with " + imageRequest_.startBurstN + " images.");
            }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

}
