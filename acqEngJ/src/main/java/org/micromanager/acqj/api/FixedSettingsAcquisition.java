package org.micromanager.acqj.api;

import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;

/**
 * Abstraction for an acquisition where all the settings are specified in
 * advance, such that the AcqusitionEvents can be specified at the beginning of
 * the acquisition (e.g. a fixed set of channels, number of frames, and
 * z-stacks). This is in contrast to an acquisition that relies on user input or
 * feedback from data to dynamically generate events
 *
 * Subclasses only need to implement an acquisition-specific way of generating
 * events
 *
 * @author henrypinkard
 */
public abstract class FixedSettingsAcquisition extends AcquisitionBase {

   private Future acqFuture_;
   private volatile boolean aborted_ = false;

   public FixedSettingsAcquisition(MinimalAcquisitionSettings settings, DataSink sink) {
      super(settings, sink);
   }

   public void start() {
      if (finished_) {
         throw new RuntimeException("Cannot start acquistion since it has already been run. "
                 + " Try refreshing acquisition list or creating new acquisition");
      }
      Iterator<AcquisitionEvent> acqEventIterator = buildAcqEventGenerator();
      acqFuture_ = Engine.getInstance().submitEventIterator(acqEventIterator, this);
      //This event is how the acquisition will end, whether through aborting (which cancels everything undone in the previous event)
      //or through running its natural course
      acqFinishedFuture_ = Engine.getInstance().finishAcquisition(this);
   }

   protected abstract Iterator<AcquisitionEvent> buildAcqEventGenerator();

   @Override
   public synchronized void abort() {
      if (aborted_) {
         return;
      }
      aborted_ = true;
      if (this.isPaused()) {
         this.togglePaused();
      }
      if (acqFuture_ != null) {
         acqFuture_.cancel(true);
      }
   }

   @Override
   public void close() {
      if (acqFuture_ == null) {
         //it was never successfully started
         return;
      }
      super.close();
   }

}
