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
public class AcquisitionDisplayFromDisk extends AcquisitionDisplay {
   AcquisitionVirtualStack virtualStack_;
   
   AcquisitionDisplayFromDisk(CMMCore core) {
      super(core);
   }

   public void run() {
      try {
         Metadata mdCopy = new Metadata();
         Metadata lastMD;
         int images;
         int lastImages = -1;
         while (true) {
            images = core_.getRemainingImageCount();
            if (lastImages == -1 || (images - lastImages) == 0) {
               lastImages = images;
               Thread.sleep(30);
            } else {
               break;
            }
         }

         do  {
            lastMD = mdCopy;
            Object img = core_.getLastImageMD(0, 0, mdCopy);
            if (sameFrame(lastMD, mdCopy)) {
               Thread.sleep(30);
            } else {
               super.displayImage(img, mdCopy);
            }
         } while (!core_.acquisitionIsFinished());
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         ex.printStackTrace();
      }

   }
}
