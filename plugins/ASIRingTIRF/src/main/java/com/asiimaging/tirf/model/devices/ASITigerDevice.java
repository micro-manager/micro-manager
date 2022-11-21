/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.model.devices;

import java.util.Objects;
import mmcorej.CMMCore;
import org.micromanager.LogManager;
import org.micromanager.Studio;

/**
 * The common base class for all ASI Tiger devices.
 */
public abstract class ASITigerDevice {

   protected String deviceName;

   protected final CMMCore core;
   protected final LogManager logs;

   public ASITigerDevice(final Studio studio) {
      Objects.requireNonNull(studio);
      core = studio.core();
      logs = studio.logs();
      deviceName = "";
   }

   public String getDeviceName() {
      return deviceName;
   }

   public void setDeviceName(final String name) {
      deviceName = name;
   }

   public String getDeviceInfoString() {
      return !deviceName.equals("")
            ? getFirmwareBuild() + " v" + getFirmwareVersion() + " " + getFirmwareDate() :
            "No Device";
   }

   public String getFirmwareBuild() {
      try {
         return core.getProperty(deviceName, Keys.FIRMWARE_BUILD);
      } catch (Exception e) {
         logs.logError("could not get the firmware build property");
      }
      return "";
   }

   public String getFirmwareDate() {
      try {
         return core.getProperty(deviceName, Keys.FIRMWARE_DATE);
      } catch (Exception e) {
         logs.logError("could not get the firmware date property");
      }
      return "";
   }

   public float getFirmwareVersion() {
      try {
         return Float.parseFloat(core.getProperty(deviceName, Keys.FIRMWARE_VERSION));
      } catch (Exception e) {
         logs.logError("could not get the firmware version property");
      }
      return 0.0f;
   }

   public int getTigerHexAddress() {
      try {
         return Integer.parseInt(core.getProperty(deviceName, Keys.TIGER_HEX_ADDRESS));
      } catch (Exception e) {
         logs.logError("could not get the tiger hex address property");
      }
      return -1;
   }

   public void setRefreshPropertyValues(final boolean value) {
      try {
         core.setProperty(deviceName, Keys.REFRESH_PROPERTY_VALUES, value ? "Yes" : "No");
      } catch (Exception e) {
         logs.logError("setRefreshPropertyValues failed.");
      }
   }

   public boolean getRefreshPropertyValues() {
      try {
         return core.getProperty(deviceName, Keys.REFRESH_PROPERTY_VALUES).equals("Yes");
      } catch (Exception e) {
         logs.logError("getRefreshPropertyValues failed.");
      }
      return false;
   }

   public String getDescription() {
      String result = "";
      try {
         result = core.getProperty(deviceName, Keys.DESCRIPTION);
      } catch (Exception e) {
         logs.logError("could not get the description property");
      }
      return result;
   }

   public String getName() {
      String result = "";
      try {
         result = core.getProperty(deviceName, Keys.NAME);
      } catch (Exception e) {
         logs.logError("could not get the name property");
      }
      return result;
   }

   public class Keys {
      public static final String TIGER_LIBRARY_NAME = "ASITiger";
      // public static final String MS2000_LIBRARY_NAME = "ASIStage";

      public static final String NAME = "Name";
      public static final String DESCRIPTION = "Description";
      public static final String TIGER_HEX_ADDRESS = "TigerHexAddress";

      public static final String FIRMWARE_BUILD = "FirmwareBuild";
      public static final String FIRMWARE_DATE = "FirmwareDate";
      public static final String FIRMWARE_VERSION = "FirmwareVersion";

      public static final String REFRESH_PROPERTY_VALUES = "RefreshPropertyValues";
   }
}
