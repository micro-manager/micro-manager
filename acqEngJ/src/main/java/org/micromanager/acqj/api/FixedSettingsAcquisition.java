package org.micromanager.acqj.api;

import java.util.Iterator;
import java.util.concurrent.Future;
import org.micromanager.acqj.api.Acquisition;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.internal.acqengj.Engine;

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
public abstract class FixedSettingsAcquisition extends Acquisition {

   private Future acqFuture_;
   private volatile boolean aborted_ = false;

   public FixedSettingsAcquisition(DataSink sink) {
      super(sink);
   }
   
    public FixedSettingsAcquisition() {}

   public void start() {
      super.start();
      if (finished_) {
         throw new RuntimeException("Cannot start acquistion since it has already been run");
      }
      Iterator<AcquisitionEvent> acqEventIterator = buildAcqEventGenerator();
      acqFuture_ = Engine.getInstance().submitEventIterator(acqEventIterator, this);
      //This event is how the acquisition will end, whether through aborting (which cancels everything undone in the previous event)
      //or through running its natural course
      acqFinishedFuture_ = Engine.getInstance().finishAcquisition(this);
   }

   protected abstract Iterator<AcquisitionEvent> buildAcqEventGenerator();

   public void abort() {
      abortRequested_ = true;
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
