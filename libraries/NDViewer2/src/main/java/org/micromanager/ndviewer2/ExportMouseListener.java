package org.micromanager.ndviewer2;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.BiConsumer;

/**
 * Temporary canvas mouse listener used during export mode to let the user
 * draw a selection rectangle. Fields are volatile so the overlay thread
 * reads fresh values.
 *
 * <p>On mouse release, {@code onRelease} is always called so the controller can
 * restore the normal viewer state. {@code onRoiSelected} is only called when
 * the drag is large enough to define a meaningful ROI.
 */
class ExportMouseListener implements NDViewer2CanvasMouseListenerInterface {

   private static final int MIN_DRAG_PIXELS = 5;

   volatile Point mouseDragStartPoint_;
   volatile Point currentMouseLocation_;
   private final NDViewer2API viewer_;
   private final Runnable onRelease_;
   private final BiConsumer<Point, Point> onRoiSelected_;
   private boolean released_ = false;

   ExportMouseListener(NDViewer2API viewer,
                       Runnable onRelease,
                       BiConsumer<Point, Point> onRoiSelected) {
      viewer_ = viewer;
      onRelease_ = onRelease;
      onRoiSelected_ = onRoiSelected;
   }

   @Override
   public void mousePressed(MouseEvent e) {
      mouseDragStartPoint_ = e.getPoint();
      currentMouseLocation_ = e.getPoint();
      viewer_.redrawOverlay();
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      viewer_.redrawOverlay();
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      if (released_) {
         return;
      }
      released_ = true;
      Point start = mouseDragStartPoint_;
      Point end = e.getPoint();
      currentMouseLocation_ = end;
      if (start != null
              && (Math.abs(end.x - start.x) >= MIN_DRAG_PIXELS
                  || Math.abs(end.y - start.y) >= MIN_DRAG_PIXELS)) {
         onRoiSelected_.accept(start, end);
      }
      onRelease_.run();
   }

   @Override
   public void mouseMoved(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      viewer_.redrawOverlay();
   }

   @Override
   public void mouseExited(MouseEvent e) {
      currentMouseLocation_ = null;
      viewer_.redrawOverlay();
   }

   @Override
   public void mouseClicked(MouseEvent e) {}

   @Override
   public void mouseEntered(MouseEvent e) {}

   @Override
   public void mouseWheelMoved(MouseWheelEvent e) {}
}
