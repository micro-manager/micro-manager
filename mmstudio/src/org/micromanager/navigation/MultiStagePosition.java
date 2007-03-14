package org.micromanager.navigation;

import java.util.ArrayList;

import mmcorej.CMMCore;

public class MultiStagePosition {
   private ArrayList<StagePosition> stagePosList_;
   private String label_;
   
   public MultiStagePosition() {
      stagePosList_ = new ArrayList<StagePosition>();
      label_ = new String("Undefined");
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

}
