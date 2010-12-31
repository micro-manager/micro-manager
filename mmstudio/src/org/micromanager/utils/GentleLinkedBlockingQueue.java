/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.utils;

import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author arthur
 */
public class GentleLinkedBlockingQueue<E> extends LinkedBlockingQueue<E> {

   public GentleLinkedBlockingQueue() {
      super();
   }

   @Override
   public void put(E e) throws InterruptedException {
      final int n = 1000 / 5; // Timeout after 1 second.
      final long limitBytes = 20000000;
      for (int i = 0;
           (i<n) && (JavaUtils.getAvailableUnusedMemory() < limitBytes);
           ++i) {
         JavaUtils.sleep(5);
      }
      super.put(e);
   }

}
