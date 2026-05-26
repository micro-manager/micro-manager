package org.micromanager.orthogonalviewer;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JPanel;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.Overlay;

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

   // Overlay support — only set on the XY panel
   private List<Overlay> overlays_;
   private List<Image> overlayImages_;
   private Image primaryImage_;
   private DisplaySettings displaySettings_;

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

   /**
    * Convert a panel-relative mouse point to image fractions [fracA, fracB] in [0,1].
    * Returns null if the point is outside the image drawing area or no image is set.
    */
   public double[] toImageFraction(java.awt.Point p) {
      int[] area = computeImageArea();
      if (area == null) {
         return null;
      }
      int offsetX = area[0];
      int offsetY = area[1];
      int drawW = area[2];
      int drawH = area[3];
      if (drawW <= 0 || drawH <= 0) {
         return null;
      }
      double fracA = (p.x - offsetX) / (double) drawW;
      double fracB = (p.y - offsetY) / (double) drawH;
      if (fracA < 0.0 || fracA >= 1.0 || fracB < 0.0 || fracB >= 1.0) {
         return null;
      }
      return new double[]{fracA, fracB};
   }

   public void setOverlayContext(List<Overlay> overlays, List<Image> images,
                                 Image primaryImage, DisplaySettings settings) {
      overlays_ = overlays;
      overlayImages_ = images;
      primaryImage_ = primaryImage;
      displaySettings_ = settings;
   }

   public boolean hasOverlayContext() {
      return overlays_ != null;
   }

   public BufferedImage getCurrentImage() {
      return currentImage_;
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

      // Paint MM Inspector overlays (XY panel only — requires image coordinates)
      if (overlays_ != null && primaryImage_ != null && displaySettings_ != null) {
         int iw = currentImage_.getWidth();
         int ih = currentImage_.getHeight();
         List<Image> images = (overlayImages_ != null && !overlayImages_.isEmpty())
               ? overlayImages_ : java.util.Collections.singletonList(primaryImage_);
         Graphics2D og = (Graphics2D) g2.create();
         // Clip to the image area so overlays don't bleed into the grey border
         og.setClip(offsetX, offsetY, drawW, drawH);
         // Transform so overlays work in image-pixel coordinates.
         // screenRect must match the graphics context coordinate space (image pixels),
         // and imageViewPort is the visible portion of the image (the whole image here).
         og.translate(offsetX, offsetY);
         og.scale((double) drawW / iw, (double) drawH / ih);
         Rectangle screenRect = new Rectangle(0, 0, iw, ih);
         Rectangle2D.Float imageViewPort = new Rectangle2D.Float(0, 0, iw, ih);
         for (Overlay overlay : overlays_) {
            if (overlay.isVisible()) {
               try {
                  overlay.paintOverlay(og, screenRect, displaySettings_,
                        images, primaryImage_, imageViewPort);
               } catch (Exception ex) {
                  // ignore overlay paint errors
               }
            }
         }
         og.dispose();
      }
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
