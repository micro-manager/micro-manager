/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 *
 * @author arthur and henry
 */
public class GUIUpdater {

   AtomicReference<Runnable> latestTask = new AtomicReference<Runnable>();

   /*
    * Post a task for running on the EDT thread. If multiple
    * tasks pile up, only the most recent will run.
    */
   public void post(Runnable task) {
      Runnable oldTask = latestTask.getAndSet(task);
      if (oldTask == null) {
         SwingUtilities.invokeLater(new Runnable() {
            public void run() {
               final Runnable taskToRun = latestTask.getAndSet(null);
               taskToRun.run();
            }
         });
      }
   }
}
