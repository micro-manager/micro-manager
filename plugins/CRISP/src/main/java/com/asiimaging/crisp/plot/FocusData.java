/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.crisp.plot;

/**
 * An individual data point in a FocusDataSet.
 */
public class FocusData {

   private final double time;
   private final double position;
   private final double error;

   public FocusData(final double time, final double position, final double error) {
      this.time = time;
      this.position = position;
      this.error = error;
   }

   @Override
   public String toString() {
      return String.format("%s(%s, %s, %s)",
            getClass().getSimpleName(),
            time, position, error);
   }

   public String toStringCSV() {
      return time + "," + position + "," + error;
   }

   public double getTime() {
      return time;
   }

   public double getPosition() {
      return position;
   }

   public double getError() {
      return error;
   }
}
