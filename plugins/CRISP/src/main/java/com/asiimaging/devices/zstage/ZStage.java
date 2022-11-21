/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */

package com.asiimaging.devices.zstage;

import java.util.Objects;
import mmcorej.CMMCore;
import org.micromanager.Studio;

public class ZStage {

   private final Studio studio;
   private final CMMCore core;

   private String deviceName;

   public ZStage(final Studio studio) {
      this.studio = Objects.requireNonNull(studio);
      this.core = studio.core();
      deviceName = "";
   }

   public void findDevice() {
      deviceName = core.getFocusDevice();
      //System.out.println(deviceName);
   }

   public void setPosition(final double n) {
      try {
         core.setPosition(deviceName, n);
      } catch (Exception e) {
         studio.logs().logError("ZStage: could not set position!");
      }
   }

   public void setRelativePosition(final double n) {
      try {
         core.setRelativePosition(deviceName, n);
      } catch (Exception e) {
         studio.logs().logError("ZStage: could not set relative position!");
      }
   }

   public double getPosition() {
      double result = 0.0;
      try {
         result = core.getPosition(deviceName);
      } catch (Exception e) {
         studio.logs().logError("ZStage: could not get the position!");
      }
      return result;
   }

   public void waitForDevice() {
      try {
         core.waitForDevice(deviceName);
      } catch (Exception e) {
         studio.logs().logError("ZStage: could not wait for device!");
      }
   }

   public float getBacklash() {
      float result = 0.0f;
      try {
         result = Float.parseFloat(core.getProperty(deviceName, "Backlash-B(um)"));
      } catch (Exception e) {
         studio.logs().logError("ZStage: could not get the backlash value!");
      }
      return result;
   }

   public void setBacklash(final float backlash) {
      try {
         core.setProperty(deviceName, "Backlash-B(um)", backlash);
      } catch (Exception e) {
         studio.logs().logError("ZStage: could not set the backlash value!");
      }
   }

}
