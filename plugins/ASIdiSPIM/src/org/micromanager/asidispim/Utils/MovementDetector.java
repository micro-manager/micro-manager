
package org.micromanager.asidispim.Utils;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.micromanager.acquisition.MMAcquisition;

/**
 *
 * @author nico
 */
public class MovementDetector {
      
   private Vector3D lastPosition_;
   private final Vector3D zeroPoint_;
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
      acq_ = acq;
      ch_ = ch;
      pos_ = pos;
      zeroPoint_ = new Vector3D(0.0, 0.0, 0.0);
   }
   
   /**
    * Determines sample position (currently using Center of Mass) 
    * and returns the difference with the previously measured position 
    * (or the 0,0,0 vector for the first time point).
    * Remembers the position of the object so that it can be used next.
    * It is therefore important to call this function consecutively for
    * each time point.
    * However, this will result in the stages moving back and forth between
    * 2 positions.  To avoid this, the last stage position is corrected by 
    * the movement (that was presumably executed by the stages
    * @param maxDistance maximum distance (in pixel coordinates) that the image
    *                      is allowed to move.  If it moves more than this, 
    *                      the zero vector will be returned.
    * @return Movement since the last time-point in pixel coordinates (or a zeroed
    *          vector if the movement is more than maxDistance).
    */
   public Vector3D detectMovement(double maxDistance) {
      Vector3D movement = new Vector3D(0.0, 0.0, 0.0);
      
      int lastFrame = acq_.getLastAcquiredFrame();
      Vector3D position = SamplePositionDetector.getCenter(acq_, ch_, pos_);
            
      if (lastFrame > 0 && lastPosition_ != null) {
         movement = position.subtract(lastPosition_);
         if (movement.distance(zeroPoint_) > maxDistance) {
            movement = new Vector3D(0.0, 0.0, 0.0);
         }
      }
      
      lastPosition_ = position.add(movement);
     
      return movement;
   }
   
}
