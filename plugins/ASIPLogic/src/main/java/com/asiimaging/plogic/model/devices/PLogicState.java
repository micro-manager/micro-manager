package com.asiimaging.plogic.model.devices;

import com.asiimaging.plogic.PLogicControlModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * An internal representation of the current PLogic device state.
 */
public class PLogicState {

   public static final int NUM_IO_CELLS = 16;
   public static final int IO_START_ADDR = 33;
   public static final int IO_MAX_ADDR = IO_START_ADDR + NUM_IO_CELLS - 1;

   private final int numCells_;
   private ASIPLogic.TriggerSource triggerSource_;

   private final PLogicCell[] cells_;
   private final PLogicIO[] io_;

   public PLogicState(final int numCells) {
      numCells_ = numCells;
      triggerSource_ = ASIPLogic.TriggerSource.INTERNAL;
      // init logic cells
      cells_ = new PLogicCell[numCells_];
      for (int i = 0; i < numCells_; i++) {
         cells_[i] = new PLogicCell(i);
      }
      // init I/O cells
      io_ = new PLogicIO[NUM_IO_CELLS];
      for (int i = 0; i < NUM_IO_CELLS; i++) {
         io_[i] = new PLogicIO(i);
      }
   }

   public int numCells() {
      return numCells_;
   }

   public void triggerSource(final ASIPLogic.TriggerSource triggerSource) {
      triggerSource_ = triggerSource;
   }

   public ASIPLogic.TriggerSource triggerSource() {
      return triggerSource_;
   }

   public PLogicCell cell(final int cellNum) {
      if (cellNum < 1 || cellNum > numCells_) {
         throw new IllegalArgumentException(
               "Logic Cell pointer positions are 1-" + numCells_ + ".\n"
                     + "The input was " + cellNum + ".");
      }
      return cells_[cellNum - 1]; // remap 1-16 to 0-15
   }

   public PLogicIO io(final int cellNum) {
      if (cellNum < IO_START_ADDR || cellNum > IO_MAX_ADDR) {
         throw new IllegalArgumentException(
               "Physical I/O pointer positions are 33-" + IO_MAX_ADDR + ".\n"
                     + "The input was " + cellNum + ".");
      }
      return io_[cellNum - IO_START_ADDR]; // remap 33-48 to 0-15
   }

   /**
    * Update the {@code PLogicState} by querying the controller.
    *
    * @param model the {@code PLogicControlModel} model
    */
   public void updateCells(final PLogicControlModel model) {
      final ASIPLogic plc = model.plc();
      triggerSource_ = plc.triggerSource();
      for (int i = 0; i < plc.numCells(); i++) {
         if (!model.isUpdating()) {
            return; // early exit => stop sending serial commands
         }
         plc.pointerPosition(i + 1);
         cells_[i].type(plc.cellType());
         cells_[i].config(plc.cellConfig());
         for (int input = 1; input <= 4; input++) {
            cells_[i].input(input, plc.cellInput(input));
         }
      }
      for (int i = 0; i < NUM_IO_CELLS; i++) {
         if (!model.isUpdating()) {
            return; // early exit => stop sending serial commands
         }
         plc.pointerPosition(io_[i].address());
         io_[i].type(plc.ioType());
         io_[i].sourceAddress(plc.sourceAddress());
      }
   }

   /**
    * Update the controller by sending the {@code PLogicState} as serial commands.
    *
    * @param model the {@code PLogicControlModel} model
    */
   public void updateDevice(final PLogicControlModel model) {
      final ASIPLogic plc = model.plc();
      plc.triggerSource(triggerSource_);
      for (int i = 0; i < plc.numCells(); i++) {
         if (!model.isUpdating()) {
            return; // early exit => stop sending serial commands
         }
         plc.pointerPosition(i + 1);
         plc.cellType(cells_[i].type());
         plc.cellConfig(cells_[i].config());
         for (int input = 1; input <= 4; input++) {
            plc.cellInput(input, cells_[i].input(input));
         }
      }
      for (int i = 0; i < NUM_IO_CELLS; i++) {
         if (!model.isUpdating()) {
            return; // early exit => stop sending serial commands
         }
         plc.pointerPosition(io_[i].address());
         plc.ioType(io_[i].type());
         plc.sourceAddress(io_[i].sourceAddress());
      }
   }

   /**
    * Clear the state, set all cells to constant type,
    * set config to zero, and set all inputs to 0.
    */
   public void clearLogicCells() {
      for (int i = 0; i < numCells_; i++) {
         cells_[i].type(ASIPLogic.CellType.CONSTANT);
         cells_[i].config(0);
         for (int input = 1; input <= 4; input++) {
            cells_[i].input(input, 0);
         }
      }
   }

   public String toJson() {
      return new Gson().toJson(this);
   }

   public String toPrettyJson() {
      final Gson gson = new GsonBuilder().setPrettyPrinting().create();
      return gson.toJson(this);
   }

   public static PLogicState fromJson(final String json) {
      return new Gson().fromJson(json, PLogicState.class);
   }

}
