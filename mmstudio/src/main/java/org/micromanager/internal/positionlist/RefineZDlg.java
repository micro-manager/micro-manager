///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu

//COPYRIGHT:    University of California, San Francisco, 2008 - 2024

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager.internal.positionlist;

import ij.process.ImageProcessor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.AutofocusPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.internal.positionlist.utils.TileCreator;
import org.micromanager.internal.positionlist.utils.TileGrid;
import org.micromanager.internal.positionlist.utils.ZGenerator;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 * "Refine Z" (mesh leveling) window for the Tile Creator.  Shows a graphical
 * overview of the tiles that will be imaged and lets the operator capture Z at
 * many interior points -- automatically (autofocus at N evenly distributed
 * tiles) or manually (ctrl-click a tile, focus by hand, Add).  The refined
 * points then drive Z interpolation when the full grid is generated and added
 * to the Stage Position List.
 *
 * @author Nico Stuurman
 */
public final class RefineZDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private static final int THUMB_PX = 64;
   private static final Color VISITED_COLOR = new Color(80, 140, 80);
   private static final Color PENDING_COLOR = new Color(200, 160, 40);

   private enum Mode { AUTOMATIC, MANUAL }

   /** One captured Z (and optional thumbnail) for a single tile. */
   private static final class RefinedPoint {
      final int row;
      final int tmpX;
      final double stageX;
      final double stageY;
      final Map<String, Double> z;
      BufferedImage thumb;
      Color marker;

      RefinedPoint(int row, int tmpX, double stageX, double stageY,
                   Map<String, Double> z) {
         this.row = row;
         this.tmpX = tmpX;
         this.stageX = stageX;
         this.stageY = stageY;
         this.z = z;
         this.marker = VISITED_COLOR;
      }
   }

   private final CMMCore core_;
   private final Studio studio_;
   private final PositionListDlg positionListDlg_;
   private final TileCreatorDlg tileCreatorDlg_;
   private final TileCreator tileCreator_;
   private final TileGrid grid_;
   private final double overlap_;
   private final TileCreator.OverlapUnitEnum overlapUnit_;
   private final double pixelSizeUm_;
   private final String xyStage_;
   private final StrVector zStages_;
   private final MultiStagePosition[] endPoints_;
   private final String labelPrefix_;

   // Refined points, keyed by row * nrImagesX + tmpX.
   private final Map<Long, RefinedPoint> refined_ = new LinkedHashMap<Long, RefinedPoint>();

   private Mode mode_ = Mode.AUTOMATIC;
   private int[] pendingCell_; // {tmpX, row} of last ctrl-clicked tile in MANUAL mode
   private AutoRefineWorker worker_;

   private final GridOverviewPanel overview_;
   private final JComboBox<ZGenerator.Type> methodCombo_;
   private final JRadioButton automaticButton_;
   private final JRadioButton manualButton_;
   private final JTextField nPointsField_;
   private final JButton startButton_;
   private final JButton cancelButton_;
   private final JButton addButton_;
   private final JButton doneButton_;
   private final JButton applyButton_;
   private final JProgressBar progressBar_;
   private final JLabel statusLabel_;

   /**
    * Constructs the Refine Z dialog.
    *
    * @param core the CMMCore
    * @param studio the Studio
    * @param positionListDlg the parent position list dialog
    * @param tileCreatorDlg the Tile Creator dialog that opened this window;
    *                       closed when positions are added
    * @param tileCreator the TileCreator used to build the final grid
    * @param grid the precomputed tile grid geometry
    * @param overlap overlap value
    * @param overlapUnit overlap unit
    * @param pixelSizeUm pixel size in microns
    * @param xyStage XY stage name
    * @param zStages checked Z stage names
    * @param endPoints corner positions (define the grid extent)
    * @param labelPrefix label prefix for generated positions
    */
   public RefineZDlg(CMMCore core, Studio studio, PositionListDlg positionListDlg,
                     TileCreatorDlg tileCreatorDlg, TileCreator tileCreator, TileGrid grid,
                     double overlap, TileCreator.OverlapUnitEnum overlapUnit,
                     double pixelSizeUm, String xyStage, StrVector zStages,
                     MultiStagePosition[] endPoints, String labelPrefix) {
      super(tileCreatorDlg, "Refine Z", false);
      core_ = core;
      studio_ = studio;
      positionListDlg_ = positionListDlg;
      tileCreatorDlg_ = tileCreatorDlg;
      tileCreator_ = tileCreator;
      grid_ = grid;
      overlap_ = overlap;
      overlapUnit_ = overlapUnit;
      pixelSizeUm_ = pixelSizeUm;
      xyStage_ = xyStage;
      zStages_ = zStages;
      endPoints_ = endPoints;
      labelPrefix_ = labelPrefix;

      setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      setLayout(new BorderLayout(5, 5));

      overview_ = new GridOverviewPanel();
      overview_.setPreferredSize(new Dimension(420, 420));
      overview_.setBorder(BorderFactory.createLineBorder(Color.GRAY));
      add(overview_, BorderLayout.CENTER);

      JPanel controls = new JPanel();
      controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
      controls.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

      // Interpolation method.
      JPanel methodPanel = new JPanel();
      methodPanel.add(new JLabel("Interpolation:"));
      methodCombo_ = new JComboBox<ZGenerator.Type>(new ZGenerator.Type[] {
            ZGenerator.Type.SHEPINTERPOLATE, ZGenerator.Type.AVERAGE});
      methodPanel.add(methodCombo_);
      controls.add(methodPanel);

      // Mode selection.
      automaticButton_ = new JRadioButton("Automatic", true);
      manualButton_ = new JRadioButton("Manual", false);
      ButtonGroup modeGroup = new ButtonGroup();
      modeGroup.add(automaticButton_);
      modeGroup.add(manualButton_);
      automaticButton_.addActionListener(e -> setMode(Mode.AUTOMATIC));
      manualButton_.addActionListener(e -> setMode(Mode.MANUAL));
      JPanel modePanel = new JPanel();
      modePanel.add(automaticButton_);
      modePanel.add(manualButton_);
      controls.add(modePanel);

      // Automatic controls.
      JPanel autoPanel = new JPanel();
      autoPanel.add(new JLabel("Points:"));
      nPointsField_ = new JTextField("5", 4);
      autoPanel.add(nPointsField_);
      startButton_ = new JButton("Start");
      startButton_.addActionListener(e -> startAutomatic());
      autoPanel.add(startButton_);
      cancelButton_ = new JButton("Cancel");
      cancelButton_.setEnabled(false);
      cancelButton_.addActionListener(e -> {
         if (worker_ != null) {
            worker_.cancel(true);
         }
      });
      autoPanel.add(cancelButton_);
      controls.add(autoPanel);

      // Manual controls.
      addButton_ = new JButton("Add");
      addButton_.setToolTipText("Record the current Z for the ctrl-clicked tile");
      addButton_.addActionListener(e -> manualAdd());
      doneButton_ = new JButton("Done");
      doneButton_.addActionListener(e -> setMode(Mode.AUTOMATIC));
      JPanel manualPanel = new JPanel();
      manualPanel.add(addButton_);
      manualPanel.add(doneButton_);
      controls.add(manualPanel);

      // Apply / progress / status.
      applyButton_ = new JButton("Add to position list");
      applyButton_.addActionListener(e -> apply());
      JPanel applyPanel = new JPanel();
      applyPanel.add(applyButton_);
      controls.add(applyPanel);

      progressBar_ = new JProgressBar(0, 100);
      progressBar_.setStringPainted(true);
      controls.add(progressBar_);

      statusLabel_ = new JLabel(" ");
      controls.add(statusLabel_);

      add(controls, BorderLayout.SOUTH);

      // Ctrl-click handling for manual mode.
      overview_.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            if (mode_ == Mode.MANUAL && SwingUtilities.isLeftMouseButton(e)
                  && e.isControlDown()) {
               handleManualClick(e.getPoint());
            }
         }
      });

      setMode(Mode.AUTOMATIC);
      pack();
      setLocationRelativeTo(tileCreatorDlg);
   }

   private void setMode(Mode mode) {
      mode_ = mode;
      automaticButton_.setSelected(mode == Mode.AUTOMATIC);
      manualButton_.setSelected(mode == Mode.MANUAL);
      boolean auto = mode == Mode.AUTOMATIC;
      boolean running = worker_ != null && !worker_.isDone();
      nPointsField_.setEnabled(auto);
      startButton_.setEnabled(auto && !running);
      addButton_.setEnabled(!auto);
      doneButton_.setEnabled(!auto);
      if (auto) {
         setStatus("Automatic: enter number of points and press Start.");
      } else {
         setStatus("Manual: ctrl-click a tile to move there, focus, then Add.");
      }
   }

   private void setStatus(String text) {
      statusLabel_.setText(text);
   }

   private long cellKey(int tmpX, int row) {
      return (long) row * grid_.nrImagesX + tmpX;
   }

   // -------------------------------------------------------------------------
   // Automatic mode
   // -------------------------------------------------------------------------

   private void startAutomatic() {
      int total = grid_.nrImagesX * grid_.nrImagesY;
      int n;
      try {
         n = (int) Math.round(NumberUtils.displayStringToDouble(nPointsField_.getText()));
      } catch (ParseException ex) {
         ReportingUtils.showError("Number of points must be a number", this);
         return;
      }
      if (n < 1) {
         n = 1;
      }
      if (n > total) {
         n = total;
      }
      nPointsField_.setText(Integer.toString(n));

      // Pick n tiles evenly across the flattened (row-major, logical-column) list.
      List<int[]> cells = new ArrayList<int[]>();
      boolean[] used = new boolean[total];
      for (int k = 0; k < n; k++) {
         int idx = (n == 1) ? 0 : (int) Math.round((double) k * (total - 1) / (n - 1));
         while (used[idx]) {
            idx = (idx + 1) % total;
         }
         used[idx] = true;
         int row = idx / grid_.nrImagesX;
         int tmpX = idx % grid_.nrImagesX;
         cells.add(new int[] {tmpX, row});
      }

      startButton_.setEnabled(false);
      cancelButton_.setEnabled(true);
      progressBar_.setValue(0);
      worker_ = new AutoRefineWorker(cells);
      worker_.execute();
   }

   private final class AutoRefineWorker extends SwingWorker<Void, RefinedPoint> {
      private final List<int[]> cells_;
      private final List<String> failures_ = new ArrayList<String>();

      AutoRefineWorker(List<int[]> cells) {
         cells_ = cells;
      }

      @Override
      protected Void doInBackground() {
         AutofocusPlugin af = studio_.getAutofocusManager().getAutofocusMethod();
         int done = 0;
         for (int[] cell : cells_) {
            if (isCancelled()) {
               break;
            }
            int tmpX = cell[0];
            int row = cell[1];
            double cx = grid_.tileCenterX(tmpX, row);
            double cy = grid_.tileCenterY(tmpX, row);
            try {
               core_.setXYPosition(xyStage_, cx, cy);
               core_.waitForDevice(xyStage_);
            } catch (Exception e) {
               failures_.add(tileName(tmpX, row) + " (move failed)");
               done++;
               setProgress(100 * done / cells_.size());
               continue;
            }
            if (af == null) {
               failures_.add(tileName(tmpX, row) + " (no autofocus device)");
               done++;
               setProgress(100 * done / cells_.size());
               continue;
            }
            try {
               af.fullFocus();
            } catch (Exception e) {
               failures_.add(tileName(tmpX, row));
               done++;
               setProgress(100 * done / cells_.size());
               continue;
            }
            RefinedPoint rp = readPoint(tmpX, row, cx, cy);
            if (rp == null) {
               failures_.add(tileName(tmpX, row) + " (Z read failed)");
            } else {
               rp.thumb = grabThumbnail();
               if (rp.thumb == null) {
                  rp.marker = VISITED_COLOR;
               }
               publish(rp);
            }
            done++;
            setProgress(100 * done / cells_.size());
         }
         return null;
      }

      @Override
      protected void process(List<RefinedPoint> chunks) {
         for (RefinedPoint rp : chunks) {
            refined_.put(cellKey(rp.tmpX, rp.row), rp);
         }
         overview_.repaint();
         setStatus("Refined " + refined_.size() + " point(s)...");
      }

      @Override
      protected void done() {
         cancelButton_.setEnabled(false);
         startButton_.setEnabled(mode_ == Mode.AUTOMATIC);
         progressBar_.setValue(100);
         overview_.repaint();
         if (isCancelled()) {
            setStatus("Cancelled. Refined " + refined_.size() + " point(s).");
            return;
         }
         if (!failures_.isEmpty()) {
            StringBuilder sb = new StringBuilder("Autofocus failed (skipped) at:\n");
            for (String f : failures_) {
               sb.append("  ").append(f).append('\n');
            }
            JOptionPane.showMessageDialog(RefineZDlg.this, sb.toString(),
                  "Refine Z", JOptionPane.WARNING_MESSAGE);
         }
         setStatus("Done. Refined " + refined_.size() + " point(s).");
      }
   }

   // -------------------------------------------------------------------------
   // Manual mode
   // -------------------------------------------------------------------------

   private void handleManualClick(Point p) {
      final int[] cell = overview_.screenToCell(p);
      if (cell == null) {
         return;
      }
      pendingCell_ = cell;
      final int tmpX = cell[0];
      final int row = cell[1];
      final double cx = grid_.tileCenterX(tmpX, row);
      final double cy = grid_.tileCenterY(tmpX, row);
      setStatus("Moving to " + tileName(tmpX, row) + "...");
      overview_.repaint();
      new Thread(() -> {
         try {
            core_.setXYPosition(xyStage_, cx, cy);
            core_.waitForDevice(xyStage_);
            SwingUtilities.invokeLater(() ->
                  setStatus("At " + tileName(tmpX, row) + ". Focus, then press Add."));
         } catch (Exception e) {
            SwingUtilities.invokeLater(() ->
                  ReportingUtils.showError(e, "Failed to move stage", RefineZDlg.this));
         }
      }, "RefineZ manual move").start();
   }

   private void manualAdd() {
      if (pendingCell_ == null) {
         ReportingUtils.showError("Ctrl-click a tile first", this);
         return;
      }
      final int tmpX = pendingCell_[0];
      final int row = pendingCell_[1];
      final double cx = grid_.tileCenterX(tmpX, row);
      final double cy = grid_.tileCenterY(tmpX, row);
      setStatus("Recording Z for " + tileName(tmpX, row) + "...");
      new Thread(() -> {
         final RefinedPoint rp = readPoint(tmpX, row, cx, cy);
         if (rp == null) {
            SwingUtilities.invokeLater(() ->
                  ReportingUtils.showError("Failed to read Z position", RefineZDlg.this));
            return;
         }
         rp.thumb = grabThumbnail();
         SwingUtilities.invokeLater(() -> {
            refined_.put(cellKey(tmpX, row), rp);
            overview_.repaint();
            setStatus("Recorded " + tileName(tmpX, row) + ". "
                  + refined_.size() + " point(s).");
         });
      }, "RefineZ manual add").start();
   }

   // -------------------------------------------------------------------------
   // Shared helpers (called off the EDT)
   // -------------------------------------------------------------------------

   /** Reads the current Z of every checked Z stage; returns null on failure. */
   private RefinedPoint readPoint(int tmpX, int row, double cx, double cy) {
      Map<String, Double> zVals = new LinkedHashMap<String, Double>();
      try {
         for (int a = 0; a < zStages_.size(); a++) {
            String z = zStages_.get(a);
            zVals.put(z, core_.getPosition(z));
         }
      } catch (Exception e) {
         ReportingUtils.logError(e, "RefineZ: failed to read Z position");
         return null;
      }
      return new RefinedPoint(row, tmpX, cx, cy, zVals);
   }

   /** Best-effort thumbnail from a snap; returns null on any failure. */
   private BufferedImage grabThumbnail() {
      try {
         List<Image> images = studio_.live().snap(false);
         if (images == null || images.isEmpty()) {
            return null;
         }
         Image img = images.get(0);
         ImageProcessor proc = ImageUtils.makeProcessor(img.getImageJPixelType(),
               img.getWidth(), img.getHeight(), img.getRawPixels());
         if (proc == null) {
            return null;
         }
         proc.setInterpolationMethod(ImageProcessor.BILINEAR);
         ImageProcessor scaled = proc.resize(THUMB_PX, THUMB_PX);
         return scaled.getBufferedImage();
      } catch (Exception e) {
         ReportingUtils.logError(e, "RefineZ: failed to grab thumbnail");
         return null;
      }
   }

   private String tileName(int tmpX, int row) {
      return "tile " + tmpX + "," + row;
   }

   // -------------------------------------------------------------------------
   // Apply: build refined position list, interpolate, add to position list
   // -------------------------------------------------------------------------

   private void apply() {
      if (refined_.isEmpty()) {
         ReportingUtils.showError("No refined points captured yet", this);
         return;
      }
      if (zStages_.size() > 0 && refined_.size() < 3) {
         int choice = JOptionPane.showConfirmDialog(this,
               "Only " + refined_.size() + " point(s) captured. At least 3 are "
                     + "recommended for a meaningful Z surface. Continue anyway?",
               "Refine Z", JOptionPane.YES_NO_OPTION);
         if (choice != JOptionPane.YES_OPTION) {
            return;
         }
      }

      PositionList refinedList = new PositionList();
      for (RefinedPoint rp : refined_.values()) {
         MultiStagePosition msp = new MultiStagePosition();
         msp.setDefaultXYStage(xyStage_);
         msp.add(StagePosition.create2D(xyStage_, rp.stageX, rp.stageY));
         if (zStages_.size() > 0) {
            msp.setDefaultZStage(zStages_.get(0));
            for (int a = 0; a < zStages_.size(); a++) {
               String z = zStages_.get(a);
               Double val = rp.z.get(z);
               if (val != null) {
                  msp.add(StagePosition.create1D(z, val));
               }
            }
         }
         refinedList.addPosition(msp);
      }

      ZGenerator zGen = null;
      if (zStages_.size() > 0) {
         ZGenerator.Type type = (ZGenerator.Type) methodCombo_.getSelectedItem();
         zGen = ZGenerator.create(type, refinedList);
      }

      PositionList full = tileCreator_.createTiles(overlap_, overlapUnit_, endPoints_,
            pixelSizeUm_, labelPrefix_, xyStage_, zStages_, zGen);
      if (full == null) {
         return;
      }
      for (MultiStagePosition msp : full.getPositions()) {
         positionListDlg_.addPosition(msp, msp.getLabel());
      }
      positionListDlg_.activateAxisTable(true);
      dispose();
      if (tileCreatorDlg_ != null) {
         tileCreatorDlg_.dispose();
      }
   }

   // -------------------------------------------------------------------------
   // Overview panel
   // -------------------------------------------------------------------------

   private final class GridOverviewPanel extends JPanel {
      private static final long serialVersionUID = 1L;
      private static final int MARGIN = 12;

      // Mapping from stage microns to screen pixels, recomputed each paint.
      private double scale_ = 1.0;
      private double originX_ = 0.0; // stage X mapped to screen x=MARGIN
      private double originY_ = 0.0; // stage Y mapped to screen y=MARGIN

      /**
       * Computes a uniform stage->screen transform that fits all tile cells
       * with a margin. Stage +Y is drawn downward (screen +y), matching the
       * convention used elsewhere in the position list UI.
       */
      private void computeTransform() {
         double minX = Double.POSITIVE_INFINITY;
         double minY = Double.POSITIVE_INFINITY;
         double maxX = Double.NEGATIVE_INFINITY;
         double maxY = Double.NEGATIVE_INFINITY;
         for (int y = 0; y < grid_.nrImagesY; y++) {
            for (int x = 0; x < grid_.nrImagesX; x++) {
               int tmpX = grid_.snakeColumn(x, y);
               double cx = grid_.tileCenterX(tmpX, y);
               double cy = grid_.tileCenterY(tmpX, y);
               minX = Math.min(minX, cx);
               maxX = Math.max(maxX, cx);
               minY = Math.min(minY, cy);
               maxY = Math.max(maxY, cy);
            }
         }
         // Pad by half a tile so cells are not clipped at the edges.
         double padX = Math.abs(grid_.tileSizeXUm) / 2.0 + 1.0;
         double padY = Math.abs(grid_.tileSizeYUm) / 2.0 + 1.0;
         minX -= padX;
         maxX += padX;
         minY -= padY;
         maxY += padY;
         double spanX = Math.max(maxX - minX, 1e-6);
         double spanY = Math.max(maxY - minY, 1e-6);
         double availW = Math.max(getWidth() - 2 * MARGIN, 1);
         double availH = Math.max(getHeight() - 2 * MARGIN, 1);
         scale_ = Math.min(availW / spanX, availH / spanY);
         originX_ = minX;
         originY_ = minY;
      }

      private int screenX(double stageX) {
         return MARGIN + (int) Math.round((stageX - originX_) * scale_);
      }

      private int screenY(double stageY) {
         return MARGIN + (int) Math.round((stageY - originY_) * scale_);
      }

      /**
       * Returns the {tmpX, row} of the tile whose cell contains screen point p,
       * or null if none.
       *
       * @param p screen point
       * @return logical cell coordinates {tmpX, row} or null
       */
      int[] screenToCell(Point p) {
         computeTransform();
         int cellW = Math.max((int) Math.round(Math.abs(grid_.tileSizeXUm) * scale_), 4);
         int cellH = Math.max((int) Math.round(Math.abs(grid_.tileSizeYUm) * scale_), 4);
         for (int y = 0; y < grid_.nrImagesY; y++) {
            for (int x = 0; x < grid_.nrImagesX; x++) {
               int tmpX = grid_.snakeColumn(x, y);
               int sx = screenX(grid_.tileCenterX(tmpX, y));
               int sy = screenY(grid_.tileCenterY(tmpX, y));
               Rectangle2D.Double r = new Rectangle2D.Double(
                     sx - cellW / 2.0, sy - cellH / 2.0, cellW, cellH);
               if (r.contains(p.x, p.y)) {
                  return new int[] {tmpX, y};
               }
            }
         }
         return null;
      }

      @Override
      protected void paintComponent(Graphics g) {
         super.paintComponent(g);
         if (grid_.nrImagesX < 1 || grid_.nrImagesY < 1) {
            return;
         }
         Graphics2D g2 = (Graphics2D) g.create();
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
               RenderingHints.VALUE_ANTIALIAS_ON);
         computeTransform();
         int cellW = Math.max((int) Math.round(Math.abs(grid_.tileSizeXUm) * scale_), 4);
         int cellH = Math.max((int) Math.round(Math.abs(grid_.tileSizeYUm) * scale_), 4);

         for (int y = 0; y < grid_.nrImagesY; y++) {
            for (int x = 0; x < grid_.nrImagesX; x++) {
               int tmpX = grid_.snakeColumn(x, y);
               int sx = screenX(grid_.tileCenterX(tmpX, y));
               int sy = screenY(grid_.tileCenterY(tmpX, y));
               int left = sx - cellW / 2;
               int top = sy - cellH / 2;

               RefinedPoint rp = refined_.get(cellKey(tmpX, y));
               boolean pending = pendingCell_ != null
                     && pendingCell_[0] == tmpX && pendingCell_[1] == y;
               if (rp != null && rp.thumb != null) {
                  g2.drawImage(rp.thumb, left, top, cellW, cellH, null);
               } else if (rp != null) {
                  g2.setColor(rp.marker);
                  g2.fillRect(left, top, cellW, cellH);
               } else if (pending) {
                  g2.setColor(PENDING_COLOR);
                  g2.fillRect(left, top, cellW, cellH);
               } else {
                  g2.setColor(getBackground().darker());
                  g2.fillRect(left, top, cellW, cellH);
               }
               g2.setColor(pending ? PENDING_COLOR : Color.DARK_GRAY);
               g2.drawRect(left, top, cellW, cellH);
            }
         }
         g2.dispose();
      }
   }
}
