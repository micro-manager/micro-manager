package org.micromanager.imagedisplay.dev;

import org.micromanager.api.display.DisplayManager;
import org.micromanager.api.display.DisplaySettings;

public class DefaultDisplayManager implements DisplayManager {
   @Override
   public DisplaySettings getStandardDisplaySettings() {
      return DefaultDisplaySettings.getStandardSettings();
   }

   @Override
   public DisplaySettings.DisplaySettingsBuilder getDisplaySettingsBuilder() {
      return new DefaultDisplaySettings.Builder();
   }
}
