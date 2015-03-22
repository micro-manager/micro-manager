/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import coordinates.XYStagePosition;
import java.util.List;
import propsandcovariants.CovariantPairing;

/**
 * Information about the acquisition of a single image
 */
public class AcquisitionEvent  {

   enum SpecialFlag {TimepointFinished, AcqusitionFinished, SwappingQueues, EngineTaskFinished, AutofocusAdjustment};
   
   final public Acquisition acquisition_;
   final public int timeIndex_, sliceIndex_, channelIndex_, positionIndex_;
   final public double zPosition_;
   final public XYStagePosition xyPosition_;
   private SpecialFlag specialFlag_;
   final public List<CovariantPairing> covariants_;
   public String autofocusZName_;
   public double autofocusPosition_;
   
   public AcquisitionEvent(Acquisition acq, int frameIndex, int channelIndex, int sliceIndex, int positionIndex, 
           double zPos, XYStagePosition xyPos, List<CovariantPairing> covariants) {
      timeIndex_ = frameIndex;
      sliceIndex_ = sliceIndex;
      channelIndex_ = channelIndex;
      positionIndex_ = positionIndex;    
      zPosition_ = zPos;
      acquisition_ = acq;
      xyPosition_ = xyPos;
      covariants_ = covariants;
   }
   
   public static AcquisitionEvent createAutofocusEvent(String zName, double pos ) {   
      AcquisitionEvent evt = new AcquisitionEvent(null, 0, 0, 0, 0, 0, null, null);
      evt.autofocusZName_ = zName;
      evt.autofocusPosition_ = pos;
      evt.specialFlag_ = SpecialFlag.AutofocusAdjustment;
      return evt;
   }
   
    public boolean isAutofocusAdjustmentEvent() {
      return specialFlag_ == SpecialFlag.AutofocusAdjustment;
   }
   
   public static AcquisitionEvent createEngineTaskFinishedEvent() {
      AcquisitionEvent evt = new AcquisitionEvent(null, 0, 0, 0, 0, 0, null, null);
      evt.specialFlag_ = SpecialFlag.EngineTaskFinished;
      return evt;
   }
   
   public boolean isEngineTaskFinishedEvent() {
      return specialFlag_ == SpecialFlag.EngineTaskFinished;
   }
   
   public static AcquisitionEvent createTimepointFinishedEvent(Acquisition acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq, 0, 0, 0, 0, 0, null, null);
      evt.specialFlag_ = SpecialFlag.TimepointFinished;
      return evt;
   }
   
   public boolean isTimepointFinishedEvent() {
      return specialFlag_ == SpecialFlag.TimepointFinished;
   }
   
   public static AcquisitionEvent createReQuerieEventQueueEvent() {
      AcquisitionEvent evt = new AcquisitionEvent(null, 0, 0, 0, 0, 0, null, null);
      evt.specialFlag_ = SpecialFlag.SwappingQueues;
      return evt;
   }
   
   public boolean isReQueryEvent() {
      return specialFlag_ == SpecialFlag.SwappingQueues;
   }
   
   public static AcquisitionEvent createAcquisitionFinishedEvent(Acquisition acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq, 0, 0, 0, 0, 0, null, null);
      evt.specialFlag_ = SpecialFlag.AcqusitionFinished;
      return evt;
   }
   
   public boolean isAcquisitionFinishedEvent() {
      return specialFlag_ == SpecialFlag.AcqusitionFinished;
   }
   
   @Override
   public String toString() {
      return "P: " + positionIndex_ + "\t\tT: " + timeIndex_ + "\t\tZ: " + sliceIndex_ + "\t\tC: " + channelIndex_; 
   }
}
