/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.internal.gui;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.api.CanvasMouseListenerInterface;

/**
 *
 * @author henrypinkard
 */
public class CanvasMouseListener implements CanvasMouseListenerInterface {

   private static final int MOUSE_WHEEL_ZOOM_INTERVAL_MS = 100;

   private static final double ZOOM_FACTOR_MOUSE = 1.4;

   //all these are volatile because they are accessed by overlayer
   private volatile Point mouseDragStartPointLeft_, mouseDragStartPointRight_, currentMouseLocation_;
   private volatile long lastMouseWheelZoomTime_ = 0;
   private volatile boolean mouseDragging_ = false;

   private NDViewer display_;

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
   }

   @Override
   public void mouseExited(MouseEvent e) {
      currentMouseLocation_ = null;
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
