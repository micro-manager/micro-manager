package org.micromanager.internal.utils;

import java.util.prefs.Preferences;

import org.micromanager.UserSettings;

public class DefaultUserSettings implements UserSettings {
   private String userName_;
   public DefaultUserSettings(String userName) {
      userName_ = userName;
   }

   @Override
   public Preferences userNodeForPackage(Class<?> c) {
      Preferences topNode = Preferences.userNodeForPackage(c);
      return topNode.node(userName_);
   }
}
