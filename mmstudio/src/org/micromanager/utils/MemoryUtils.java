/* MemoryUtils.java
 * Created on Jun 8, 2007
 *
 * MicroManage
 */
package org.micromanager.utils;

import ij.plugin.Memory;

public class MemoryUtils {
   /**
    * Returns the currently allocated memory.
    */
   public static long currentMemory() {
      long freeMem = Runtime.getRuntime().freeMemory();
      long totMem = Runtime.getRuntime().totalMemory();
      return totMem-freeMem;
   }
   
   public static long freeMemory() {
      long maxMemory = Runtime.getRuntime().maxMemory();
      long totalMemory = Runtime.getRuntime().totalMemory();
      long freeMemory = Runtime.getRuntime().freeMemory();
      
      return maxMemory - (totalMemory - freeMemory);
   }

   /** Returns the maximum amount of memory available */
   public static long maxMemory() {
      Memory mem = new Memory();
      long maxMemory = mem.getMemorySetting();
         if (maxMemory==0L)
            maxMemory = mem.maxMemory();
      return maxMemory;
   }

}
