package org.micromanager.positionlist;

class AxisData {
   public enum AxisType {oneD, twoD};

   private boolean use_;
   private String axisName_;
   private AxisType type_;
   
   public AxisData(boolean use, String axisName, AxisType type) {
      use_ = use;
      axisName_ = axisName;
      type_ = type;
   }
   public boolean getUse() {return use_;}
   public String getAxisName() {return axisName_;}
   public AxisType getType() {return type_;}
   
   public void setUse(boolean use) {use_ = use;}
}
