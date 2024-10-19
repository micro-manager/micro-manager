package com.asiimaging.plogic.model.devices;

public class PLogicIO {

   private final int address_;
   private ASIPLogic.IOType type_;
   private int sourceAddr_;

   public PLogicIO(final int num) {
      if (num < 8) {
         type_ = ASIPLogic.IOType.OUTPUT_PUSH_PULL;
      } else {
         type_ = ASIPLogic.IOType.INPUT;
      }
      address_ = num + 32 + 1; // BNC1 starts at address 33
      sourceAddr_ = 0;
   }

   public int address() {
      return address_;
   }

   public ASIPLogic.IOType type() {
      return type_;
   }

   public void type(ASIPLogic.IOType type) {
      type_ = type;
   }

   public int sourceAddress() {
      return sourceAddr_;
   }

   public void sourceAddress(final int addr) {
      sourceAddr_ = addr;
   }

}
