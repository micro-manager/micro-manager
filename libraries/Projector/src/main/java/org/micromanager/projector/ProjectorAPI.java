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

   /**
    * Creates a ProjectionDevice from the first SLM (or Galvo if no SLM is present) found by the
    * Micro-Manager Core.
    *
    * @return First ProjectionDevice found
    */
   public ProjectionDevice getProjectionDevice() {
      return ProjectorActions.getProjectionDevice(studio_);
   }

   /**
    * Activates a spot at the center of the Galvo/SLM range
    *
    * @param dev ProjectionDevice to be used
    */
   public void displayCenterSpot(ProjectionDevice dev) {
      ProjectorActions.displayCenterSpot(dev);
   }

   /**
    * Displays a spot at position x,y.
    *
    * @param dev ProjectionDevice to be used
    * @param x   coordinate of ProjectionDevice
    * @param y   coordinate of ProjectionDevice
    */
   public void displaySpot(ProjectionDevice dev, double x, double y) {
      ProjectorActions.displaySpot(dev, x, y);
   }

   /**
    * Converts an array of ImageJ Rois to an array of Polygons. Handles EllipseRois and compound
    * Point ROIs.
    *
    * @param rois ImageJ Rois to be converted to Polygons
    * @return Polygons representing the input ImageJ Rois
    */
   public Polygon[] roisAsPolygons(Roi[] rois) {
      return ProjectorActions.roisAsPolygons(rois);
   }

   public void transformAndSetMask(Mapping mapping,
         ProjectionDevice dev, byte[] inputImage, int width, int height,
         Rectangle cameraROI, Integer cameraBinning) {
      ProjectorActions
            .transformAndSetMask(mapping, dev, inputImage, width, height, cameraROI, cameraBinning);
   }

   /**
    * Transform the Roi polygons with the given nonlinear mapping.
    *
    * @param roiPolygons
    * @param mapping
    * @param cameraROI     current ROI of the camera.  Will be ignored when null
    * @param cameraBinning current binning of the camera.  Assumed to be 1 when null
    * @return
    */
   public List<FloatPolygon> transformRoiPolygons(
         Polygon[] roiPolygons, Mapping mapping, Rectangle cameraROI, Integer cameraBinning) {
      return ProjectorActions.transformRoiPolygons(roiPolygons, mapping, cameraROI, cameraBinning);
   }

   /**
    * Returns ROIs, transformed by the current mapping.
    *
    * @param rois          Array of ImageJ Rois to be converted
    * @param mapping       Mapping between camera and projector coordinates
    * @param cameraROI     current ROI of the camera.  Will be ignored when null
    * @param cameraBinning current binning of the camera.  Assumed to be 1 when null
    * @return list of Rois converted into Polygons
    */
   public List<FloatPolygon> transformROIs(Roi[] rois,
         Mapping mapping, Rectangle cameraROI, Integer cameraBinning) {
      return ProjectorActions.transformROIs(rois, mapping, cameraROI, cameraBinning);
   }

   /**
    * Transform a point in camera coordinates to projector coordinates, given the mapping, which
    * contains a Map of polygon cells to AffineTransforms.
    *
    * @param mapping       Map of cells (quadrants) on the camera image, that each contain the
    *                      affineTransform appropriate to that cell
    * @param pt            Point to be transformed from camera to projector coordinates
    * @param cameraROI     - current ROI of the camera
    * @param cameraBinning - current binning of the camera
    * @return Point in projector coordinates
    */
   public Point2D.Double transformPoint(Mapping mapping, Point2D.Double pt,
         Rectangle cameraROI, Integer cameraBinning) {
      return ProjectorActions.transformPoint(mapping, pt, cameraROI, cameraBinning);
   }

   /**
    * Loads the mapping between camera and projector from user profile.
    *
    * @param dev projection device for which we request the mapping.
    * @return Mapping between camera and projector.
    */
   public Mapping loadMapping(ProjectionDevice dev) {
      return ProjectorActions.loadMapping(studio_, dev);
   }

   /**
    * Sets the exposure time for the photo-targeting device.
    *
    * @param dev        ProjectionDevice to be used
    * @param intervalUs new exposure time in micros
    */
   public void setExposure(ProjectionDevice dev, double intervalUs) {
      ProjectorActions.setExposure(dev, intervalUs);
   }

   /**
    * Enables Point and SHoot mode.  Same as activating this mode in the UI.
    * A listener will be attached to the active data viewer, which will listen for
    * mousevents indicating where the user wants to target the device.  These
    * points will be added to a queue, which is continuously monitored and "shot".
    * If you only want to programmatically add points, use
    * {@link #enablePointAndShootMode(boolean)} instead.
    *
    * @param on whether to switch PointAndShootMode on or off
    */
   public void enablePointAndShootMode(boolean on) {
      // expect Null Pointer Exceptions if the ProjectorControlForm is not open
      projectorControlForm_.enablePointAndShootMode(on);
   }

   /**
    * Enables shoot mode.  Any points added to the PointAndShootQueue will be
    * "shot". No UI elements will be activated.
    *
    * @param on true will enable shoot mode, false will switch it off
    */
   public void enableShootMode(boolean on) {
      projectorControlForm_.enableShootMode(on);
   }

   /**
    * Adds a point to the point and shoot queue.
    * Will be shot if shoot mode is on.
    *
    * @param x location in image coordinates
    * @param y location in image coordinates
    */
   public void addPointToPointAndShootQueue(double x, double y) {
      // expect Null Pointer Exceptions if the ProjectorControlForm is not open
      Point2D p2D = new Point2D.Double(x, y);
      projectorControlForm_.addPointToPointAndShootQueue(p2D);
   }

   /**
    * Establishes the mapping between camera and projector coordinates.
    *
    * @param blocking When blocking, the function will only return after calibration
    *                 is fully executed.
    */
   public void calibrate(boolean blocking) {
      projectorControlForm_.runCalibration(blocking);
   }


}
