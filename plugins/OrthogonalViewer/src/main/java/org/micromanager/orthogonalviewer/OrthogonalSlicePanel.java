package org.micromanager.orthogonalviewer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/**
 * A panel that renders one orthogonal slice (XY, XZ, or YZ) with crosshair overlay.
 *
 * <p>The image is scaled to fill the panel while preserving aspect ratio (letterboxed).
 * Crosshair lines are drawn as semi-transparent cyan lines over the image.
 * Mouse clicks translate to image-pixel coordinates and are forwarded to the frame.</p>
 */
public class OrthogonalSlicePanel extends JPanel {

   private static final Color BACKGROUND = new Color(50, 50, 50);
   private static final Color CROSSHAIR_COLOR = new Color(0, 255, 255, 160);
   private static final BasicStroke CROSSHAIR_STROKE = new BasicStroke(1.0f);

   /**
    * Callback interface for mouse clicks on this panel.
    */
   public interface ClickListener {
      /**
       * Called when the user clicks on this panel.
       *
       * @param fracA horizontal fraction within the image [0,1)
       * @param fracB vertical fraction within the image [0,1)
       */
      void onClick(double fracA, double fracB);
   }

   private BufferedImage currentImage_;
   /** Crosshair position as fraction of image width (horizontal line). */
   private double fracA_ = 0.5;
   /** Crosshair position as fraction of image height (vertical line). */
   private double fracB_ = 0.5;

   private ClickListener clickListener_;

   public OrthogonalSlicePanel() {
      setBackground(BACKGROUND);
      setPreferredSize(new Dimension(256, 256));
      setMinimumSize(new Dimension(64, 64));

      addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            if (clickListener_ == null) {
               return;
            }
            int[] imageArea = computeImageArea();
            if (imageArea == null) {
               return;
            }
            int offsetX = imageArea[0];
            int offsetY = imageArea[1];
            int drawW = imageArea[2];
            int drawH = imageArea[3];
            if (drawW <= 0 || drawH <= 0) {
               return;
            }
            double fracA = (e.getX() - offsetX) / (double) drawW;
            double fracB = (e.getY() - offsetY) / (double) drawH;
            fracA = Math.max(0.0, Math.min(1.0, fracA));
            fracB = Math.max(0.0, Math.min(1.0, fracB));
            clickListener_.onClick(fracA, fracB);
         }
      });
   }

   public void setClickListener(ClickListener listener) {
      clickListener_ = listener;
   }

   public void setImage(BufferedImage image) {
      currentImage_ = image;
      repaint();
   }

   /**
    * Update crosshair fractions and repaint.
    *
    * @param fracA horizontal fraction [0,1] (vertical crosshair line position)
    * @param fracB vertical fraction [0,1] (horizontal crosshair line position)
    */
   public void setCrosshairFractions(double fracA, double fracB) {
      fracA_ = fracA;
      fracB_ = fracB;
      repaint();
   }

   @Override
   protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g;

      g2.setColor(BACKGROUND);
      g2.fillRect(0, 0, getWidth(), getHeight());

      if (currentImage_ == null) {
         return;
      }

      int[] imageArea = computeImageArea();
      if (imageArea == null) {
         return;
      }
      int offsetX = imageArea[0];
      int offsetY = imageArea[1];
      int drawW = imageArea[2];
      int drawH = imageArea[3];

      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2.drawImage(currentImage_, offsetX, offsetY, drawW, drawH, null);

      // Draw crosshair lines
      g2.setComposite(AlphaComposite.SrcOver);
      g2.setColor(CROSSHAIR_COLOR);
      g2.setStroke(CROSSHAIR_STROKE);

      int lineX = offsetX + (int) Math.round(fracA_ * drawW);
      int lineY = offsetY + (int) Math.round(fracB_ * drawH);

      g2.drawLine(lineX, 0, lineX, getHeight());
      g2.drawLine(0, lineY, getWidth(), lineY);
   }

   /**
    * Compute the image drawing area [offsetX, offsetY, drawW, drawH] within the panel,
    * preserving aspect ratio (letterboxed). Returns null if no image is set.
    */
   private int[] computeImageArea() {
      if (currentImage_ == null) {
         return null;
      }
      int iw = currentImage_.getWidth();
      int ih = currentImage_.getHeight();
      if (iw <= 0 || ih <= 0) {
         return null;
      }
      double scaleX = (double) getWidth() / iw;
      double scaleY = (double) getHeight() / ih;
      double scale = Math.min(scaleX, scaleY);
      int drawW = (int) Math.round(iw * scale);
      int drawH = (int) Math.round(ih * scale);
      int offsetX = (getWidth() - drawW) / 2;
      int offsetY = (getHeight() - drawH) / 2;
      return new int[]{offsetX, offsetY, drawW, drawH};
   }
}
