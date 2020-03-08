/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.internal.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.SwingUtilities;
import org.micromanager.multiresviewer.NDViewer;
import static org.micromanager.ndviewer.internal.gui.DisplayWindow.EXPLORE;

/**
 *
 * @author henrypinkard
 */
public class CanvasMouseListener implements MouseListener, MouseMotionListener, MouseWheelListener {

   protected static final int MOUSE_WHEEL_ZOOM_INTERVAL_MS = 100;

   protected static final double ZOOM_FACTOR_MOUSE = 1.4;

   //all these are volatile because they are accessed by overlayer
   protected volatile Point mouseDragStartPointLeft_, mouseDragStartPointRight_, currentMouseLocation_;
   protected volatile long lastMouseWheelZoomTime_ = 0;
   protected volatile boolean mouseDragging_ = false;

   protected NDViewer display_;

   public CanvasMouseListener(NDViewer display) {
      display_ = display;
   }

   @Override
   public void mouseWheelMoved(MouseWheelEvent mwe) {
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastMouseWheelZoomTime_ > MOUSE_WHEEL_ZOOM_INTERVAL_MS) {
         lastMouseWheelZoomTime_ = currentTime;
         if (mwe.getWheelRotation() < 0) {
            display_.zoom(1 / ZOOM_FACTOR_MOUSE, currentMouseLocation_); // zoom in?
         } else if (mwe.getWheelRotation() > 0) {
            display_.zoom(ZOOM_FACTOR_MOUSE, currentMouseLocation_); //zoom out
         }
      }
   }

   @Override
   public void mouseDragged(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      mouseDraggedActions(e);
   }

   @Override
   public void mouseMoved(MouseEvent e) {
      currentMouseLocation_ = e.getPoint();
      if (display_.getOverlayMode() == EXPLORE) {
         display_.redrawOverlay();
      }
   }

   @Override
   public void mouseClicked(MouseEvent e) {
   }

   @Override
   public void mousePressed(MouseEvent e) {
      //to make zoom respond properly when switching between windows
      display_.getCanvas().getCanvas().requestFocusInWindow();
      if (SwingUtilities.isRightMouseButton(e)) {
         mouseDragStartPointRight_ = e.getPoint();
      } else if (SwingUtilities.isLeftMouseButton(e)) {
         mouseDragStartPointLeft_ = e.getPoint();
      }
      display_.redrawOverlay();
   }

   @Override
   public void mouseReleased(MouseEvent e) {
      mouseReleasedActions(e);
      mouseDragStartPointLeft_ = null;
      mouseDragStartPointRight_ = null;
      display_.redrawOverlay();
   }

   @Override
   public void mouseEntered(MouseEvent e) {
      if (display_.getOverlayMode() == EXPLORE) {
         display_.redrawOverlay();
      }
   }

   @Override
   public void mouseExited(MouseEvent e) {
      currentMouseLocation_ = null;
      if (display_.getOverlayMode() == EXPLORE) {
         display_.redrawOverlay();
      }
   }

   private void mouseReleasedActions(MouseEvent e) {
      mouseDragging_ = false;
      display_.redrawOverlay();
   }

   private void mouseDraggedActions(MouseEvent e) {
      Point currentPoint = e.getPoint();
      mouseDragging_ = true;
      if (SwingUtilities.isLeftMouseButton(e)) {
         //pan
         display_.pan(mouseDragStartPointLeft_.x - currentPoint.x, mouseDragStartPointLeft_.y - currentPoint.y);
         mouseDragStartPointLeft_ = currentPoint;
      } 
      display_.redrawOverlay();
   }

   Point getMouseDragStartPointLeft() {
      return mouseDragStartPointLeft_;
   }

   Point getCurrentMouseLocation() {
      return currentMouseLocation_;
   }

}
