package org.micromanager.navigation;

import java.util.ArrayList;

import mmcorej.CMMCore;

public class MultiStagePosition {
   private ArrayList<StagePosition> stagePosList_;
   private String label_;
   private String defaultZStage_;
   private String defaultXYStage_;
   
   public MultiStagePosition() {
      stagePosList_ = new ArrayList<StagePosition>();
      label_ = new String("Undefined");
      defaultZStage_ = new String("");
      defaultXYStage_ = new String("");
   }
   
   public void add(StagePosition sp) {
      stagePosList_.add(sp);
   }
   
   public int size() {
      return stagePosList_.size();
   }
   
   public StagePosition get(int idx) {
      return stagePosList_.get(idx);
   }
   
   public String getLabel() {
      return label_;
   }
   
   public void setLabel(String lab) {
      label_ = lab;
   }
   
   public void setDefaultZStage(String stage) {
      defaultZStage_ = stage;
   }
   
   public String getDefaultZStage() {
      return defaultZStage_;
   }
   
   public String getDefaultXYStage() {
      return defaultXYStage_;
   }
   
   public void setDefaultXYStage(String stage) {
      defaultXYStage_ = stage;
   }

   /**
    * Moves all stages to the specified positions.
    * @param core_ - microscope API
    * @throws Exception
    */
   public static void goToPosition(MultiStagePosition msp, CMMCore core_) throws Exception {
      for (int i=0; i<msp.size(); i++) {
         StagePosition sp = msp.get(i);
         if (sp.numAxes == 1) {
            core_.setPosition(sp.stageName, sp.x);
         } else if (sp.numAxes == 2) {
            core_.setXYPosition(sp.stageName, sp.x, sp.y);
         }
      }
      
   }
   // TODO: implement more efficient position calculation
   public double getX() {
      for (StagePosition stage : stagePosList_) {
         if (stage.numAxes == 2 && stage.stageName.compareTo(defaultXYStage_) == 0) {
            return stage.x;
         }
      }
      return 0.0;
   }
   
   public double getY() {
      for (StagePosition stage : stagePosList_) {
         if (stage.numAxes == 2 && stage.stageName.compareTo(defaultXYStage_) == 0) {
            return stage.y;
         }
      }
      return 0.0;
   }
   
   public double getZ() {
      for (StagePosition stage : stagePosList_) {
         if (stage.numAxes == 1 && stage.stageName.compareTo(defaultZStage_) == 0) {
            return stage.x;
         }
      }
      return 0.0;
   }

}
