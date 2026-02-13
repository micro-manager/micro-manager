package org.micromanager.internal.jacque;

import mmcorej.CMMCore;

public interface CoreOps {
   boolean isPropertySequenceable(String device, String property)
         throws Exception;

   int getPropertySequenceMaxLength(String device, String property)
         throws Exception;

   String getFocusDevice();

   boolean isStageSequenceable(String device) throws Exception;

   int getStageSequenceMaxLength(String device) throws Exception;

   static CoreOps fromCMMCore(CMMCore mmc) {
      return new CoreOps() {
         @Override
         public boolean isPropertySequenceable(String device,
               String property) throws Exception {
            return mmc.isPropertySequenceable(device, property);
         }

         @Override
         public int getPropertySequenceMaxLength(String device,
               String property) throws Exception {
            return mmc.getPropertySequenceMaxLength(device, property);
         }

         @Override
         public String getFocusDevice() {
            return mmc.getFocusDevice();
         }

         @Override
         public boolean isStageSequenceable(String device)
               throws Exception {
            return mmc.isStageSequenceable(device);
         }

         @Override
         public int getStageSequenceMaxLength(String device)
               throws Exception {
            return mmc.getStageSequenceMaxLength(device);
         }
      };
   }
}
