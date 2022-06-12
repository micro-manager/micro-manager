/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.profile.internal;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Access the Java Preferences nodes used in Micro-Manager 1.
 * <p>
 * Intended for extracting some critical settings from the old preferences.
 *
 * @author Chris Weisiger, extracted to separate class by Mark A. Tsuchida
 */
public final class LegacyMM1Preferences {
   private LegacyMM1Preferences() {
   }

   /**
    * @return pref node, or null if not available
    */
   public static Preferences getUserRoot() {
      return getNode(Preferences.userRoot());
   }

   /**
    * @return pref node, or null if not available
    */
   public static Preferences getSystemRoot() {
      return getNode(Preferences.systemRoot());
   }

   private static Preferences getNode(Preferences root) {
      // Ensure the necessary nodes exist.
      try {
         if (!root.nodeExists("org")) {
            return null;
         }
         root = root.node("org");
         if (!root.nodeExists("micromanager")) {
            return null;
         }
         return root.node("micromanager");
      } catch (BackingStoreException e) {
         // No old preferences found.
         return null;
      }
   }
}