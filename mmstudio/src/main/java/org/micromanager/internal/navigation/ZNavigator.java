package org.micromanager.internal.navigation;


import com.google.common.util.concurrent.AtomicDouble;
import org.micromanager.Studio;
import org.micromanager.events.internal.DefaultStagePositionChangedEvent;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ThreadFactoryFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ZNavigator {
   private final Studio studio_;
   private final Map<String, ZStageTask> zStageTaskMap_;

   public ZNavigator(Studio studio) {
      studio_ = studio;
      zStageTaskMap_ = new HashMap<>();
   }

   public void setPosition(String stage, double relativeMovement) {
      if (!zStageTaskMap_.containsKey(stage)) {
         zStageTaskMap_.put(stage, new ZStageTask(stage));
      }
      zStageTaskMap_.get(stage).setPosition(relativeMovement);
   }

   /**
    * Utility class sending movement commands to the stage(s)
    * Each stage will get its own instance.
    * Each instance runs its own Executor.
    * Movements can be send whether or not the stage is in motion
    * If the stage is in motion requested movements will be added to
    * previous requests (if present), and the combined movement will take
    * place once the stage finishes its previous movement.
    */
   private class ZStageTask implements Runnable {
      private final String stage_;
      private final AtomicDouble moveMemory_;
      private final ExecutorService executorService_;
      private Future<?> future_;

      public ZStageTask(String stage) {
         stage_ = stage;
         moveMemory_ = new AtomicDouble(0.0);
         executorService_ = Executors.newSingleThreadExecutor(
                 ThreadFactoryFactory.createThreadFactory("ZNavigator-" + stage ));
      }

      public void setPosition(double pos) {
         moveMemory_.addAndGet(pos);
         if (future_ == null || future_.isDone()) {
            future_ = executorService_.submit(this);
         }

      }

      @Override
      public void run() {
         // Move the stage
         try {
            // If moveMemory_ changes while we are moving, we will
            // execute the desired movement as well.
            while (moveMemory_.get() != 0.0) {
               double pos = moveMemory_.getAndSet(0.0);
               studio_.core().setRelativePosition(stage_, pos);
               studio_.core().waitForDevice(stage_);
               double z = studio_.core().getPosition(stage_);
               studio_.events().post(
                       new DefaultStagePositionChangedEvent(stage_, z));
            }
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
      }
   }


}
