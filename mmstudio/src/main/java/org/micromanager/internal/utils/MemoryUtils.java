/* MemoryUtils.java
 * Created on Jun 8, 2007
 *
 * MicroManage
 */

package org.micromanager.internal.utils;

import ij.plugin.Memory;

/**
 * Memory related utility functions.
 */
public final class MemoryUtils {
   /**
    * Returns the currently allocated memory.
    *
    * @return estimate of the currently allocated memory in bytes
    */
   public static long currentMemory() {
      long freeMem = Runtime.getRuntime().freeMemory();
      long totMem = Runtime.getRuntime().totalMemory();
      return totMem - freeMem;
   }

   /**
    * Estimates the free memory available to this JVM.
    *
    * @return Estimate of free memory in bytes
    */
   public static long freeMemory() {
      long maxMemory = Runtime.getRuntime().maxMemory();
      long totalMemory = Runtime.getRuntime().totalMemory();
      long freeMemory = Runtime.getRuntime().freeMemory();

      return maxMemory - (totalMemory - freeMemory);
   }

   /**
    * Returns the maximum amount of memory available
    *
    * @return estimate of maximum amount of memory available in bytes
    */
   public static long maxMemory() {
      Memory mem = new Memory();
      long maxMemory = mem.getMemorySetting();
      if (maxMemory == 0L) {
         maxMemory = mem.maxMemory();
      }
      return maxMemory;
   }

}
