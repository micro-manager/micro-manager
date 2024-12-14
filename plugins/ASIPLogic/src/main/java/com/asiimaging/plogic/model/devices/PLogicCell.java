package com.asiimaging.plogic.model.devices;

public class PLogicCell {

   private final int address_;
   private ASIPLogic.CellType type_;
   private int config_;
   private final int[] inputs_;

   public PLogicCell(final int num) {
      address_ = num + 1;
      type_ = ASIPLogic.CellType.CONSTANT;
      config_ = 0;
      inputs_ = new int[4];
   }

   public int address() {
      return address_;
   }

   public ASIPLogic.CellType type() {
      return type_;
   }

   public void typeDirect(ASIPLogic.CellType type) {
      type_ = type;
   }

   // Note: clear state when switching cell types
   public void type(ASIPLogic.CellType type) {
      type_ = type;
      config_ = 0;
      inputs_[0] = type.isEdgeSensitive(1) ? 128 : 0;
      inputs_[1] = type.isEdgeSensitive(2) ? 128 : 0;
      inputs_[2] = type.isEdgeSensitive(3) ? 128 : 0;
      inputs_[3] = type.isEdgeSensitive(4) ? 128 : 0;
   }

   public int config() {
      return config_;
   }

   public void config(final int value) {
      config_ = value;
   }

   public void input(final int input, int value) {
      if (input < 1 || input > 4) {
         throw new IllegalArgumentException("Each cell only has inputs 1-4.");
      }
      inputs_[input - 1] = value;
   }

   public int input(final int input) {
      if (input < 1 || input > 4) {
         throw new IllegalArgumentException("Each cell only has inputs 1-4.");
      }
      return inputs_[input - 1];
   }
}
