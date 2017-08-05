
package org.micromanager.asidispim.Utils;

import javax.vecmath.Point3d;
import org.micromanager.acquisition.MMAcquisition;

/**
 *
 * @author nico
 */
public class MovementDetector {
      
   private  Point3d lastPosition_;
   private final MMAcquisition acq_;
   private final int ch_;
   private final int pos_;
   
   /**
    * This class determines the position of the object in a stack and
    * returns the difference in XYZ position of that object with the 
    * previous time point (or a zeroed 3D point if this is the first timepoint).
    * @param acq MMAcquisition for which we track movement
    * @param ch  Channel to be used to determine position of the object
    * @param pos Position in this acquisition that we track
    */
   public MovementDetector (
           final MMAcquisition acq, 
           final int ch, 
           final int pos) {
      lastPosition_ = new Point3d();
      acq_ = acq;
      ch_ = ch;
      pos_ = pos;
   }
   
   /**
    * Determines sample position (currently using Center of Mass) 
    * and returns the difference with the previously measured position 
    * (or the 0,0,0 vector for the first time point).
    * Remembers the position of the object so that it can be used next.
    * It is therefore important to call this function consecutively for
    * each time point.
    * @return Movement since the last time point in pixel coordinates
    */
   public Point3d detectMovement() {
      Point3d movement = new Point3d();
      
      int lastFrame = acq_.getLastAcquiredFrame();
      Point3d position = SamplePositionDetector.getCenter(acq_, ch_, pos_);
            
      if (lastFrame > 0) {
         movement = new Point3d(position);
         movement.sub(lastPosition_);
      }
      
      lastPosition_ = position;
     
      return movement;
   }
   
}
