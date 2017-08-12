
package org.micromanager.asidispim.Utils;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.json.JSONException;
import org.micromanager.acquisition.MMAcquisition;
import org.micromanager.api.MMTags;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;

/**
 *
 * @author nico
 */
public class MovementDetector {

   public enum Method { CenterOfMass, PhaseCorrelation };
   
   private Vector3D lastPosition_;
   private final Vector3D zeroPoint_;
   private Vector3D lastMovement_;
   private final Prefs prefs_;
   private final MMAcquisition acq_;
   private final int ch_;
   private final int pos_;
   private final Rotation rotation_;
   private final double pixelSize_;
   private final double stepSize_;
   private final int runEachNTimePoints_;
   private final float maxDistance_;
   private final float minDistance_;
   
   /**
    * This class determines the position of the object in a stack and
    * returns the difference in XYZ position of that object with the 
    * previous time point (or a zeroed 3D point if this is the first timepoint).
    * @param prefs diSPIM preferences used to transmit user settings
    * @param acq MMAcquisition for which we track movement
    * @param ch  Channel to be used to determine position of the object
    * @param pos Position in this acquisition that we track
    * @param rotation Rotation between this channel and the stages of the diSPIM
    */
   public MovementDetector (
           final Prefs prefs,
           final MMAcquisition acq, 
           final int ch, 
           final int pos,
           final Rotation rotation) {
      prefs_ = prefs;
      acq_ = acq;
      ch_ = ch;
      pos_ = pos;
      rotation_ = rotation;
      zeroPoint_ = new Vector3D(0.0, 0.0, 0.0);
      lastMovement_ = new Vector3D(0.0, 0.0, 0.0);
      double pixelSize = 0.165;
      double stepSize = 1.5;
      try {
         pixelSize = acq_.getSummaryMetadata().getDouble(MMTags.Summary.PIXSIZE);
         stepSize = acq_.getSummaryMetadata().getDouble("z-step_um");
      } catch (JSONException notHandled) {
         System.out.println("PIXELSIZE OR STEPSIZE NOT FOUND IN ACQUISITION DATA");
      } finally {
         pixelSize_ = pixelSize;
         stepSize_ = stepSize;
      }
      runEachNTimePoints_ = prefs_.getInt(MyStrings.PanelNames.AUTOFOCUS.toString(), 
              Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_EACHNIMAGES, 1);
      maxDistance_ = prefs_.getFloat(MyStrings.PanelNames.AUTOFOCUS.toString(),
              Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_MAXCHANGE, 25.0f);
       minDistance_ = prefs_.getFloat(MyStrings.PanelNames.AUTOFOCUS.toString(),
              Properties.Keys.PLUGIN_AUTOFOCUS_CORRECTMOVEMENT_MINCHANGE, 0.0f);
   }
   
   /**
    * Determines sample position (using indicated method) 
    * and returns the difference with the position in the previous frame
    * (or the 0,0,0 vector for the first time point).
    * May remember the position of the object so that it can be used next.
    * It is therefore important to call this function consecutively for
    * each time point.
    * However, this will result in the stages moving back and forth between
    * 2 positions.  To avoid this, the last stage position is corrected by 
    * the movement (that was presumably executed by the stages
    * @param method
    * @return Movement (in microns) since the last time-point (or a zeroed
    *          vector if the movement is more than maxDistance).
    */
   public Vector3D detectMovement(Method method) {
      Vector3D movement = new Vector3D(0.0, 0.0, 0.0);
  
      if (method == Method.CenterOfMass) {
         int lastFrame = acq_.getLastAcquiredFrame();
         // TODO: convert from pixels to microns!
         Vector3D position = SamplePositionDetector.getCenter(acq_, ch_, pos_);

         if (lastFrame > 0 && lastPosition_ != null) {
            movement = position.subtract(lastPosition_);
            if (movement.distance(zeroPoint_) > maxDistance_) {
               movement = new Vector3D(0.0, 0.0, 0.0);
            }
         }

         lastPosition_ = position.add(movement);
      } else if (method == Method.PhaseCorrelation) {
         int currentFrame = acq_.getLastAcquiredFrame();
         movement = SamplePositionDetector.
                 getDisplacementUsingIJPhaseCorrelation(
                         acq_, currentFrame, currentFrame - runEachNTimePoints_, 
                         ch_, pos_, pixelSize_, stepSize_);
         
         // convert from camera space to stage space:
         movement = rotation_.applyTo(movement);
         
         // check for min and max distance traveled
         if (movement.distance(movement) > maxDistance_) {
            movement = zeroPoint_;
         }
         if (movement.getX() < minDistance_) {
            movement = new Vector3D(0.0, movement.getY(), movement.getZ());
         }
         if (movement.getY() < minDistance_) {
            movement = new Vector3D(movement.getX(), 0.0, movement.getZ());
         }         
         if (movement.getZ() < minDistance_) {
            movement = new Vector3D(movement.getX(), movement.getY(), 0.0);
         }
        
         // correct for the amount we moved last time to avoid oscillations
         // I did not think through why we need an add, but it works.
         movement = movement.add(lastMovement_);
         
         // remember what we asked the stages to move for next time
         lastMovement_ = movement;
      }
      return movement;
   }
   
}
