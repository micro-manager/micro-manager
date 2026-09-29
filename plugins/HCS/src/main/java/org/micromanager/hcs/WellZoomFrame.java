package org.micromanager.hcs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import mmcorej.DeviceType;
import org.micromanager.Studio;
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
    * @param plateGui Callback interface — used only to read the calibration offset.
    * @param studio   Micro-Manager Studio handle.
    */
   public WellZoomFrame(SBSPlate plate, ParentPlateGUI plateGui, Studio studio) {
      super("Well Zoom");
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      setSize(420, 420);
      java.net.URL iconUrl = getClass().getResource("/org/micromanager/icons/microscope.gif");
      if (iconUrl != null) {
         setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
      }
      WindowPositioning.setUpBoundsMemory(this, WellZoomFrame.class, null);

      panel_ = new WellZoomPanel(plate, plateGui, studio);
      add(panel_);

      addWindowListener(new java.awt.event.WindowAdapter() {
         @Override
         public void windowClosed(java.awt.event.WindowEvent e) {
            panel_.executor_.shutdown();
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

   /**
    * Called by SiteGenerator.updateStagePositions() on every stage update.
    *
    * @param x         Stage X in device coordinates.
    * @param y         Stage Y in device coordinates.
    * @param wellLabel Well the stage is currently in, or "" when outside all wells.
    */
   public void updateStagePosition(double x, double y, String wellLabel) {
      panel_.updateStagePosition(x, y, wellLabel);
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
      // Last well the stage was seen inside; null until first well entry.
      private WellPositionList currentWell_ = null;
      // Well label from the last updateStagePosition call; "" means outside.
      private String currentWellLabel_ = "";
      // Stage position in device coords, from the last updateStagePosition call.
      private Point2D.Double stageDevPos_ = new Point2D.Double(0, 0);
      private double cameraFovX_ = 0.0;
      private double cameraFovY_ = 0.0;

      // Coordinate transform parameters cached from the last paintComponent call,
      // used by the mouse handler to convert click position to plate coords.
      private double lastXFactor_ = 1.0;
      private double lastYFactor_ = 1.0;
      private int lastCx_ = 0;
      private int lastCy_ = 0;
      private double lastWellCx_ = 0.0;
      private double lastWellCy_ = 0.0;

      final ExecutorService executor_ = Executors.newSingleThreadExecutor();

      WellZoomPanel(SBSPlate plate, ParentPlateGUI plateGui, Studio studio) {
         plate_ = plate;
         plateGui_ = plateGui;
         studio_ = studio;
         updateCameraFov();

         addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
               if (e.isControlDown()) {
                  onCtrlClick(e.getX(), e.getY());
               }
            }
         });

         // Show a hint cursor so the user knows Ctrl-click is available.
         addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
               if ((e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0) {
                  setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
               } else {
                  setCursor(Cursor.getDefaultCursor());
               }
            }
         });
      }

      private void onCtrlClick(int pixX, int pixY) {
         if (currentWell_ == null) {
            return;
         }
         if (!plateGui_.isCalibratedXY()) {
            studio_.logs().showMessage("Calibrate XY first");
            return;
         }
         // Convert pixel → well-relative plate coords using the last paint transform.
         double relX = (pixX - lastCx_) / lastXFactor_;
         double relY = (pixY - lastCy_) / lastYFactor_;

         // Reject clicks outside the well boundary.
         if (!plate_.isPointWithinWell(relX, relY)) {
            return;
         }

         // Plate coords = well centre + relative offset.
         final double plateX = lastWellCx_ + relX;
         final double plateY = lastWellCy_ + relY;

         executor_.submit(() -> {
            try {
               final Point2D.Double target = plateGui_.applyOffset(
                     new Point2D.Double(plateX, plateY));
               studio_.getCMMCore().setXYPosition(target.x, target.y);
               studio_.getCMMCore().waitForDeviceType(DeviceType.XYStageDevice);
               if (plateGui_.useThreePtAF()
                     && plateGui_.getThreePointZPos(target.x, target.y) != null) {
                  boolean cfOn = studio_.getCMMCore().isContinuousFocusEnabled();
                  if (cfOn) {
                     studio_.getCMMCore().enableContinuousFocus(false);
                  }
                  try {
                     studio_.getCMMCore().setPosition(plateGui_.getZStageName(),
                           plateGui_.getThreePointZPos(target.x, target.y));
                  } finally {
                     if (cfOn) {
                        studio_.getCMMCore().enableContinuousFocus(true);
                     }
                  }
               }
            } catch (Exception ex) {
               studio_.logs().logError(ex, "HCS WellZoom: stage move failed");
            }
         });
      }

      void setSites(WellPositionList[] wells) {
         allWells_ = wells;
         if (currentWell_ != null) {
            String label = currentWell_.getLabel();
            currentWell_ = null;
            for (WellPositionList w : wells) {
               if (w.getLabel().equals(label)) {
                  currentWell_ = w;
                  break;
               }
            }
         }
         SwingUtilities.invokeLater(this::repaint);
      }

      void updateStagePosition(double x, double y, String wellLabel) {
         stageDevPos_ = new Point2D.Double(x, y);
         currentWellLabel_ = wellLabel == null ? "" : wellLabel;

         // Switch the displayed well when the stage enters a new one.
         if (!currentWellLabel_.isEmpty()
               && (currentWell_ == null
                    || !currentWell_.getLabel().equals(currentWellLabel_))) {
            for (WellPositionList w : allWells_) {
               if (w.getLabel().equals(currentWellLabel_)) {
                  currentWell_ = w;
                  break;
               }
            }
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
         if (xFactor < yFactor) {
            yFactor = xFactor;
         } else {
            xFactor = yFactor;
         }

         final int cx = w / 2;
         final int cy = h / 2;

         double wellCx;
         double wellCy;
         try {
            wellCx = plate_.getWellXUm(currentWell_.getLabel());
            wellCy = plate_.getWellYUm(currentWell_.getLabel());
         } catch (HCSException e) {
            return;
         }

         // Cache transform for use by the mouse handler.
         lastXFactor_ = xFactor;
         lastYFactor_ = yFactor;
         lastCx_ = cx;
         lastCy_ = cy;
         lastWellCx_ = wellCx;
         lastWellCy_ = wellCy;

         // ---- draw well outline ----
         int wellPxW = (int) (wellSizeX * xFactor);
         int wellPxH = (int) (wellSizeY * yFactor);
         int wellLeft = cx - wellPxW / 2;
         int wellTop  = cy - wellPxH / 2;

         g2.setColor(studio_.app().skin().getEnabledTextColor());
         g2.setStroke(new BasicStroke(1.5f));
         if (plate_.isWellCircular()) {
            g2.drawOval(wellLeft, wellTop, wellPxW, wellPxH);
         } else {
            g2.drawRect(wellLeft, wellTop, wellPxW, wellPxH);
         }

         // ---- compute site pixel positions ----
         // Sites and well centre are both in plate coords (µm from plate origin).
         int n = currentWell_.getSitePositions().getNumberOfPositions();
         int[] sxPx = new int[n];
         int[] syPx = new int[n];
         for (int i = 0; i < n; i++) {
            double sx = currentWell_.getSitePositions().getPosition(i).getX();
            double sy = currentWell_.getSitePositions().getPosition(i).getY();
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

               double dx = x2 - x1;
               double dy = y2 - y1;
               double len = Math.sqrt(dx * dx + dy * dy);
               if (len > 8) {
                  double ux = dx / len;
                  double uy = dy / len;
                  double mx = (x1 + x2) / 2.0;
                  double my = (y1 + y2) / 2.0;
                  double arrowSize = 5.0;
                  double tx = mx + ux * arrowSize;
                  double ty = my + uy * arrowSize;
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

         // ---- draw stage pointer and well label ----
         // currentWellLabel_ is set authoritatively by SiteGenerator (which
         // gets it from PlatePanel after offset correction). Empty = outside.
         boolean stageInWell = !currentWellLabel_.isEmpty()
               && currentWellLabel_.equals(currentWell_.getLabel());

         if (stageInWell) {
            // Convert device → plate coords using the calibration offset.
            Point2D.Double offset = plateGui_.getOffset();
            double stagePlateX = stageDevPos_.x - offset.x;
            double stagePlateY = stageDevPos_.y - offset.y;
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
            g2.setColor(Color.ORANGE);
            g2.setFont(new Font("Helvetica", Font.PLAIN, 11));
            g2.drawString("(stage outside well)", MARGIN + 4, h - MARGIN / 2);
         }

         g2.setColor(studio_.app().skin().getEnabledTextColor());
         g2.setFont(new Font("Helvetica", Font.BOLD, 14));
         g2.drawString(stageInWell ? currentWell_.getLabel() : "--", MARGIN + 4, MARGIN + 16);
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
