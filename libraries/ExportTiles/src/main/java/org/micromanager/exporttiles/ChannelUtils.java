package org.micromanager.exporttiles;

/**
 * Shared channel-matching utilities for ExportTiles.
 */
public final class ChannelUtils {

   private ChannelUtils() {
   }

   /**
    * Returns true when a channel name from the caller matches a channel value from storage.
    *
    * <p>Handles the case where unnamed channels are stored as {@code Integer} indices
    * but the caller passes the index as a {@code String} (e.g. {@code "0"}).</p>
    */
   public static boolean channelValuesMatch(String callerName, Object storedValue) {
      if (storedValue == null) {
         return false;
      }
      if (callerName.equals(storedValue)) {
         return true;
      }
      if (storedValue instanceof Integer) {
         try {
            return Integer.parseInt(callerName) == (Integer) storedValue;
         } catch (NumberFormatException e) {
            return false;
         }
      }
      return false;
   }
}
