package org.micromanager.projector.internal;

import java.awt.Point;
import java.awt.geom.Point2D;
import mmcorej.Configuration;
import org.micromanager.projector.ProjectionDevice;

/**
 * Simple class to hold data needed for Point and SHoot actions Instances of this class are used on
 * a blocking queue, used to asynchronously execute Point and Shoot actions
 *
 * @author nico
 */
public class PointAndShootInfo {

   private final ProjectionDevice dev_;
   private final Point2D.Double devPoint_;
   private final Configuration originalConfig_;
   private final Point canvasPoint_;
   private final boolean stop_;

   public static final class Builder {

      private ProjectionDevice dev_;
      private Point2D.Double devPoint_;
      private Configuration originalConfig_;
      private Point canvasPoint_;
      private boolean stop_ = false;

      public Builder projectionDevice(ProjectionDevice dev) {
         dev_ = dev;
         return this;
      }

      public Builder devPoint(Point2D.Double devPoint) {
         devPoint_ = devPoint;
         return this;
      }


      public Builder originalConfig(Configuration config) {
         originalConfig_ = config;
         return this;
      }

      public Builder canvasPoint(Point canvasPoint) {
         canvasPoint_ = canvasPoint;
         return this;
      }

      public Builder stop() {
         stop_ = true;
         return this;
      }

      public PointAndShootInfo build() {
         return new PointAndShootInfo(dev_,
               devPoint_,
               originalConfig_,
               canvasPoint_,
               stop_);
      }

   }

   private PointAndShootInfo(ProjectionDevice dev,
         Point2D.Double devPoint,
         Configuration originalConfig,
         Point canvasPoint,
         boolean stop) {
      dev_ = dev;
      devPoint_ = devPoint;
      originalConfig_ = originalConfig;
      canvasPoint_ = canvasPoint;
      stop_ = stop;
   }

   public ProjectionDevice getDevice() {
      return dev_;
   }

   public Point2D.Double getDevPoint() {
      return devPoint_;
   }

   public Configuration getOriginalConfig() {
      return originalConfig_;
   }

   public Point getCanvasPoint() {
      return canvasPoint_;
   }

   public Boolean isStopped() {
      return stop_;
   }
}
