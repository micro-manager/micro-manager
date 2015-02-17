package org.micromanager;

import java.util.prefs.Preferences;

/**
 * This interface provides a way to save and recall parameters across multiple
 * sessions of the program. It acts as a wrapper around the
 * java.util.prefs.Preferences class, and handles the use case where multiple
 * users are using the same system-level account, and thus would otherwise
 * trample each others' settings in the Preferences.
 */
public interface UserSettings {
   /**
    * As the java.util.prefs.Preferences implementation of the similarly-named
    * method, except that the Preferences object returned will be specific
    * to the currently-selected user.
    */
   public Preferences userNodeForPackage(Class<?> c);
}
