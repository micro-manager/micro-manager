/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.devices.crisp;

/**
 * A data class to store software settings for CRISP.
 *
 * <p>Also can convert {@code CRISPSettings} to and from JSON.
 */
public class CRISPSettings {

   private String name;
   private int gain;
   private int ledIntensity;
   private int updateRateMs;
   private int numAverages;
   private double objectiveNA;
   private double lockRange;

   public static final String NAME_PREFIX = "Profile";
   public static final String SETTINGS_NOT_FOUND = "No Settings";
   public static final String DEFAULT_PROFILE_NAME = "Default";

   public CRISPSettings(final String name) {
      this.name = name;
      this.gain = 1;
      this.ledIntensity = 50;
      this.updateRateMs = 10;
      this.numAverages = 1;
      this.objectiveNA = 0.65;
      this.lockRange = 1.0;
   }

   public CRISPSettings(
         final String name,
         final int gain,
         final int ledIntensity,
         final int updateRateMs,
         final int numAverages,
         final double objectiveNA,
         final double lockRange) {
      this.name = name;
      this.gain = gain;
      this.ledIntensity = ledIntensity;
      this.updateRateMs = updateRateMs;
      this.numAverages = numAverages;
      this.objectiveNA = objectiveNA;
      this.lockRange = lockRange;
   }

   @Override
   public String toString() {
      return String.format(
            "%s[name=\"%s\", gain=%s, ledIntensity=%s, updateRateMs=%s,"
                    + " numAverages=%s, objectiveNA=%s, lockRange=%s]",
              getClass().getSimpleName(),
              name, gain, ledIntensity, updateRateMs, numAverages, objectiveNA, lockRange
      );
   }

   public int getGain() {
      return gain;
   }

   public int getLEDIntensity() {
      return ledIntensity;
   }

   public int getUpdateRateMs() {
      return updateRateMs;
   }

   public int getNumAverages() {
      return numAverages;
   }

   public double getObjectiveNA() {
      return objectiveNA;
   }

   public double getLockRange() {
      return lockRange;
   }

   public String getName() {
      return name;
   }

   public void setGain(final int n) {
      gain = n;
   }

   public void setLEDIntensity(final int n) {
      ledIntensity = n;
   }

   public void setUpdateRateMs(final int n) {
      updateRateMs = n;
   }

   public void setNumAverages(final int n) {
      numAverages = n;
   }

   public void setObjectiveNA(final double n) {
      objectiveNA = n;
   }

   public void setLockRange(final double n) {
      lockRange = n;
   }

   public void setName(final String newName) {
      name = newName;
   }

}
