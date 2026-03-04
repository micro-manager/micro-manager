package org.micromanager.magellan.internal.explore.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.BiConsumer;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;
import org.micromanager.ndviewer.main.NDViewer;

/**
 * Temporary canvas mouse listener used during export mode to let the user
 * draw a selection rectangle. Fields are volatile so the overlayer thread
 * reads fresh values (same pattern as ExploreMouseListener).
 */
public class ExportMouseListener implements CanvasMouseListenerInterface {

   protected volatile Point mouseDragStartPoint_;
   protected volatile Point currentMouseLocation_;
   private final NDViewer viewer_;
   private final BiConsumer<Point, Point> onRoiSelected_;

   public ExportMouseListener(NDViewer viewer, BiConsumer<Point, Point> onRoiSelected) {
      viewer_ = viewer;
      onRoiSelected_ = onRoiSelected;
   }

   @Override
   public void mousePressed(MouseEvent e) {
      viewer_.getCanvasJPanel().requestFocusInWindow();
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
      Point start = mouseDragStartPoint_;
      Point end = e.getPoint();
      currentMouseLocation_ = end;
      if (start != null) {
         onRoiSelected_.accept(start, end);
      }
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
   public void mouseClicked(MouseEvent e) {
   }

   @Override
   public void mouseEntered(MouseEvent e) {
   }

   @Override
   public void mouseWheelMoved(MouseWheelEvent e) {
   }

}
