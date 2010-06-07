/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import mmcorej.CMMCore;
import mmcorej.Metadata;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionDisplayInRam extends AcquisitionDisplay {
   AcquisitionDisplayInRam(CMMCore core) {
      super(core);
   }

   
   public void run() {
      long t1 = System.currentTimeMillis();
      try {
         do {
            while (core_.getRemainingImageCount() > 0) {
               ++imgCount_;
               try {
                  Metadata mdCopy = new Metadata();
                  Object img = core_.popNextImageMD(0, 0, mdCopy);
                  displayImage(img, mdCopy);
                  //    ReportingUtils.logMessage("time=" + mdCopy.getFrameData("Frame") + ", position=" +
                  //            mdCopy.getPositionIndex() + ", channel=" + mdCopy.getChannelIndex() +
                  //            ", slice=" + mdCopy.getSliceIndex()
                  //            + ", remaining images =" + core_.getRemainingImageCount());
               } catch (Exception ex) {
                  ReportingUtils.logError(ex);
               }
            }
            Thread.sleep(30);
         } while (!core_.acquisitionIsFinished() || core_.getRemainingImageCount() > 0);
      } catch (Exception ex2) {
         ReportingUtils.logError(ex2);
      }

      long t2 = System.currentTimeMillis();
      ReportingUtils.logMessage(imgCount_ + " images in " + (t2 - t1) + " ms.");
   }

}
