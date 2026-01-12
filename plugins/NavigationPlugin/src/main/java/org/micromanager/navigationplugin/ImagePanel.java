/**
 * ImagePanel - Custom JPanel for displaying reference image and handling interactions
 *
 * Displays the reference image with overlays for calibration points and current
 * stage position. Handles mouse clicks for calibration and navigation.
 *
 * LICENSE:      This file is distributed under the BSD license.
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

public class ImagePanel extends JPanel {

   private final NavigationState state_;
   private Point2D.Double currentStagePosition_;
   private Rectangle imageDrawRect_;
   private ImageClickListener clickListener_;

   public interface ImageClickListener {
      void onImageClicked(Point2D.Double imageCoord);
   }

   public ImagePanel(NavigationState state) {
      this.state_ = state;
      this.currentStagePosition_ = null;
      this.imageDrawRect_ = new Rectangle();

      setPreferredSize(new Dimension(800, 600));
      setBackground(Color.DARK_GRAY);

      // Add mouse listener for clicks
      addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            handleMouseClick(e.getPoint());
         }
      });

      // Add mouse motion listener for cursor changes
      addMouseListener(new MouseAdapter() {
         @Override
         public void mouseEntered(MouseEvent e) {
            updateCursor();
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

   private void updateCursor() {
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

   private void handleMouseClick(Point pixelPoint) {
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
    * Convert panel pixel coordinates to image pixel coordinates
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
    * Convert image pixel coordinates to panel pixel coordinates
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

      updateCursor();
   }

   private void calculateImageDrawRect(BufferedImage image) {
      int panelWidth = getWidth();
      int panelHeight = getHeight();
      int imageWidth = image.getWidth();
      int imageHeight = image.getHeight();

      // Calculate scale to fit image in panel while maintaining aspect ratio
      double scaleX = (double) panelWidth / imageWidth;
      double scaleY = (double) panelHeight / imageHeight;
      double scale = Math.min(scaleX, scaleY) * 0.95; // 95% to leave margin

      int drawWidth = (int) (imageWidth * scale);
      int drawHeight = (int) (imageHeight * scale);

      // Center the image
      int drawX = (panelWidth - drawWidth) / 2;
      int drawY = (panelHeight - drawHeight) / 2;

      imageDrawRect_.setBounds(drawX, drawY, drawWidth, drawHeight);
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
