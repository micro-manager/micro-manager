package org.micromanager.plugins.isim;

import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Data model for the alignment tool. All fields are persisted via the MM user
 * profile and loaded/saved explicitly.
 */
public class AlignmentModel {
   private static final String KEY_ANGLE_DEG = "angleDeg";
   private static final String KEY_SPACING_PX = "spacingPx";
   private static final String KEY_OFFSET_X = "offsetX";
   private static final String KEY_OFFSET_Y = "offsetY";
   private static final String KEY_DETECTION_ENABLED = "detectionEnabled";
   private static final String KEY_THRESHOLD = "threshold";
   private static final String KEY_WINDOW_PX = "windowPx";

   private static final double DEFAULT_ANGLE_DEG = -4.13;
   private static final int DEFAULT_SPACING_PX = 64;
   private static final int DEFAULT_OFFSET_X = 0;
   private static final int DEFAULT_OFFSET_Y = 0;
   private static final boolean DEFAULT_DETECTION_ENABLED = false;
   private static final int DEFAULT_THRESHOLD = 500;
   private static final int DEFAULT_WINDOW_PX = 20;

   private final MutablePropertyMapView settings_;

   private double angleDeg_;
   private double angleRad_;
   private int spacingPx_;
   private int offsetX_;
   private int offsetY_;
   private boolean detectionEnabled_;
   // Volatile: written from EDT (spinner listeners), read from detection thread.
   private volatile int threshold_;
   private volatile int windowPx_;

   public AlignmentModel(MutablePropertyMapView settings) {
      settings_ = settings;
      angleDeg_ = settings_.getDouble(KEY_ANGLE_DEG, DEFAULT_ANGLE_DEG);
      angleRad_ = Math.toRadians(angleDeg_);
      spacingPx_ = settings_.getInteger(KEY_SPACING_PX, DEFAULT_SPACING_PX);
      offsetX_ = settings_.getInteger(KEY_OFFSET_X, DEFAULT_OFFSET_X);
      offsetY_ = settings_.getInteger(KEY_OFFSET_Y, DEFAULT_OFFSET_Y);
      detectionEnabled_ = settings_.getBoolean(KEY_DETECTION_ENABLED, DEFAULT_DETECTION_ENABLED);
      threshold_ = settings_.getInteger(KEY_THRESHOLD, DEFAULT_THRESHOLD);
      windowPx_ = settings_.getInteger(KEY_WINDOW_PX, DEFAULT_WINDOW_PX);
   }

   public void save() {
      settings_.putDouble(KEY_ANGLE_DEG, angleDeg_);
      settings_.putInteger(KEY_SPACING_PX, spacingPx_);
      settings_.putInteger(KEY_OFFSET_X, offsetX_);
      settings_.putInteger(KEY_OFFSET_Y, offsetY_);
      settings_.putBoolean(KEY_DETECTION_ENABLED, detectionEnabled_);
      settings_.putInteger(KEY_THRESHOLD, threshold_);
      settings_.putInteger(KEY_WINDOW_PX, windowPx_);
   }

   public double getAngleDeg() {
      return angleDeg_;
   }

   public void setAngleDeg(double angleDeg) {
      angleDeg_ = angleDeg;
      angleRad_ = Math.toRadians(angleDeg);
   }

   public double getAngleRad() {
      return angleRad_;
   }

   public int getSpacingPx() {
      return spacingPx_;
   }

   public void setSpacingPx(int spacingPx) {
      spacingPx_ = spacingPx;
   }

   public int getOffsetX() {
      return offsetX_;
   }

   public void setOffsetX(int offsetX) {
      offsetX_ = offsetX;
   }

   public int getOffsetY() {
      return offsetY_;
   }

   public void setOffsetY(int offsetY) {
      offsetY_ = offsetY;
   }

   public boolean isDetectionEnabled() {
      return detectionEnabled_;
   }

   public void setDetectionEnabled(boolean detectionEnabled) {
      detectionEnabled_ = detectionEnabled;
   }

   public int getThreshold() {
      return threshold_;
   }

   public void setThreshold(int threshold) {
      threshold_ = threshold;
   }

   public int getWindowPx() {
      return windowPx_;
   }

   public void setWindowPx(int windowPx) {
      windowPx_ = windowPx;
   }
}
