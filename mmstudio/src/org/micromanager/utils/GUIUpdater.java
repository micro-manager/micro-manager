/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/**
 *
 * @author arthur
 */
public class GUIUpdater {
   AtomicBoolean readyForNewPost = new AtomicBoolean();
   AtomicReference<Runnable> latestTask = new AtomicReference<Runnable>();

   public GUIUpdater() {
      readyForNewPost.set(true);
   }

   /*
    * Post a task for running on the EDT thread. If multiple
    * tasks pile up, only the most recent will run.
    */
   public void post(Runnable task) {
      latestTask.set(task);
      if (readyForNewPost.getAndSet(false)) {
         SwingUtilities.invokeLater(new Runnable() {
            public void run() {
               readyForNewPost.set(true);
               final Runnable task = latestTask.getAndSet(null);
               if (task != null) {
                  task.run();
               } else {
                  System.err.println("Null task in GUIUpdater!");
               }
            }
         });
      }
   }
}
