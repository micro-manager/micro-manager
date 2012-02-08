/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ReportingUtils;

/**
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
         for (DataProcessor<E> processor:processors_) {
            right = new LinkedBlockingQueue<E>(1);
            processor.setInput(left);
            processor.setOutput(right);
            left = right;
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

}
