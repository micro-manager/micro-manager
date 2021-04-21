package org.micromanager.internal.pixelcalibrator;

import java.awt.geom.AffineTransform;

/** @author nico */
public class CalibrationThread extends Thread {

  protected int progress_;
  protected AffineTransform result_ = null;

  synchronized int getProgress() {
    return progress_;
  }

  AffineTransform getResult() {
    return result_;
  }
}
