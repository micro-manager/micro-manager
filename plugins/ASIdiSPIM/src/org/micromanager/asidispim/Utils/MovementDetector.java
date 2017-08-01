
package org.micromanager.asidispim.Utils;

import ij.IJ;
import javax.vecmath.Point3d;
import org.micromanager.acquisition.MMAcquisition;

/**
 *
 * @author Nico
 */
public class MovementDetector {
   
   public static Point3d detect(MMAcquisition acq) {
      Point3d movement = new Point3d();
      
      int lastFrame = acq.getLastAcquiredFrame();
      if (lastFrame < 2) {
         IJ.run("Z Project...", "projection=[Max Intensity]");
         return movement;
      }
      
      //IJ.run(command, options);
      
      //acq.getImageCache().getImage(lastFrame, lastFrame, lastFrame, lastFrame).
      
      return movement;
   }
}
