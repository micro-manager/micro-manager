/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.micromanager.api.DataProcessor;

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
      if (processors != null) {
         for (DataProcessor<E> processor:processors) {
            right = new LinkedBlockingQueue<E>();
            processor.setInput(left);
            processor.setOutput(right);
            left = right;
         }
      }
      output_ = right;
   }

   public BlockingQueue<E> getOutputChannel() {
      return output_;
   }

   public void start() {
      for (DataProcessor<E> processor:processors_) {
         processor.start();
      }
   }
}
