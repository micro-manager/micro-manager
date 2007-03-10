package org.micromanager.navigation;

import java.util.ArrayList;

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

}
