/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.model.devices;

import java.util.Objects;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.micromanager.Studio;

public class DeviceBase {

   protected final Studio studio_;
   protected final CMMCore core_;

   protected String deviceName_;

   public DeviceBase(final Studio studio) {
      studio_ = Objects.requireNonNull(studio);
      core_ = studio_.core();
   }

   public DeviceBase(final Studio studio, final String deviceName) {
      studio_ = Objects.requireNonNull(studio);
      core_ = studio_.core();
      deviceName_ = deviceName;
   }

   public void deviceName(final String deviceName) {
      deviceName_ = deviceName;
   }

   public String deviceName() {
      return deviceName_;
   }

   public boolean hasProperty(final String propertyName) {
      boolean result = false;
      try {
         result = core_.hasProperty(deviceName_, propertyName);
      } catch (Exception e) {
         studio_.logs().showError("Error: could not determine if " + propertyName + " exists.");
      }
      return result;
   }

   public String getProperty(final String propertyName) {
      String result = "";
      try {
         result = core_.getProperty(deviceName_, propertyName);
      } catch (Exception e) {
         studio_.logs().logError("Could not get the \"" + propertyName + "\" property.");
      }
      return result;
   }

   public void setProperty(final String propertyName, final String propertyValue) {
      try {
         core_.setProperty(deviceName_, propertyName, propertyValue);
      } catch (Exception e) {
         studio_.logs().logError("Could not set the \"" + propertyName + "\" property to " + propertyValue + ".");
      }
   }

   public int getPropertyInt(final String propertyName) {
      int result = 0;
      try {
         result = Integer.parseInt(core_.getProperty(deviceName_, propertyName));
      } catch (Exception e) {
         studio_.logs().logError("Could not get the \"" + propertyName + "\" property as an integer.");
      }
      return result;
   }

   public void setPropertyInt(final String propertyName, final int propertyValue) {
      try {
         core_.setProperty(deviceName_, propertyName, propertyValue);
      } catch (Exception e) {
         studio_.logs().logError("Could not set the \"" + propertyName + "\" property to " + propertyValue + ".");
      }
   }

   // Note: this refers to the Micro-Manager FloatProperty, not the Java float type.
   // See: the FloatProperty class in mmCoreAndDevices/MMDevice/Property.h
   public double getPropertyFloat(final String propertyName) {
      double result = 0.0;
      try {
         result = Double.parseDouble(core_.getProperty(deviceName_, propertyName));
      } catch (Exception e) {
         studio_.logs().logError("could not get the \"" + propertyName + "\" property as a double.");
      }
      return result;
   }

   public void setPropertyFloat(final String propertyName, final double propertyValue) {
      try {
         core_.setProperty(deviceName_, propertyName, propertyValue);
      } catch (Exception e) {
         studio_.logs().logError("could not set the \"" + propertyName + "\" property to " + propertyValue + ".");
      }
   }

   public boolean isPropertyPreInit(final String propertyName) {
      boolean result = false;
      try {
         result = core_.isPropertyPreInit(deviceName_, propertyName);
      } catch (Exception e) {
         studio_.logs().logError(e.getMessage());
      }
      return result;
   }

   public boolean isPropertyReadOnly(final String propertyName) {
      boolean result = false;
      try {
         result = core_.isPropertyReadOnly(deviceName_, propertyName);
      } catch (Exception e) {
         studio_.logs().logError(e.getMessage());
      }
      return result;
   }

   public DeviceType deviceType(final String deviceName) {
      try {
         return core_.getDeviceType(deviceName);
      } catch (Exception e) {
         return DeviceType.UnknownType;
      }
   }

   public String deviceLibrary() {
      try {
         return core_.getDeviceLibrary(deviceName_);
      } catch (Exception e) {
         return "Unknown";
      }
   }

   public String[] devicePropertyNames() {
      StrVector devices = new StrVector();
      try {
         devices = core_.getDevicePropertyNames(deviceName_);
      } catch (Exception e) {
         studio_.logs().logError(e.getMessage());
      }
      return devices.toArray();
   }

   public void waitForDevice() {
      try {
         core_.waitForDevice(deviceName_);
      } catch (Exception e) {
         studio_.logs().logError("Error waiting for device: " + deviceName_);
      }
   }
}
