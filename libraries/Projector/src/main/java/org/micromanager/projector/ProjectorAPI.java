package org.micromanager.projector;

import ij.gui.Roi;
import ij.process.FloatPolygon;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.List;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.projector.internal.ProjectorControlForm;

/**
 * Simple API for Projector control Created to provide access to Projector function from
 * pycromanager In java, use ProjectorActions instead, which gives static accessor functions
 * pycromanager can not use static functions (yet), hence this class...
 */
public class ProjectorAPI {

   ProjectorControlForm projectorControlForm_;
   Studio studio_;

   public ProjectorAPI() {
      studio_ = MMStudio.getInstance();
      projectorControlForm_ = ProjectorControlForm.getSingleton();
   }

   public ProjectorAPI(Studio studio) {
      studio_ = studio;
      projectorControlForm_ = ProjectorControlForm.getSingleton();
   }

   public ProjectionDevice getProjectionDevice() {
      return ProjectorActions.getProjectionDevice(studio_);
   }

   public void displayCenterSpot(ProjectionDevice dev) {
      ProjectorActions.displayCenterSpot(dev);
   }

   public void displaySpot(ProjectionDevice dev, double x, double y) {
      ProjectorActions.displaySpot(dev, x, y);
   }

   public Polygon[] roisAsPolygons(Roi[] rois) {
      return ProjectorActions.roisAsPolygons(rois);
   }

   public void transformAndSetMask(Mapping mapping,
         ProjectionDevice dev, byte[] inputImage, int width, int height,
         Rectangle cameraROI, Integer cameraBinning) {
      ProjectorActions
            .transformAndSetMask(mapping, dev, inputImage, width, height, cameraROI, cameraBinning);
   }

   public List<FloatPolygon> transformRoiPolygons(
         Polygon[] roiPolygons, Mapping mapping, Rectangle cameraROI, Integer cameraBinning) {
      return ProjectorActions.transformRoiPolygons(roiPolygons, mapping, cameraROI, cameraBinning);
   }

   public List<FloatPolygon> transformROIs(Roi[] rois,
         Mapping mapping, Rectangle cameraROI, Integer cameraBinning) {
      return ProjectorActions.transformROIs(rois, mapping, cameraROI, cameraBinning);
   }

   public Point2D.Double transformPoint(Mapping mapping, Point2D.Double pt,
         Rectangle cameraROI, Integer cameraBinning) {
      return ProjectorActions.transformPoint(mapping, pt, cameraROI, cameraBinning);
   }

   public Mapping loadMapping(ProjectionDevice dev) {
      return ProjectorActions.loadMapping(studio_, dev);
   }

   public void setExposure(ProjectionDevice dev, double intervalUs) {
      ProjectorActions.setExposure(dev, intervalUs);
   }

   public void enablePointAndShootMode(boolean on) {
      // expect Null Pointer Exceptions if the ProjectorControlForm is not open
      projectorControlForm_.enablePointAndShootMode(on);
   }

   public void addPointToPointAndShootQueue(double x, double y) {
      // expect Null Pointer Exceptions if the ProjectorControlForm is not open
      Point2D p2D = new Point2D.Double(x, y);
      projectorControlForm_.addPointToPointAndShootQueue(p2D);
   }

   public void calibrate(boolean blocking) {
      projectorControlForm_.runCalibration(blocking);
   }


}
