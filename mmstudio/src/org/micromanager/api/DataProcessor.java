///////////////////////////////////////////////////////////////////////////////
//FILE:          DataProcessor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
// COPYRIGHT:    University of California, San Francisco, 2010
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager.api;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.micromanager.utils.ReportingUtils;

/**
 * A DataProcessor thread allows for on-the-fly modification of image
 * data during acquisition.
 *
 * Inherit from this class and use the AcquisitionEngine functions
 * addImageProcessor and removeImageProcessor to insert your code into the
 * acquisition pipeline
 *
 */
public abstract class DataProcessor<E> extends Thread {
   private BlockingQueue<E> input_;
   private BlockingQueue<E> output_;
   private boolean stopRequested_ = false;
   private boolean started_ = false;

   /*
    * The process method should be overridden by classes implementing
    * DataProcessor, to provide a processing function.
    * 
    * For example, an "Identity" DataProcessor (where nothing is
    * done to the data) would override process() thus:
    *
    * @Override
    * public void process() {
    *    produce(poll());
    * }
    * TaggedImageQueue.POISON will be the last object
    * received by polling -- the process method should pass this
    * object on unchanged.
    */
   protected abstract void process();
 

   /*
    * The run method that causes images to be processed. As DataProcessor
    * extends java's Thread class, this method will be executed whenever
    * DataProcessor.start() is called.
    */
   @Override
   public void run() {
      setStarted(true);
      while (!stopRequested_) {
         process();
      }
   }

   /*
    * Request that the data processor stop processing. The current
    * processing event will continue, but no others will be started.
    */
   public synchronized void requestStop() {
      stopRequested_ = true;
   }

   /*
    * Private method for tracking when processing has started.
    */
   private synchronized void setStarted(boolean started) {
      started_ = started;
   }

   /*
    * Returns true if the DataProcessor has started up and objects
    * are being processed as they arrive.
    */
   public synchronized boolean isStarted() {
      return started_;
   }

   /*
    * The constructor.
    */
   public DataProcessor() {}

   /*
    * Sets the input queue where objects to be processed
    * are received by the DataProcessor.
    */
   public void setInput(BlockingQueue<E> input) {
      input_ = input;
   }

   /*
    * Sets the output queue where objects that have been processed
    * exit the DataProcessor.
    */
   public void setOutput(BlockingQueue<E> output) {
      output_ = output;
   }

   /*
    * A protected method that reads the next object from the input
    * queue.
    */
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

   /*
    * A convenience method for draining all available data objects
    * on the input queue to a collection.
    */
   protected void drainTo(Collection<E> data) {
      input_.drainTo(data);
   }

   /*
    * A convenience method for posting a data object to the output queue.
    */
   protected void produce(E datum) {
      try {
         output_.put(datum);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }
   };

   /*
    * Returns true if stop has been requested.
    */
   protected synchronized boolean stopRequested() {
      return stopRequested_;
   }

}
