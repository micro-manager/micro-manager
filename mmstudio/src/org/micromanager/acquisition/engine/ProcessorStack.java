/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
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
           List<Class> processorClasses) {
      processors_ = new ArrayList<DataProcessor<E>>();
      for (Class processorClass:processorClasses) {
         try {
            processors_.add((DataProcessor<E>) processorClass.newInstance());
         } catch (InstantiationException ex) {
            ReportingUtils.logError(ex);
         } catch (IllegalAccessException ex) {
            ReportingUtils.logError(ex);
         }
      }
      input_ = input;

      BlockingQueue<E> left = input_;
      BlockingQueue<E> right = left;
      if (processors_ != null) {
         for (DataProcessor<E> processor:processors_) {
            right = new LinkedBlockingQueue<E>();
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
      for (DataProcessor<E> processor:processors_) {
        processor.start();
      }
   }

}
