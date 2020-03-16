package org.micromanager.acqj.api;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.api.ExceptionCallback;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;

/**
 * Abstraction for an acquisition where all the settings are NOT specified in
 * advance, so that it can rely on user input or feedback from data to
 * dynamically generate events. Extends this class to implement different types
 * of acquistions
 *
 *
 * @author henrypinkard
 */
public abstract class DynamicSettingsAcquisition extends AcquisitionBase {

   private volatile boolean aborted_ = false;
   private ExecutorService submittedSequenceMonitorExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> {
      return new Thread(r, "Submitted sequence monitor");
   });

   public DynamicSettingsAcquisition(MinimalAcquisitionSettings settings, DataSink sink) {
      super(settings, sink);
   }

   public void start() {
      //nothing to do its alreay ready
   }

   /**
    * Submit a iterator of acquisition events for execution.
    *
    * @param iter an iterator of acquisition events
    * @param callback an ExceptionCallback for asynchronously handling
    * exceptions
    *
    */
   public void submitEventIterator(Iterator<AcquisitionEvent> iter, ExceptionCallback callback) {
      submittedSequenceMonitorExecutor_.submit(() -> {
         Future iteratorFuture = null;
         try {
            iteratorFuture = Engine.getInstance().submitEventIterator(iter, this);
            iteratorFuture.get();
         } catch (InterruptedException ex) {
            iteratorFuture.cancel(true);
         } catch (ExecutionException ex) {
            callback.run(ex);
         }
      });
   }

   /**
    * signal that no more streams can be submitted
    */
   public void complete() {
      acqFinishedFuture_ = Engine.getInstance().finishAcquisition(this);
      submittedSequenceMonitorExecutor_.shutdown();
   }

   @Override
   public synchronized void abort() {
      if (aborted_) {
         return;
      }
      aborted_ = true;
      if (this.isPaused()) {
         this.togglePaused();
      }
      submittedSequenceMonitorExecutor_.shutdownNow();
      acqFinishedFuture_ = Engine.getInstance().finishAcquisition(this);
   }

 
}
