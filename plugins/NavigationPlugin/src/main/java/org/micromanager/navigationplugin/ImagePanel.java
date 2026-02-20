/**
 * ImagePanel - Custom JPanel for displaying reference image and handling interactions
 *
 * <p>Displays the reference image with overlays for calibration points and current
 * stage position. Handles mouse clicks for calibration and navigation.
 *
 * <p>lLICENSE: This file is distributed under the BSD license.
 */

package org.micromanager.navigationplugin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class ImagePanel extends JPanel {

   private final NavigationState state_;
   private Point2D.Double currentStagePosition_;
   private Rectangle imageDrawRect_;
   private ImageClickListener clickListener_;

   // Zoom / pan state
   private double zoomLevel_;             // 1.0 = fit-to-panel baseline
   private double panOffsetX_;            // pan in panel-pixel units
   private double panOffsetY_;
   private Point dragStart_;              // where mouse went down
   private double panOffsetXAtDragStart_;
   private double panOffsetYAtDragStart_;
   private boolean isDragging_;

   private static final int DRAG_THRESHOLD = 5;
   private static final double ZOOM_FACTOR = 1.15;
   private static final double ZOOM_MIN = 0.5;
   private static final double ZOOM_MAX = 32.0;

   public interface ImageClickListener {
      void onImageClicked(Point2D.Double imageCoord);
   }

   public ImagePanel(NavigationState state) {
      this.state_ = state;
      this.currentStagePosition_ = null;
      this.imageDrawRect_ = new Rectangle();
      this.zoomLevel_ = 1.0;
      this.panOffsetX_ = 0;
      this.panOffsetY_ = 0;
      this.isDragging_ = false;
      this.dragStart_ = null;

      setPreferredSize(new Dimension(800, 600));
      setBackground(Color.DARK_GRAY);

      // Unified handler (implements MouseListener + MouseMotionListener)
      MouseAdapter interactionHandler = new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e)  {
            handleMousePressed(e);
         }

         @Override
         public void mouseDragged(MouseEvent e)  {
            handleMouseDragged(e);
         }

         @Override
         public void mouseReleased(MouseEvent e) {
            handleMouseReleased(e);
         }

         @Override
         public void mouseEntered(MouseEvent e)  {
            updateCursor();
         }
      };
      addMouseListener(interactionHandler);
      addMouseMotionListener(interactionHandler);

      // Zoom on scroll
      addMouseWheelListener(new MouseAdapter() {
         @Override
         public void mouseWheelMoved(MouseWheelEvent e) {
            handleMouseWheel(e);
         }
      });

      // Reset zoom on resize (pan offset is in panel pixels, so reset on resize)
      addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            resetZoomAndPan();
         }
      });
   }

   public void setClickListener(ImageClickListener listener) {
      this.clickListener_ = listener;
   }

   public void setCurrentStagePosition(Point2D.Double stagePos) {
      this.currentStagePosition_ = stagePos;
      repaint();
   }

   private boolean isPanGesture(MouseEvent e) {
      return SwingUtilities.isRightMouseButton(e);
   }

   private void handleMousePressed(MouseEvent e) {
      dragStart_ = e.getPoint();
      panOffsetXAtDragStart_ = panOffsetX_;
      panOffsetYAtDragStart_ = panOffsetY_;
      isDragging_ = false;
   }

   private void handleMouseDragged(MouseEvent e) {
      if (dragStart_ == null || !isPanGesture(e)) {
         return;
      }
      int dx = e.getX() - dragStart_.x;
      int dy = e.getY() - dragStart_.y;
      if (!isDragging_) {
         if (Math.abs(dx) <= DRAG_THRESHOLD && Math.abs(dy) <= DRAG_THRESHOLD) {
            return;
         }
         isDragging_ = true;
      }
      panOffsetX_ = panOffsetXAtDragStart_ + dx;
      panOffsetY_ = panOffsetYAtDragStart_ + dy;
      repaint();
   }

   private void handleMouseReleased(MouseEvent e) {
      boolean wasDragging = isDragging_;
      isDragging_ = false;
      dragStart_  = null;
      if (wasDragging) {
         return;
      }
      if (!SwingUtilities.isLeftMouseButton(e)) {
         return;
      }

      boolean ctrl  = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK)  != 0;
      boolean shift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;

      if (e.getClickCount() == 2 && !ctrl && !shift) {
         resetZoomAndPan();
      } else if (ctrl && !shift) {
         handleNavigationOrCalibrationClick(e.getPoint());
      } else if (shift && !ctrl) {
         handleRemoveCalibrationPoint(e.getPoint());
      }
      // plain single left-click: no action
   }

   private void handleRemoveCalibrationPoint(Point pixelPoint) {
      BufferedImage image = state_.getReferenceImage();
      if (image == null) {
         return;
      }
      if (!imageDrawRect_.contains(pixelPoint)) {
         return;
      }
      Point2D.Double imageCoord = pixelToImageCoord(pixelPoint);
      if (imageCoord == null) {
         return;
      }
      state_.removeClosestCalibrationPoint(imageCoord);
      repaint();
   }

   private void handleMouseWheel(MouseWheelEvent e) {
      BufferedImage image = state_.getReferenceImage();
      if (image == null) {
         return;
      }
      int notches = e.getWheelRotation();
      double newZoom = zoomLevel_;
      if (notches < 0) {
         for (int i = 0; i < -notches; i++) {
            newZoom *= ZOOM_FACTOR;
         }
      } else {
         for (int i = 0; i < notches; i++)  {
            newZoom /= ZOOM_FACTOR;
         }
      }
      newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, newZoom));

      double fitScale = computeFitScale(image);
      double dxOld = image.getWidth()  * fitScale * zoomLevel_;
      double dyOld = image.getHeight() * fitScale * zoomLevel_;
      double dxNew = image.getWidth()  * fitScale * newZoom;
      double dyNew = image.getHeight() * fitScale * newZoom;
      double cxOld = (getWidth()  - dxOld) / 2.0;
      double cyOld = (getHeight() - dyOld) / 2.0;
      double cxNew = (getWidth()  - dxNew) / 2.0;
      double cyNew = (getHeight() - dyNew) / 2.0;
      double drawXOld = cxOld + panOffsetX_;
      double drawYOld = cyOld + panOffsetY_;
      double ratio = newZoom / zoomLevel_;
      panOffsetX_ = e.getX() - (e.getX() - drawXOld) * ratio - cxNew;
      panOffsetY_ = e.getY() - (e.getY() - drawYOld) * ratio - cyNew;
      zoomLevel_  = newZoom;
      repaint();
   }

   private void resetZoomAndPan() {
      zoomLevel_ = 1.0;
      panOffsetX_ = 0;
      panOffsetY_ = 0;
      repaint();
   }

   private void updateCursor() {
      if (isDragging_) {
         setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
         return;
      }
      if (state_.getReferenceImage() != null) {
         if (state_.isCalibrated()) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
         } else {
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
         }
      } else {
         setCursor(Cursor.getDefaultCursor());
      }
   }

   private void handleNavigationOrCalibrationClick(Point pixelPoint) {
      BufferedImage image = state_.getReferenceImage();
      if (image == null || clickListener_ == null) {
         return;
      }

      // Check if click is within image bounds
      if (!imageDrawRect_.contains(pixelPoint)) {
         return;
      }

      // Convert screen pixel to image coordinate
      Point2D.Double imageCoord = pixelToImageCoord(pixelPoint);
      if (imageCoord != null) {
         clickListener_.onImageClicked(imageCoord);
      }
   }

   /**
    * Convert panel pixel coordinates to image pixel coordinates.
    */
   private Point2D.Double pixelToImageCoord(Point pixelCoord) {
      BufferedImage image = state_.getReferenceImage();
      if (image == null || imageDrawRect_.width == 0 || imageDrawRect_.height == 0) {
         return null;
      }

      // Account for image scaling and centering in panel
      double scaleX = (double) image.getWidth() / imageDrawRect_.width;
      double scaleY = (double) image.getHeight() / imageDrawRect_.height;

      double imgX = (pixelCoord.x - imageDrawRect_.x) * scaleX;
      double imgY = (pixelCoord.y - imageDrawRect_.y) * scaleY;

      // Clamp to image bounds
      imgX = Math.max(0, Math.min(image.getWidth() - 1, imgX));
      imgY = Math.max(0, Math.min(image.getHeight() - 1, imgY));

      return new Point2D.Double(imgX, imgY);
   }

   /**
    * Convert image pixel coordinates to panel pixel coordinates.
    */
   private Point imageToPixelCoord(Point2D.Double imageCoord) {
      BufferedImage image = state_.getReferenceImage();
      if (image == null || imageDrawRect_.width == 0 || imageDrawRect_.height == 0) {
         return null;
      }

      double scaleX = (double) imageDrawRect_.width / image.getWidth();
      double scaleY = (double) imageDrawRect_.height / image.getHeight();

      int pixelX = imageDrawRect_.x + (int) (imageCoord.x * scaleX);
      int pixelY = imageDrawRect_.y + (int) (imageCoord.y * scaleY);

      return new Point(pixelX, pixelY);
   }

   @Override
   protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D) g;
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
               RenderingHints.VALUE_INTERPOLATION_BILINEAR);

      BufferedImage image = state_.getReferenceImage();
      if (image == null) {
         drawNoImageMessage(g2d);
         return;
      }

      // Calculate image draw rectangle (maintain aspect ratio, center in panel)
      calculateImageDrawRect(image);

      // Draw the scaled reference image
      g2d.drawImage(image,
            imageDrawRect_.x, imageDrawRect_.y,
            imageDrawRect_.width, imageDrawRect_.height,
            null);

      // Draw calibration point overlays
      drawCalibrationPoints(g2d);

      // Draw current stage position indicator (if calibrated)
      drawStagePositionIndicator(g2d);

      // Draw zoom level indicator
      drawZoomIndicator(g2d);

      updateCursor();
   }

   private void calculateImageDrawRect(BufferedImage image) {
      double fitScale = computeFitScale(image);
      double drawScale = fitScale * zoomLevel_;
      int drawWidth  = (int) (image.getWidth()  * drawScale);
      int drawHeight = (int) (image.getHeight() * drawScale);
      int drawX = (getWidth()  - drawWidth)  / 2 + (int) panOffsetX_;
      int drawY = (getHeight() - drawHeight) / 2 + (int) panOffsetY_;
      imageDrawRect_.setBounds(drawX, drawY, drawWidth, drawHeight);
   }

   private double computeFitScale(BufferedImage image) {
      double sx = (double) getWidth()  / image.getWidth();
      double sy = (double) getHeight() / image.getHeight();
      return Math.min(sx, sy) * 0.95;
   }

   private void drawZoomIndicator(Graphics2D g2d) {
      if (Math.abs(zoomLevel_ - 1.0) < 0.01) {
         return;
      }
      String text = (int) Math.round(zoomLevel_ * 100.0) + "%";
      g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
      int tw = g2d.getFontMetrics().stringWidth(text);
      int th = g2d.getFontMetrics().getAscent();
      final int margin = 8;
      final int px = 6;
      final int py = 4;
      int bx = getWidth() - tw - margin - px * 2;
      int by = getHeight() - th - margin - py * 2;
      g2d.setColor(new Color(0, 0, 0, 160));
      g2d.fillRoundRect(bx, by, tw + px * 2, th + py * 2, 6, 6);
      g2d.setColor(Color.WHITE);
      g2d.drawString(text, bx + px, by + py + th - 1);
   }

   private void drawNoImageMessage(Graphics2D g2d) {
      g2d.setColor(Color.LIGHT_GRAY);
      g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
      String message = "No reference image loaded";
      int msgWidth = g2d.getFontMetrics().stringWidth(message);
      g2d.drawString(message, (getWidth() - msgWidth) / 2, getHeight() / 2);
   }

   private void drawCalibrationPoints(Graphics2D g2d) {
      g2d.setColor(Color.GREEN);
      g2d.setStroke(new BasicStroke(2.0f));
      g2d.setFont(new Font("SansSerif", Font.BOLD, 14));

      for (CalibrationPoint cp : state_.getCalibrationPoints()) {
         Point pixelPt = imageToPixelCoord(cp.getImageCoord());
         if (pixelPt == null) {
            continue;
         }

         // Draw circle marker
         int radius = 8;
         g2d.drawOval(pixelPt.x - radius, pixelPt.y - radius, radius * 2, radius * 2);

         // Draw crosshair
         g2d.drawLine(pixelPt.x - 12, pixelPt.y, pixelPt.x + 12, pixelPt.y);
         g2d.drawLine(pixelPt.x, pixelPt.y - 12, pixelPt.x, pixelPt.y + 12);

         // Draw index number with background
         String label = String.valueOf(cp.getIndex());
         int labelWidth = g2d.getFontMetrics().stringWidth(label);
         int labelX = pixelPt.x + 15;
         int labelY = pixelPt.y - 10;

         // Draw label background
         g2d.setColor(new Color(0, 100, 0, 200));
         g2d.fillRect(labelX - 2, labelY - 12, labelWidth + 4, 16);

         // Draw label text
         g2d.setColor(Color.WHITE);
         g2d.drawString(label, labelX, labelY);

         // Reset color for next point
         g2d.setColor(Color.GREEN);
      }
   }

   private void drawStagePositionIndicator(Graphics2D g2d) {
      if (!state_.isCalibrated() || currentStagePosition_ == null) {
         return;
      }

      // Transform current stage position to image coordinates
      Point2D.Double imagePos = state_.stageToImage(currentStagePosition_);
      if (imagePos == null) {
         return;
      }

      Point pixelPt = imageToPixelCoord(imagePos);
      if (pixelPt == null) {
         return;
      }

      // Draw red crosshair for current position
      g2d.setColor(Color.RED);
      g2d.setStroke(new BasicStroke(2.0f));
      g2d.drawLine(pixelPt.x - 15, pixelPt.y, pixelPt.x + 15, pixelPt.y);
      g2d.drawLine(pixelPt.x, pixelPt.y - 15, pixelPt.x, pixelPt.y + 15);

      // Draw small circle at center
      g2d.drawOval(pixelPt.x - 3, pixelPt.y - 3, 6, 6);
   }
}
