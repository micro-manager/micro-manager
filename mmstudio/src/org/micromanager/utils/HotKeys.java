package org.micromanager.utils;

import java.util.LinkedHashMap;

/**
 *
 * @author nico
 */
public class HotKeys {
   // Note that this data structure is not synchroinized.  Since we are not
   // currently reading and writing at the same time, and access it only from
   // a single thread (I think), this should be safe.
   // Howvere, if this changes in the future, please synchronize this structure

   public static final LinkedHashMap<Integer, HotKeyAction> keys_ =
           new LinkedHashMap<Integer, HotKeyAction>();

   public  static boolean active_ = true;

   public static void readPrefs() {

   }
   public static void writePrefs() {

   }
}
