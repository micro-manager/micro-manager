package org.micromanager.internal.jacque;

/**
 * Mock core for testing without device adapters.
 * All sequencing queries return false/zero (no burst capability).
 * Subclass and override methods to simulate sequenceable devices.
 */
public class HelperMockCore implements CoreOps {
   @Override
   public boolean isPropertySequenceable(String device, String property)
         throws Exception {
      return false;
   }

   @Override
   public int getPropertySequenceMaxLength(String device, String property)
         throws Exception {
      return 0;
   }

   @Override
   public String getFocusDevice() {
      return "";
   }

   @Override
   public boolean isStageSequenceable(String device) throws Exception {
      return false;
   }

   @Override
   public int getStageSequenceMaxLength(String device) throws Exception {
      return 0;
   }

   // Called by Clojure's send-to-debug-log (mm.clj)
   public void logMessage(String msg, boolean debugOnly) {
   }
}
