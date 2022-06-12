package org.micromanager.internal.utils;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.micromanager.profile.internal.LegacyMM1Preferences;

/**
 * This package is here for old code we can't wait to get rid of but need to
 * keep around for a little while.
 */
@Deprecated
public final class UnpleasantLegacyCode {
   public static AffineTransform legacyRetrieveTransformFromPrefs(String key) {
      Preferences prefs = LegacyMM1Preferences.getUserRoot();
      try {
         if (prefs == null || !prefs.nodeExists(key)) {
            // No prefs, or key not found.
            return null;
         }
      } catch (BackingStoreException e) {
         ReportingUtils.logError(e, "Error checking for preferences node");
      }
      ArrayList<byte[]> chunks = new ArrayList<byte[]>();
      byte[] serialBytes = new byte[0];
      int totalLength = 0;
      try {
         for (String chunkKey : prefs.node(key).keys()) {
            byte[] chunk = prefs.node(key).getByteArray(chunkKey, new byte[0]);
            chunks.add(chunk);
            totalLength += chunk.length;
         }
         int pos = 0;
         serialBytes = new byte[totalLength];
         for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, serialBytes, pos, chunk.length);
            pos += chunk.length;
         }
      } catch (BackingStoreException ex) {
         ReportingUtils.logError(ex);
      }

      if (serialBytes.length == 0) {
         return null;
      }
      ByteArrayInputStream byteStream = new ByteArrayInputStream(serialBytes);
      try {
         ObjectInputStream objectStream = new ObjectInputStream(byteStream);
         return (AffineTransform) objectStream.readObject();
      } catch (Exception e) {
         ReportingUtils.logError(e, "Failed to get object from preferences.");
         return null;
      }
   }
}
