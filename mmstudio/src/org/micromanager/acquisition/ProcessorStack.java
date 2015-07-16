///////////////////////////////////////////////////////////////////////////////
//FILE:          ProcessorStack.java
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

package org.micromanager.acquisition;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import mmcorej.TaggedImage;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ReportingUtils;

/**
 * Sets up a queue of DataProcessors
 * The static method "run" will chain the given list of DataProcessors to 
 * inputqueue, and return an output queue.  The net result is that each 
 * DataProcessor will modify the image and pass it along to the next 
 * DataProcessor
 *
 * @author arthur
 */
public class ProcessorStack<E> {

   private final List<DataProcessor<E>> processors_;
   private final BlockingQueue<E> input_;
   private final BlockingQueue<E> output_;
   public ProcessorStack(BlockingQueue<E> input,
           List<DataProcessor<E>> processors) {
      processors_ = processors;
      input_ = input;

      BlockingQueue<E> left = input_;
      BlockingQueue<E> right = left;
      if (processors_ != null) {
         for (DataProcessor<E> processor : processors_) {
            if (processor.getIsEnabled()) {
               right = new LinkedBlockingQueue<E>(1);
               processor.setInput(left);
               processor.setOutput(right);
               left = right;
            }
         }
      }
      output_ = right;
   }

   public BlockingQueue<E> begin() {
      start();
      return output_;
   }

   public void start() {
      for (DataProcessor<E> processor : processors_) {
         if (!processor.isAlive()) {
            if (processor.isStarted()) {
               ReportingUtils.showError("Processor: " + processor.getName()
                       + " is no longer running. Remove and re-insert to get it to go again");
            } else {
               processor.start();
            }
         }
      }
   }

   private static Object processorInputOutputLock_ = new Object();

   /**
    * Sets up the DataProcessor<TaggedImage> sequence
    * @param inputTaggedImageQueue
    * @param imageProcessors
    * @return 
    */
   public static BlockingQueue<TaggedImage> run(
           BlockingQueue<TaggedImage> inputTaggedImageQueue, 
           List<DataProcessor<TaggedImage>> imageProcessors) {
      synchronized(processorInputOutputLock_) {
         ProcessorStack<TaggedImage> processorStack =
              new ProcessorStack<TaggedImage>(inputTaggedImageQueue, imageProcessors);
         return processorStack.begin();
      }
   }
   
}
