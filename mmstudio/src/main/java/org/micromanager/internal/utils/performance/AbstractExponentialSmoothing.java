// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.internal.utils.performance;

/**
 * Base implementation for exponential smoothing of time series statistic.
 *
 * <p>This provides a common implementation for {@link ExponentialSmoothing} and {@link
 * TimeIntervalExponentialSmoothing}.
 *
 * <p>See https://en.wikipedia.org/wiki/Exponential_smoothing
 *
 * @author Mark A. Tsuchida
 */
public class AbstractExponentialSmoothing {
  private final double timeConstantMs_;

  private long lastNanoTime_ = -1;

  private double rollingAverage_;
  private double rollingSquareAverage_;
  private long count_;

  protected AbstractExponentialSmoothing(double timeConstantMs) {
    timeConstantMs_ = timeConstantMs;
  }

  public double getTimeConstantMs() {
    return timeConstantMs_;
  }

  public long getCount() {
    return count_;
  }

  public double getAverage() {
    return rollingAverage_;
  }

  public double getStandardDeviation() {
    return Math.sqrt(rollingSquareAverage_ - rollingAverage_ * rollingAverage_);
  }

  @Override
  public String toString() {
    return String.format("Avg = %g, Stdev = %g", getAverage(), getStandardDeviation());
  }

  protected boolean isTimingStarted() {
    return lastNanoTime_ >= 0;
  }

  protected boolean isStatsInitialized() {
    return count_ > 0;
  }

  protected void markTime() {
    lastNanoTime_ = System.nanoTime();
  }

  protected double markTimeAndGetDeltaTMs() {
    if (lastNanoTime_ < 0) {
      throw new IllegalStateException("Programming error");
    }
    long now = System.nanoTime();
    long deltaTNs = now - lastNanoTime_;
    lastNanoTime_ = now;
    return deltaTNs / 1000000.0;
  }

  protected void initializeStats(double x) {
    rollingAverage_ = x;
    rollingSquareAverage_ = x * x;
    count_ = 1;
  }

  protected void updateStats(double deltaTMs, double x) {
    // See https://en.wikipedia.org/wiki/Exponential_smoothing and
    // https://en.wikipedia.org/wiki/Moving_average#Application_to_measuring_computer_performance

    final double alpha = alpha(deltaTMs);
    rollingAverage_ = alpha * x + (1.0 - alpha) * rollingAverage_;

    rollingSquareAverage_ = alpha * (x * x) + (1.0 - alpha) * rollingSquareAverage_;

    ++count_;
  }

  private double alpha(double deltaTMs) {
    return 1.0 - Math.exp(-deltaTMs / timeConstantMs_);
  }
}
