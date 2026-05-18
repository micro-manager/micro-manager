package org.micromanager.hcs;

import com.google.common.eventbus.Subscribe;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.micromanager.Studio;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * A resizable window showing a magnified view of a single well, including
 * imaging sites, the stage trajectory, the current stage position, and the
 * well label.
 */
public class WellZoomFrame extends JFrame {
   private static final long serialVersionUID = 1L;

   private final WellZoomPanel panel_;

   /**
    * Creates the Well Zoom window.
    *
    * @param plate    The plate model (mutated in-place on format changes).
    * @param plateGui Callback interface shared with PlatePanel.
    * @param studio   Micro-Manager Studio handle.
    */
   public WellZoomFrame(SBSPlate plate, ParentPlateGUI plateGui, Studio studio) {
      super("Well Zoom");
      setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
      setSize(420, 420);
      java.net.URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      WindowPositioning.setUpBoundsMemory(this, WellZoomFrame.class, null);

      panel_ = new WellZoomPanel(plate, plateGui, studio);
      add(panel_);

      studio.events().registerForEvents(panel_);
      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosing(java.awt.event.WindowEvent e) {
            studio.events().unregisterForEvents(panel_);
         }
      });
   }

   /**
    * Updates the site list after the plate format or site parameters change.
    * Finds the matching well for the currently-displayed label; clears the
    * display if the well no longer exists in the new layout.
    *
    * @param wells New array of WellPositionLists from PlatePanel.
    */
   public void setSites(WellPositionList[] wells) {
      panel_.setSites(wells);
   }

   // -------------------------------------------------------------------------

   /**
    * Custom panel that renders one well's sites, trajectory, and stage position.
    */
   private static class WellZoomPanel extends JPanel {
      private static final int MARGIN = 20;
      private static final int MIN_INDICATOR_PX = 4;

      private final SBSPlate plate_;
      private final ParentPlateGUI plateGui_;
      private final Studio studio_;

      private WellPositionList[] allWells_ = new WellPositionList[0];
      // last well seen; null only before first contact
      private WellPositionList currentWell_ = null;
      // whether stage is currently inside currentWell_
      private boolean stageInWell_ = false;
      private Point2D.Double stagePos_ = new Point2D.Double(0, 0);
      private double cameraFovX_ = 0.0;
      private double cameraFovY_ = 0.0;

      WellZoomPanel(SBSPlate plate, ParentPlateGUI plateGui, Studio studio) {
         plate_ = plate;
         plateGui_ = plateGui;
         studio_ = studio;
         updateCameraFov();
         // Initial stage position
         try {
            stagePos_ = studio_.getCMMCore().getXYStagePosition();
         } catch (Exception ignore) {
            // leave at (0,0)
         }
      }

      // -- called by WellZoomFrame --

      void setSites(WellPositionList[] wells) {
         allWells_ = wells;
         if (currentWell_ != null) {
            // Try to keep the same well displayed after a site/format update.
            String label = currentWell_.getLabel();
            currentWell_ = null;
            stageInWell_ = false;
            for (WellPositionList w : wells) {
               if (w.getLabel().equals(label)) {
                  currentWell_ = w;
                  break;
               }
            }
         }
         SwingUtilities.invokeLater(this::repaint);
      }

      // -- stage position event --

      @Subscribe
      public void xyStagePositionChanged(XYStagePositionChangedEvent ev) {
         if (!plateGui_.isCalibratedXY()) {
            return;
         }
         stagePos_ = new Point2D.Double(ev.getXPos(), ev.getYPos());

         // Find which well the stage is currently in.
         Point2D.Double offset = plateGui_.getOffset();
         double plateX = stagePos_.x - offset.x;
         double plateY = stagePos_.y - offset.y;

         if (!plate_.isPointWithin(plateX, plateY)) {
            // Stage is off the plate entirely — keep last well visible but
            // mark as outside so the stage pointer is hidden.
            stageInWell_ = false;
         } else {
            String label = plate_.getWellLabel(plateX, plateY);
            // Switch displayed well when the label changes.
            if (currentWell_ == null || !currentWell_.getLabel().equals(label)) {
               WellPositionList found = null;
               for (WellPositionList w : allWells_) {
                  if (w.getLabel().equals(label)) {
                     found = w;
                     break;
                  }
               }
               if (found != null) {
                  currentWell_ = found;
               }
            }
            // Stage is in the grid area; check it's actually inside the well
            // boundary (not in the spacing gap between wells).
            stageInWell_ = currentWell_ != null
                  && plate_.getWellLabel(plateX, plateY).equals(currentWell_.getLabel());
         }
         SwingUtilities.invokeLater(this::repaint);
      }

      // -- painting --

      @Override
      protected void paintComponent(Graphics g) {
         super.paintComponent(g);
         Graphics2D g2 = (Graphics2D) g;
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         int w = getWidth();
         int h = getHeight();

         if (currentWell_ == null) {
            // No well seen yet — stage has never entered a well.
            String msg = "Move stage into a well";
            g2.setColor(studio_.app().skin().getEnabledTextColor());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(msg)) / 2;
            int ty = h / 2;
            g2.drawString(msg, tx, ty);
            return;
         }

         // ---- coordinate transform ----
         int drawW = w - 2 * MARGIN;
         int drawH = h - 2 * MARGIN;
         if (drawW <= 0 || drawH <= 0) {
            return;
         }
         double wellSizeX = plate_.getWellSizeX();
         double wellSizeY = plate_.getWellSizeY();
         if (wellSizeX <= 0 || wellSizeY <= 0) {
            return;
         }

         double xFactor = drawW / wellSizeX;
         double yFactor = drawH / wellSizeY;
         // Lock aspect ratio (match smallest scale).
         if (xFactor < yFactor) {
            yFactor = xFactor;
         } else {
            xFactor = yFactor;
         }

         // Panel centre — well centre maps here.
         int cx = w / 2;
         int cy = h / 2;

         // Well centre in plate (µm) coords — needed to convert stage pos.
         double wellCx;
         double wellCy;
         try {
            wellCx = plate_.getWellXUm(currentWell_.getLabel());
            wellCy = plate_.getWellYUm(currentWell_.getLabel());
         } catch (HCSException e) {
            // Shouldn't happen; well label came from the plate itself.
            return;
         }

         // ---- draw well outline ----
         int wellPxW = (int) (wellSizeX * xFactor);
         int wellPxH = (int) (wellSizeY * yFactor);
         int wellLeft = cx - wellPxW / 2;
         int wellTop  = cy - wellPxH / 2;

         Color outlineColor = studio_.app().skin().getEnabledTextColor();
         g2.setColor(outlineColor);
         g2.setStroke(new BasicStroke(1.5f));
         if (plate_.isWellCircular()) {
            g2.drawOval(wellLeft, wellTop, wellPxW, wellPxH);
         } else {
            g2.drawRect(wellLeft, wellTop, wellPxW, wellPxH);
         }

         // ---- compute site pixel positions ----
         // Sites in WellPositionList and well centre from getWellXUm/YUm are
         // both in plate coordinates (µm from plate origin, no offset applied).
         // Stage position is in device coordinates: stagePlate = stage - offset.
         final Point2D.Double offset = plateGui_.getOffset();

         int n = currentWell_.getSitePositions().getNumberOfPositions();
         int[] sxPx = new int[n];
         int[] syPx = new int[n];
         for (int i = 0; i < n; i++) {
            double sx = currentWell_.getSitePositions().getPosition(i).getX();
            double sy = currentWell_.getSitePositions().getPosition(i).getY();
            // sx, sy and wellCx, wellCy are all in plate coords — subtract directly.
            sxPx[i] = (int) ((sx - wellCx) * xFactor + cx + 0.5);
            syPx[i] = (int) ((sy - wellCy) * yFactor + cy + 0.5);
         }

         // ---- draw trajectory lines with direction arrows ----
         if (n > 1) {
            g2.setColor(new Color(120, 120, 120));
            g2.setStroke(new BasicStroke(1.0f));
            for (int i = 0; i < n - 1; i++) {
               double x1 = sxPx[i];
               double y1 = syPx[i];
               double x2 = sxPx[i + 1];
               double y2 = syPx[i + 1];
               g2.draw(new Line2D.Double(x1, y1, x2, y2));

               // Small arrowhead at the midpoint pointing toward x2,y2.
               double dx = x2 - x1;
               double dy = y2 - y1;
               double len = Math.sqrt(dx * dx + dy * dy);
               if (len > 8) {
                  double ux = dx / len;
                  double uy = dy / len;
                  double mx = (x1 + x2) / 2.0;
                  double my = (y1 + y2) / 2.0;
                  double arrowSize = 5.0;
                  // tip of arrowhead (slightly ahead of midpoint)
                  double tx = mx + ux * arrowSize;
                  double ty = my + uy * arrowSize;
                  // two base corners (perpendicular, behind midpoint)
                  double bx = mx - ux * arrowSize;
                  double by = my - uy * arrowSize;
                  int[] arrowX = {
                     (int) Math.round(tx),
                     (int) Math.round(bx - uy * arrowSize * 0.6),
                     (int) Math.round(bx + uy * arrowSize * 0.6)
                  };
                  int[] arrowY = {
                     (int) Math.round(ty),
                     (int) Math.round(by + ux * arrowSize * 0.6),
                     (int) Math.round(by - ux * arrowSize * 0.6)
                  };
                  g2.fillPolygon(arrowX, arrowY, 3);
               }
            }
         }

         // ---- draw site indicators ----
         updateCameraFov();
         int indW = Math.max(MIN_INDICATOR_PX, (int) (cameraFovX_ * xFactor + 0.5));
         int indH = Math.max(MIN_INDICATOR_PX, (int) (cameraFovY_ * yFactor + 0.5));
         g2.setColor(studio_.app().skin().getEnabledTextColor());
         g2.setStroke(new BasicStroke(1.0f));
         for (int i = 0; i < n; i++) {
            g2.drawRect(sxPx[i] - indW / 2, syPx[i] - indH / 2, indW, indH);
         }

         // ---- draw stage pointer ----
         if (plateGui_.isCalibratedXY()) {
            if (stageInWell_) {
               double stagePlateX = stagePos_.x - offset.x;
               double stagePlateY = stagePos_.y - offset.y;
               double stageRelX = stagePlateX - wellCx;
               double stageRelY = stagePlateY - wellCy;
               int spxX = (int) (stageRelX * xFactor + cx + 0.5);
               int spxY = (int) (stageRelY * yFactor + cy + 0.5);
               int spW = Math.max(MIN_INDICATOR_PX, (int) (cameraFovX_ * xFactor + 0.5));
               int spH = Math.max(MIN_INDICATOR_PX, (int) (cameraFovY_ * yFactor + 0.5));
               g2.setColor(Color.RED);
               g2.setStroke(new BasicStroke(1.5f));
               g2.fillRect(spxX - spW / 2, spxY - spH / 2, spW, spH);
            } else {
               // Stage is outside this well — show a small notice in the corner.
               g2.setColor(Color.ORANGE);
               g2.setFont(new Font("Helvetica", Font.PLAIN, 11));
               g2.drawString("(stage outside well)", MARGIN + 4, h - MARGIN / 2);
            }
         }

         // ---- draw well label ----
         g2.setColor(studio_.app().skin().getEnabledTextColor());
         g2.setFont(new Font("Helvetica", Font.BOLD, 14));
         String displayLabel = stageInWell_ ? currentWell_.getLabel() : "--";
         g2.drawString(displayLabel, MARGIN + 4, MARGIN + 16);
      }

      private void updateCameraFov() {
         long width  = studio_.core().getImageWidth();
         long height = studio_.core().getImageHeight();
         double pxSize = studio_.core().getPixelSizeUm();
         cameraFovX_ = pxSize * width;
         cameraFovY_ = pxSize * height;
      }
   }
}
