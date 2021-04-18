///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.



package org.micromanager;

import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * A user profile, where user preferences can be stored.
 *
 * In MMStudio and its plugins, <em>all</em> user preferences should be stored
 * in the current user profile, <em>not in the standard Java preferences</em>.
 * <p>
 * This works similarly to {@link java.util.prefs.Preferences}, except that
 * it is possible to switch between multiple profiles within a single operating
 * system user account. This is provided because sharing an OS account by all
 * users is a widespread practice on computers attached to scientific equipment.
 * <p>
 * The user profile stores the same kinds of values as {@link PropertyMap},
 * but is mutable and periodically saves any modifications (if  thus set up by
 * the system).
 *
 * @author Chris Weisiger, Mark A. Tsuchida
 */
public interface UserProfile {
   /**
    * Return the name of this profile as displayed to the user
    * @return the profile name
    */
   String getProfileName();

   /**
    * Get an interface to save and retrieve settings.
    * <p>
    * This is the main interface for accessing values in the user profile.
    *
    * @param owner the class that "owns" the settings
    * @return an object allowing settings to be set and retrieved
    */
   MutablePropertyMapView getSettings(Class<?> owner);

   /**
    * Reset this user profile, deleting all settings.
    * <p>
    * Do not confuse with {@code getSettings(owner).clear()}!
    */
   void clearSettingsForAllClasses();


   // Old methods with weird types

   /** @deprecated use {@code getSettings(c).getString(key, fallback)} instead */
   @Deprecated
   String getString(Class<?> c, String key, String fallback);
   /** @deprecated use {@code getSettings(c).getStringList(key, fallback)} instead */
   @Deprecated
   String[] getStringArray(Class<?> c, String key, String[] fallback);
   /** @deprecated use {@code getSettings(c).putString(key, value)} instead */
   @Deprecated
   void setString(Class<?> c, String key, String value);
   /** @deprecated use {@code getSettings(c).putStringList(key, value)} instead */
   @Deprecated
   void setStringArray(Class<?> c, String key, String[] value);
   /** @deprecated use {@code getSettings(c).getInteger(key, fallback)} instead */
   @Deprecated
   Integer getInt(Class<?> c, String key, Integer fallback);
   /** @deprecated use {@code getSettings(c).getIntegerList(key, fallback)} instead */
   @Deprecated
   Integer[] getIntArray(Class<?> c, String key, Integer[] fallback);
   /** @deprecated use {@code getSettings(c).putInteger(key, value)} instead */
   @Deprecated
   void setInt(Class<?> c, String key, Integer value);
   /** @deprecated use {@code getSettings(c).putIntegerList(key, value)} instead */
   @Deprecated
   void setIntArray(Class<?> c, String key, Integer[] value);
   /** @deprecated use {@code getSettings(c).getLong(key, fallback)} instead */
   @Deprecated
   Long getLong(Class<?> c, String key, Long fallback);
   /** @deprecated use {@code getSettings(c).getLongList(key, fallback)} instead */
   @Deprecated
   Long[] getLongArray(Class<?> c, String key, Long[] fallback);
   /** @deprecated use {@code getSettings(c).putLong(key, value)} instead */
   @Deprecated
   void setLong(Class<?> c, String key, Long value);
   /** @deprecated use {@code getSettings(c).putLongList(key, value)} instead */
   @Deprecated
   void setLongArray(Class<?> c, String key, Long[] value);
   /** @deprecated use {@code getSettings(c).getDouble(key, fallback)} instead */
   @Deprecated
   Double getDouble(Class<?> c, String key, Double fallback);
   /** @deprecated use {@code getSettings(c).getDoubleList(key, fallback)} instead */
   @Deprecated
   Double[] getDoubleArray(Class<?> c, String key, Double[] fallback);
   /** @deprecated use {@code getSettings(c).putDouble(key, value)} instead */
   @Deprecated
   void setDouble(Class<?> c, String key, Double value);
   /** @deprecated use {@code getSettings(c).putDoubleList(key, value)} instead */
   @Deprecated
   void setDoubleArray(Class<?> c, String key, Double[] value);
   /** @deprecated use {@code getSettings(c).getBoolean(key, fallback)} instead */
   @Deprecated
   Boolean getBoolean(Class<?> c, String key, Boolean fallback);
   /** @deprecated use {@code getSettings(c).getBooleanList(key, fallback)} instead */
   @Deprecated
   Boolean[] getBooleanArray(Class<?> c, String key, Boolean[] fallback);
   /** @deprecated use {@code getSettings(c).putBoolean(key, value)} instead */
   @Deprecated
   void setBoolean(Class<?> c, String key, Boolean value);
   /** @deprecated use {@code getSettings(c).putBooleanList(key, value)} instead */
   @Deprecated
   void setBooleanArray(Class<?> c, String key, Boolean[] value);
}