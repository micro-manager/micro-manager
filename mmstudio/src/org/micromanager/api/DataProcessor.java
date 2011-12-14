/**
 * DataProcessor thread allows for on-the-fly modification of image
 * data during acquisition.  
 * 
 * Inherit from this class and use the AcquisitionEngine functions 
 * addImageProcessor and removeImageProcessor to insert your code into the
 * acquisition pipeline
 * 
 */

package org.micromanager.api;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public abstract class DataProcessor<E> extends Thread {
   private BlockingQueue<E> input_;
   private BlockingQueue<E> output_;
   private boolean stopRequested_ = false;
   private boolean started_ = false;

   protected abstract void process();
   /*    The "Identity" process method:
    * {
    *    produce(poll());
    * }
    */

   @Override
   public void run() {
      setStarted(true);
      while (!stopRequested_) {
         process();
      }
   }

   public synchronized void requestStop() {
      stopRequested_ = true;
   }
   
   private synchronized void setStarted(boolean started) {
      started_ = started;
   }
   
   public synchronized boolean isStarted() {
      return started_;
   }

   public DataProcessor() {}

   public void setInput(BlockingQueue<E> input) {
      input_ = input;
   }

   public void setOutput(BlockingQueue<E> output) {
      output_ = output;
   }

   protected E poll() {
      while (!stopRequested()) {
         try {
            E datum = (E) input_.poll(100, TimeUnit.MILLISECONDS);
            if (datum != null) {
               return datum;
            }
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         }
      }
      return null;
   }

   protected void drainTo(Collection<E> data) {
      input_.drainTo(data);
   }

   protected void produce(E datum) {
      try {
         output_.put(datum);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
   };

   protected synchronized boolean stopRequested() {
      return stopRequested_;
   }

}
