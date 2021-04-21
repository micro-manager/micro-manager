package org.micromanager.displays.internal;

import static org.junit.Assert.*;
import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.display.internal.DefaultDisplaySettings;

/** @author nico */
public class DisplaySettingsTests {

  @Test
  public void testDisplaySettings() {
    // Generate default DisplaySettings
    DefaultDisplaySettings ds = (DefaultDisplaySettings) DefaultDisplaySettings.builder().build();
    PropertyMap pMap = ds.toPropertyMap();
    DefaultDisplaySettings fromMap =
        (DefaultDisplaySettings) DefaultDisplaySettings.fromPropertyMap(pMap);

    assertEquals(ds.getAutoscaleIgnoredQuantile(), fromMap.getAutoscaleIgnoredQuantile(), 0.00001);
    assertEquals(ds.getChannelColorMode(), fromMap.getChannelColorMode());
    assertEquals(ds.getPlaybackFPS(), fromMap.getPlaybackFPS(), 0.000001);
    assertEquals(ds.isAutostretchEnabled(), fromMap.isAutostretchEnabled());
    assertEquals(ds.isROIAutoscaleEnabled(), fromMap.isROIAutoscaleEnabled());

    // TODO: Change Channel and Component Display Settings

    // TODO: Make changes to the default, go through Propertymap conversion and test

  }
}
