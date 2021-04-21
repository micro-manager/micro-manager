///////////////////////////////////////////////////////////////////////////////
// FILE:          DataSeriesKey.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     PointAndShootAnalyzer plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2018
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

package org.micromanager.pointandshootanalysis.plot;

/**
 * Data structure to be used as Key for data series in Point and Shoot code.
 *
 * @author nico
 */
public class DataSeriesKey implements Comparable<DataSeriesKey> {
  private final int frameNr_;
  private final int xPixel_;
  private final int yPixel_;

  public DataSeriesKey(int frameNr, int xPixel, int yPixel) {
    frameNr_ = frameNr;
    xPixel_ = xPixel;
    yPixel_ = yPixel;
  }

  @Override
  public int compareTo(DataSeriesKey other) {
    if (frameNr_ < other.frameNr_) {
      return -1;
    } else if (frameNr_ > other.frameNr_) {
      return 1;
    }
    if (xPixel_ < other.xPixel_) {
      return -1;
    } else if (xPixel_ > other.xPixel_) {
      return 1;
    }
    if (yPixel_ < other.yPixel_) {
      return -1;
    } else if (yPixel_ > other.yPixel_) {
      return 1;
    }
    return 0;
  }

  @Override
  public String toString() {
    StringBuilder strB = new StringBuilder();
    strB.append(frameNr_).append("-").append(xPixel_).append("-").append(yPixel_);
    return strB.toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof DataSeriesKey) {
      DataSeriesKey dOther = (DataSeriesKey) other;
      if (frameNr_ == dOther.frameNr_ && xPixel_ == dOther.xPixel_ && yPixel_ == dOther.yPixel_) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 53 * hash + this.frameNr_;
    hash = 53 * hash + this.xPixel_;
    hash = 53 * hash + this.yPixel_;
    return hash;
  }
}
