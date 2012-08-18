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

   final AtomicReference<Runnable> latestTask = new AtomicReference<Runnable>();

   /*
    * Post a task for running on the EDT thread. If multiple
    * tasks pile up, only the most recent will run.
    */
   public void post(Runnable task) {
      if (latestTask.getAndSet(task) == null) {
         SwingUtilities.invokeLater(new Runnable() {
            public void run() {
               latestTask.getAndSet(null).run();
            }
         });
      }
   }
}
