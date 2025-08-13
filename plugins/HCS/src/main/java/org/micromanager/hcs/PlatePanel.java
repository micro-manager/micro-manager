package org.micromanager.hcs;

import com.google.common.eventbus.Subscribe;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import mmcorej.DeviceType;
import org.micromanager.PositionList;
import org.micromanager.Studio;
import org.micromanager.events.PixelSizeChangedEvent;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;


/**
 * Draws and handles UI of the plate depiction.
 */
public class PlatePanel extends JPanel {
   private static final long serialVersionUID = 1L;
   // graphic parameters
   private final int xMargin_ = 30;
   private final int yMargin_ = 30;
   private final int fontSizePt_ = 12;

   private final SBSPlate plate_;
   private WellPositionList[] wells_;
   private Hashtable<String, Integer> wellMap_;
   private WellBox[] wellBoxes_;
   private Rectangle activeRect_;
   private final Rectangle stagePointer_;
   private final Rectangle siteIndicator_;
   private Point2D.Double xyStagePos_;
   private double cameraXFieldOfView_;
   private double cameraYFieldOfView_;
   private ExecutorService executorService_;

   /**
    * Tool, either Select or Move.
    */
   public enum Tool {
      SELECT, MOVE
   }

   private Tool mode_;
   private Studio studio_;
   private boolean lockAspect_;
   private final ParentPlateGUI plateGui_;
   private Point anchor_;
   private Point previous_;

   public static Color LIGHT_YELLOW = new Color(255, 255, 145);
   public static Color LIGHT_GREEN = new Color(204, 224, 201);
   private DrawingParams drawingParams_;
   private double zStagePos_;

   private class WellBox {
      public String label;
      public Color color;
      public Color activeColor;
      public Rectangle wellBoundingRect;
      public Rectangle wellRect;
      public boolean circular;
      public boolean selected;
      public boolean active;

      private DrawingParams params_;
      private final PositionList sites_;

      public WellBox(PositionList pl) {
         label = "undef";
         color = LIGHT_GREEN;
         activeColor = LIGHT_YELLOW;
         wellBoundingRect = new Rectangle(0, 0, 100, 100);
         wellRect = new Rectangle(10, 10, 80, 80);
         selected = false;
         active = false;
         params_ = new DrawingParams();
         sites_ = pl;
         circular = false;
         anchor_ = new Point(0, 0);
         previous_ = new Point(0, 0);
         
      }



      @Override
      public String toString() {
         return label + ":" + wellBoundingRect.x + "," + wellBoundingRect.y;
      }

      public void draw(Graphics2D g, DrawingParams dp) {
         params_ = dp;
         draw(g);
      }

      public void draw(Graphics2D g) {
         final Paint oldPaint = g.getPaint();
         final Stroke oldStroke = g.getStroke();

         Color c = color;
         if (active) {
            c = activeColor;
         }
         if (selected) {
            g.setPaint(c.darker());
         } else {
            g.setPaint(c);
         }
         g.setStroke(new BasicStroke((float) 0));
         Rectangle r = new Rectangle(wellBoundingRect);
         r.grow(-1, -1);
         g.fill(r);

         g.setPaint(Color.black);
         g.setStroke(new BasicStroke((float) 1));
         if (circular) {
            g.drawOval(wellRect.x, wellRect.y, wellRect.width, wellRect.height);
         } else {
            g.draw(wellRect);
         }

         // draw sites
         int siteOffsetX = siteIndicator_.width / 2;
         int siteOffsetY = siteIndicator_.height / 2;

         for (int j = 0; j < sites_.getNumberOfPositions(); j++) {
            siteIndicator_.x = (int) (sites_.getPosition(j).getX()
                    * params_.xFactor + params_.xTopLeft - siteOffsetX + 0.5);
            siteIndicator_.y = (int) (sites_.getPosition(j).getY()
                    * params_.yFactor + params_.yTopLeft - siteOffsetY + 0.5);
            g.draw(siteIndicator_);
         }

         g.setPaint(oldPaint);
         g.setStroke(oldStroke);
      }
   }

   private class DrawingParams {
      public double xFactor = 1.0;
      public double yFactor = 1.0;
      public double xOffset = 0.0;
      public double yOffset = 0.0;
      public int xTopLeft = 0;
      public int yTopLeft = 0;

      @Override
      public String toString() {
         return "XF=" + xFactor + ",YF=" + yFactor;
      }
   }

   /**
    * Plate Panel constructor.
    *
    * @param plate Which plate to draw
    * @param pl PositionList
    * @param plateGUI plugin GUI
    * @param studio MM Studio application
    */
   public PlatePanel(SBSPlate plate, PositionList pl, ParentPlateGUI plateGUI, Studio studio) {
      plateGui_ = plateGUI;
      studio_ = studio;
      plate_ = plate;
      executorService_ = Executors.newFixedThreadPool(2);
      mode_ = PlatePanel.Tool.SELECT;
      lockAspect_ = true;
      long width = studio_.core().getImageWidth();
      long height = studio_.core().getImageHeight();
      cameraXFieldOfView_ = studio_.core().getPixelSizeUm() * width;
      cameraYFieldOfView_ = studio_.core().getPixelSizeUm() * height;
      stagePointer_ = new Rectangle(3, 3);
      siteIndicator_ = new Rectangle(4, 4);
      wellMap_ = new Hashtable<>();
      xyStagePos_ = new Point2D.Double(0.0, 0.0);
      zStagePos_ = 0.0;

      if (pl == null) {
         wells_ = plate_.generatePositions(plateGui_.getXYStageName());
      } else {
         wells_ = plate_.generatePositions(plateGui_.getXYStageName(), pl);
      }

      super.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(final MouseEvent e) {
            try {
               onMouseClicked(e);
            } catch (HCSException e1) {
               plateGui_.displayError(e1.getMessage());
            }
         }

         @Override
         public void mousePressed(final MouseEvent e) {
            onMousePressed(e);
         }

         @Override
         public void mouseReleased(final MouseEvent e) {
            onMouseReleased(e);
         }
      });

      super.addMouseMotionListener(new MouseMotionAdapter() {
         @Override
         public void mouseMoved(final MouseEvent e) {
            onMouseMove(e);
         }

         @Override
         public void mouseDragged(final MouseEvent e) {
            onMouseDragged(e);
         }
      });
      
      super.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(final ComponentEvent e) {
            rescale();
         }
      });
      
      rescale();
      wellBoxes_ = new WellBox[plate_.getNumRows() * plate_.getNumColumns()];
      wellMap_ = new Hashtable<>();
      for (int i = 0; i < wellBoxes_.length; i++) {
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());         
         wellMap_.put(getWellKey(wells_[i].getRow(), wells_[i].getColumn()), i);
      }

      studio_.events().registerForEvents(this);
   }

   private String getWellKey(int row, int column) {
      return row + "-" + column;
   }

   protected void onMouseClicked(MouseEvent e) throws HCSException {
      final Point2D.Double pt = scalePixelToDevice(e.getX(), e.getY());
      String well = plate_.getWellLabel(pt.x, pt.y);
      if (mode_ == Tool.MOVE) {
         if (studio_ == null) {
            return;
         }
         if (!plate_.isPointWithin(pt.x, pt.y)) {
            return;
         }
         Future ft = executorService_.submit(new Runnable() {
            public void run() {
               try {
                  final Point2D.Double pt2 = plateGui_.applyOffset(pt);
                  studio_.getCMMCore().setXYPosition(pt2.x, pt2.y);
                  // wait for the stage to stop moving before updating the gui.
                  studio_.getCMMCore().waitForDeviceType(DeviceType.XYStageDevice);
                  if (plateGui_.useThreePtAF()
                          && plateGui_.getThreePointZPos(pt2.x, pt2.y) != null) {
                     // This is a bit of a hack, but this is essential for the Nikon PFS
                     boolean continuousFocusOn = studio_.getCMMCore().isContinuousFocusEnabled();
                     if (continuousFocusOn) {
                        studio_.getCMMCore().enableContinuousFocus(false);
                     }
                     studio_.getCMMCore().setPosition(plateGui_.getZStageName(),
                             plateGui_.getThreePointZPos(pt2.x, pt2.y));
                     if (continuousFocusOn) {
                        studio_.getCMMCore().enableContinuousFocus(true);
                     }
                  }
                  xyStagePos_ = studio_.getCMMCore().getXYStagePosition();
                  zStagePos_ = studio_.getCMMCore().getPosition(plateGui_.getZStageName());
                  SwingUtilities.invokeLater(new Runnable() {
                     public void run() {
                        plateGui_.updateStagePositions(xyStagePos_.x, xyStagePos_.y, zStagePos_,
                                well, "undefined");
                        try {
                           refreshStagePosition();
                        } catch (HCSException e1) {
                           studio_.logs().logError(e1.getMessage());
                        }
                        repaint();
                     }
                  });
               } catch (Exception e2) {
                  studio_.logs().logError(e2.getMessage());
               }
            }
         });
      } else {
         int row = plate_.getWellRow(pt.y);
         int col = plate_.getWellColumn(pt.x);
         if (row < 0 || col < 0 || row >= plate_.getNumRows() || col >= plate_.getNumColumns()) {
            // clicked outside of the active area
            if (!e.isControlDown()) {
               clearSelection();
            }
            return;
         }

         // clicked on one of the wells
         // Don't toggle selection when control is down, because the
         // mouseReleased handler does that for us.
         if (!e.isControlDown()) {
            // new selection
            clearSelection();
            selectWell(row, col, true);                 
         }
      }
         
   }
   
   protected void onMouseDragged(MouseEvent e) {
      drawSelRect(previous_);
      previous_ = e.getPoint();
      drawSelRect(e.getPoint());
   }

   protected void onMouseReleased(MouseEvent e) {
      if (mode_ == Tool.MOVE) {
         // Don't make any changes to the selection.
         return;
      }
      drawSelRect(previous_);
      Rectangle selRect = new Rectangle(anchor_.x, anchor_.y, 
              e.getX() -  anchor_.x, e.getY() - anchor_.y);
      // Don't allow one-dimensional selection rectangles.
      selRect.width = Math.max(1, selRect.width);
      selRect.height = Math.max(1, selRect.height);
      for (WellBox wellBox : wellBoxes_) {
         if (wellBox.wellRect.intersects(selRect)) {
            wellBox.selected = !wellBox.selected;
         }
      }
      repaint();
   }

   protected void onMousePressed(MouseEvent e) {
      if (!e.isControlDown()) {
         clearSelection();
      }
      anchor_ = e.getPoint();
      previous_ = e.getPoint();
   }
   
   private void drawSelRect(Point pt) {
      Graphics2D g = (Graphics2D) getGraphics();
      g.setXORMode(getBackground());
      g.drawRect(anchor_.x, anchor_.y, pt.x - anchor_.x, pt.y - anchor_.y);
      g.setPaintMode();
   }

   private void onMouseMove(MouseEvent e) {
      if (plateGui_ == null) {
         return;
      }
      Point2D.Double pt = scalePixelToDevice(e.getX(), e.getY());
      String well = plate_.getWellLabel(pt.x, pt.y);
      plateGui_.updatePointerXYPosition(pt.x, pt.y, well, "");
   }
   
   private Point2D.Double scalePixelToDevice(int x, int y) {
      // The point returned from this method will still need to have the offset applied
      // to it in order to map to the device coordinate system.
      int pixelPosY = y - activeRect_.y;
      int pixelPosX = x - activeRect_.x;
      return new Point2D.Double(
            pixelPosX / drawingParams_.xFactor, pixelPosY / drawingParams_.yFactor);
   }
   
   private Point scaleDeviceToPixel(double x, double y) {
      Point2D.Double offset = plateGui_.getOffset();
      int pixX = (int) ((x - offset.getX()) * drawingParams_.xFactor + activeRect_.x + 0.5);
      int pixY = (int) ((y - offset.getY()) * drawingParams_.yFactor + activeRect_.y + 0.5);
      
      return new Point(pixX, pixY);
   }

   /**
    * Sets the (ImageJ) tool to show.
    *
    * @param t New ImageJ tool
    */
   public void setTool(Tool t) {
      mode_ = t;
      if (mode_ == Tool.MOVE) {
         setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      } else {
         setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
   }

   public Tool getTool() {
      return mode_;
   }

   @Override
   public void paintComponent(Graphics g) {

      super.paintComponent(g); // JPanel draws background
      Graphics2D  g2d = (Graphics2D) g;
      rescale();

      // save current settings
      final Color oldColor = g2d.getColor();
      final Paint oldPaint = g2d.getPaint();
      final Stroke oldStroke = g2d.getStroke();

      g2d.setPaint(Color.black);
      g2d.setStroke(new BasicStroke((float) 1));

      // draw active area box
      g2d.draw(activeRect_);

      // draw content
      drawLabels(g2d, activeRect_);
      drawWells(g2d);
      drawGrid(g2d, activeRect_);
      
      // draw stage pointer
      drawStagePointer(g2d);
      
      // draw three point AF plane
      drawThreePointAF(g2d);
      
      // restore settings
      g2d.setPaint(oldPaint);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
   }

   private void rescale() {
      activeRect_ = getBounds();

      // shrink drawing area by the margin amount
      activeRect_.x = xMargin_;
      activeRect_.y = yMargin_;
      activeRect_.height -= 2 * yMargin_;
      activeRect_.width -= 2 * xMargin_;
      
      // calculate drawing parameters based on the active area
      drawingParams_ = new DrawingParams();
      
      drawingParams_.xFactor = activeRect_.getWidth() / plate_.getXSize();
      drawingParams_.yFactor = activeRect_.getHeight() / plate_.getYSize();
      if (lockAspect_) {
         if (drawingParams_.xFactor < drawingParams_.yFactor) {
            drawingParams_.yFactor = drawingParams_.xFactor;
         } else {
            drawingParams_.xFactor = drawingParams_.yFactor;
         }
      }

      drawingParams_.xOffset = plate_.getTopLeftX() * drawingParams_.xFactor;
      drawingParams_.yOffset = plate_.getTopLeftY() * drawingParams_.yFactor;
      drawingParams_.xTopLeft = activeRect_.x;
      drawingParams_.yTopLeft = activeRect_.y;

      int imageWidthPixels = (int) Math.round(
              cameraXFieldOfView_ * drawingParams_.xFactor);
      int imageHeightPixels = (int) Math.round(
               cameraYFieldOfView_ * drawingParams_.yFactor);
      stagePointer_.setSize(imageWidthPixels, imageHeightPixels);
      siteIndicator_.setSize(imageWidthPixels, imageHeightPixels);
   }

   private void drawWells(Graphics2D g) {

      double wellX = plate_.getWellSpacingX() * drawingParams_.xFactor;
      double wellY = plate_.getWellSpacingY() * drawingParams_.yFactor;
      double wellInsideX = plate_.getWellSizeX() * drawingParams_.xFactor;
      double wellInsideY = plate_.getWellSizeY() * drawingParams_.yFactor;
      double wellOffsetX =
            (plate_.getWellSpacingX() - plate_.getWellSizeX()) / 2.0 * drawingParams_.xFactor;
      double wellOffsetY =
            (plate_.getWellSpacingY() - plate_.getWellSizeY()) / 2.0 * drawingParams_.yFactor;

      g.setColor(Color.BLACK);
      for (int i = 0; i < wells_.length; i++) {
         WellBox wb = wellBoxes_[i];
         wb.label = wells_[i].getLabel();
         wb.circular = plate_.isWellCircular();
         wb.wellBoundingRect.setBounds(
               (int) (activeRect_.getX() + wells_[i].getColumn() * wellX
                     + drawingParams_.xOffset + 0.5),
               (int) (activeRect_.getY() + wells_[i].getRow() * wellY
                     + drawingParams_.yOffset + 0.5),
               (int) wellX,
               (int) wellY);
         wb.wellRect.setBounds(
               (int) (activeRect_.getX() + wells_[i].getColumn() * wellX
                     + drawingParams_.xOffset + wellOffsetX + 0.5),
               (int) (activeRect_.getY() + wells_[i].getRow() * wellY
                     + drawingParams_.yOffset + wellOffsetY + 0.5),
               (int) wellInsideX,
               (int) wellInsideY);
         wb.draw(g, drawingParams_);
      }      
   }

   private void drawGrid(Graphics2D g, Rectangle box) {
      // calculate plate active area
      double xFact = box.getWidth() / plate_.getXSize();
      double yFact = box.getHeight() / plate_.getYSize();
      if (lockAspect_) {
         if (xFact < yFact) {
            yFact = xFact;
         } else {
            xFact = yFact;
         }
      }

      double xOffset = plate_.getTopLeftX() * xFact;
      double yOffset = plate_.getTopLeftY() * yFact;

      double wellX = plate_.getWellSpacingX() * xFact;
      double wellY = plate_.getWellSpacingY() * yFact;

      double xStartHor = box.getX() + xOffset;
      double xEndHor = box.getX() + plate_.getBottomRightX() * xFact;
      for (int i = 0; i <= plate_.getNumRows(); i++) {
         double yStart = box.getY() + i * wellY + yOffset;
         double yEnd = yStart;
         Point2D.Double ptStart = new Point2D.Double(xStartHor, yStart);
         Point2D.Double ptEnd = new Point2D.Double(xEndHor, yEnd);
         g.draw(new Line2D.Double(ptStart, ptEnd));      
      }

      double yStartV = box.getY() + yOffset;
      double yEndV = box.getY() + plate_.getBottomRightY() * yFact;
      for (int i = 0; i <= plate_.getNumColumns(); i++) {
         double xStart = box.getX() + i * wellX + xOffset;
         double xEnd = xStart;
         Point2D.Double ptStart = new Point2D.Double(xStart, yStartV);
         Point2D.Double ptEnd = new Point2D.Double(xEnd, yEndV);
         g.draw(new Line2D.Double(ptStart, ptEnd));      
      }
   }

   private void drawLabels(Graphics2D g, Rectangle box) {
      double xFact = box.getWidth() / plate_.getXSize();
      double yFact = box.getHeight() / plate_.getYSize();
      if (lockAspect_) {
         if (xFact < yFact) {
            yFact = xFact;
         } else {
            xFact = yFact;
         }
      }

      final double xOffset = plate_.getTopLeftX() * xFact;
      final double yOffset = plate_.getTopLeftY() * yFact;
      double wellX = plate_.getWellSpacingX() * xFact;

      Rectangle labelBoxX = new Rectangle();
      labelBoxX.width = (int) (wellX + 0.5);
      labelBoxX.height = yMargin_;
      labelBoxX.y = yMargin_;

      double wellY = plate_.getWellSpacingY() * yFact;

      Rectangle labelBoxY = new Rectangle();
      labelBoxY.height = (int) (wellY + 0.5);
      labelBoxY.width = xMargin_;
      labelBoxY.x = 0;

      FontRenderContext frc = g.getFontRenderContext();
      Font f = new Font("Helvetica", Font.BOLD, fontSizePt_);
      g.setColor(studio_.app().skin().getEnabledTextColor());
      for (int i = 0; i < plate_.getNumColumns(); i++) {
         labelBoxX.x = (int) (i * wellX + 0.5 + xMargin_ + xOffset);
         TextLayout tl = new TextLayout(plate_.getColumnLabel(i + 1), f, frc);
         Rectangle2D b = tl.getBounds();
         Point loc = getLocation(labelBoxX, b.getBounds());
         tl.draw(g, loc.x, loc.y);
      }

      for (int i = 0; i < plate_.getNumRows(); i++) {
         labelBoxY.y = (int) (i * wellY + 0.5 + yMargin_ + wellY + yOffset);
         TextLayout tl = new TextLayout(plate_.getRowLabel(i + 1), f, frc);
         Rectangle2D b = tl.getBounds();
         Point loc = getLocation(labelBoxY, b.getBounds());
         tl.draw(g, loc.x, loc.y);
      }         
   }

   private Point getLocation(Rectangle labelBox, Rectangle textBounds) {
      int xoffset = (labelBox.width - textBounds.width) / 2;
      int yoffset = (labelBox.height - textBounds.height) / 2;
      return new Point(labelBox.x + xoffset, labelBox.y - yoffset);
   }

   private void drawStagePointer(Graphics2D g) {
      if (g == null) {
         return;
      }
      
      if (xyStagePos_ == null) {
         xyStagePos_ = new Point2D.Double(0.0, 0.0);
      }
      Point pt = scaleDeviceToPixel(xyStagePos_.x, xyStagePos_.y);
      pt.x = pt.x - stagePointer_.width / 2;
      pt.y = pt.y - stagePointer_.height / 2;

      stagePointer_.setLocation(pt);
      
      Paint oldPaint = g.getPaint();
      Stroke oldStroke = g.getStroke();

      g.setStroke(new BasicStroke((float) 1));
      g.setPaint(Color.RED);
      g.draw(stagePointer_);
      g.fillRect(stagePointer_.x, stagePointer_.y, stagePointer_.width, stagePointer_.height);

      g.setPaint(oldPaint);
      g.setStroke(oldStroke);    
   }
   
   private void drawThreePointAF(Graphics2D g) {
      if (g == null) {
         return;
      }
      
      if (!plateGui_.useThreePtAF()) {
         return;
      }
      
      PositionList plist = plateGui_.getThreePointList();
      if (plist == null || plist.getNumberOfPositions() != 3) {
         return;
      }
      
      Point pt1 = scaleDeviceToPixel(plist.getPosition(0).getX(), plist.getPosition(0).getY());
      Point pt2 = scaleDeviceToPixel(plist.getPosition(1).getX(), plist.getPosition(1).getY());
      Point pt3 = scaleDeviceToPixel(plist.getPosition(2).getX(), plist.getPosition(2).getY());
      
      g.drawLine(pt1.x, pt1.y, pt2.x, pt2.y);
      g.drawLine(pt2.x, pt2.y, pt3.x, pt3.y);
      g.drawLine(pt3.x, pt3.y, pt1.x, pt1.y);
      
   }

   /**
    * Updates display of image sites.
    *
    * @param sites Sites to be Displayed
    * @throws HCSException happens
    */
   public void refreshImagingSites(PositionList sites) throws HCSException {
      updateCameraFieldOfView();
      rescale();
      
      wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, sites);

      wellBoxes_ = new WellBox[wells_.length];
      wellMap_.clear();
      for (int i = 0; i < wellBoxes_.length; i++) {
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());
         wellMap_.put(getWellKey(wells_[i].getRow(), wells_[i].getColumn()), i);
      }

      double wellX = plate_.getWellSpacingX() * drawingParams_.xFactor;
      double wellY = plate_.getWellSpacingY() * drawingParams_.yFactor;
      double wellInsideX = plate_.getWellSizeX() * drawingParams_.xFactor;
      double wellInsideY = plate_.getWellSizeY() * drawingParams_.yFactor;
      double wellOffsetX =
            (plate_.getWellSpacingX() - plate_.getWellSizeX()) / 2.0 * drawingParams_.xFactor;
      double wellOffsetY =
            (plate_.getWellSpacingY() - plate_.getWellSizeY()) / 2.0 * drawingParams_.yFactor;

      for (int i = 0; i < wells_.length; i++) {
         WellBox wb = wellBoxes_[i];
         wb.label = wells_[i].getLabel();
         wb.circular = plate_.isWellCircular();
         wb.wellBoundingRect.setBounds(
               (int) (activeRect_.getX() + wells_[i].getColumn() * wellX
                     + drawingParams_.xOffset + 0.5),
               (int) (activeRect_.getY() + wells_[i].getRow() * wellY
                     + drawingParams_.yOffset + 0.5),
               (int) wellX,
               (int) wellY);
         wb.wellRect.setBounds(
               (int) (activeRect_.getX() + wells_[i].getColumn() * wellX
                     + drawingParams_.xOffset + wellOffsetX + 0.5),
               (int) (activeRect_.getY() + wells_[i].getRow() * wellY
                     + drawingParams_.yOffset + wellOffsetY + 0.5),
               (int) wellInsideX,
               (int) wellInsideY);
      }
      
      refreshStagePosition();
   }

   WellPositionList[] getWellPositions() {
      return wells_;
   }

   /**
    * Gets the selected wells in the form of WellPositionLists.
    *
    * @return WellPositionsLists for each of the wells selected in the UI.
    */
   public List<WellPositionList> getSelectedWellPositions() {
      List<WellPositionList> wal = new ArrayList<>();
      for (int i = 0; i < wells_.length; i++) {
         if (wellBoxes_[i].selected) {
            wal.add(wells_[i]);
         }
      }
      return wal;
   }

   /**
    * Returns Selected wells as WellPositionLists.
    *
    * @param wal List with WellspoitionLists of wells that are selected.
    */
   public void setSelectedWells(List<WellPositionList> wal) {
      for (WellPositionList wpl : wal) {
         selectWell(wpl.getRow(), wpl.getColumn(), true);
      }
   }
   
   void selectWell(int row, int col, boolean sel) {
      int index = wellMap_.get(getWellKey(row, col));
      wellBoxes_[index].selected = sel;
      Graphics2D g = (Graphics2D) getGraphics();
      wellBoxes_[index].draw(g);
   }
   
   void clearSelection() {
      for (WellBox wellBox : wellBoxes_) {
         wellBox.selected = false;
      }
      repaint();
   }

   void activateWell(int row, int col, boolean act) {
      int index = wellMap_.get(getWellKey(row, col));
      wellBoxes_[index].active = act;
      Graphics2D g = (Graphics2D) getGraphics();
      wellBoxes_[index].draw(g);
   }

   void clearActive() {
      for (WellBox wellBox : wellBoxes_) {
         wellBox.active = false;
      }
      repaint();
   }

   /**
    * Starts the plugin.
    *
    * @param app Micro-Manager Studio instance.
    * @throws HCSException happens
    */
   public void setApp(Studio app) throws HCSException {
      studio_ = app;
      xyStagePos_ = null;
      zStagePos_ = 0.0;
      if (studio_ == null) {
         return;
      }

      if (SwingUtilities.isEventDispatchThread()) {
         refreshStagePosition();
         repaint();
      } else {
         SwingUtilities.invokeLater(() -> {
            try {
               refreshStagePosition();
               repaint();
            } catch (HCSException e) {
               studio_.logs().logError(e, "HCS-PlatePanel");
            }
         });
      }
   }

   public void setLockAspect(boolean state) {
      lockAspect_ = state;
      rescale();
   }

   private Point2D.Double offsetCorrectedXYPosition(Point2D.Double xyStagePos) {
      Point2D.Double offset = plateGui_.getOffset();
      return new Point2D.Double(xyStagePos.x - offset.getX(), xyStagePos.y - offset.getY());
   }

   /**
    * Gets the current stage position from the hardware and draws the current position
    * on the plate picture.
    *
    * @throws HCSException thrown when the stage position cannot be retrieved.
    */
   public void refreshStagePosition() throws HCSException {
      if (studio_ != null) {
         try {
            xyStagePos_ = studio_.getCMMCore().getXYStagePosition();
            zStagePos_ = studio_.getCMMCore().getPosition(plateGui_.getZStageName());
         } catch (Exception e) {
            throw new HCSException(e);
         }
      } else {
         xyStagePos_ = new Point2D.Double(0.0, 0.0);
         zStagePos_ = 0.0;
      }
     
      Graphics2D g = (Graphics2D) getGraphics();
      drawStagePointer(g);
      Point2D.Double pt = offsetCorrectedXYPosition(xyStagePos_);
      String well = plate_.getWellLabel(pt.x, pt.y);
      plateGui_.updateStagePositions(xyStagePos_.x, xyStagePos_.y, zStagePos_, well, "undefined");
   }

   /**
    * Gets the current stage position from the hardware and draws the current position
    * on the plate picture.
    *
    * @throws HCSException thrown when the stage position cannot be retrieved.
    */
   @Subscribe
   public void xyStagePositionChanged(XYStagePositionChangedEvent xyStagePositionChangedEvent) {
      if (plateGui_.isCalibratedXY()) {
         final Graphics2D g = (Graphics2D) getGraphics();
         xyStagePos_.x = xyStagePositionChangedEvent.getXPos();
         xyStagePos_.y = xyStagePositionChangedEvent.getYPos();
         Point2D.Double pt = offsetCorrectedXYPosition(xyStagePos_);
         if (!plate_.isPointWithin(pt.x, pt.y)) {
            return;
         }
         String well = plate_.getWellLabel(pt.x, pt.y);
         if (SwingUtilities.isEventDispatchThread()) {
            plateGui_.updateStagePositions(xyStagePos_.x, xyStagePos_.y, zStagePos_,
                     well, "undefined");
            drawStagePointer(g);
            repaint();
         } else {
            SwingUtilities.invokeLater(() -> {
               plateGui_.updateStagePositions(xyStagePos_.x, xyStagePos_.y, zStagePos_,
                     well, "undefined");
               drawStagePointer(g);
               repaint();
            });
         }
      }
   }

   /**
    * Updates the stage positions when the config file gets reloaded.
    * Especially useful for systems that home.
    *
    * @param systemConfigurationLoadedEvent Event signaling the configuration reloaded.
    */
   @Subscribe
   public void systemConfigurationLoaded(
           SystemConfigurationLoadedEvent systemConfigurationLoadedEvent) {
      // assume that pixel size changed too
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(()
                  -> systemConfigurationLoaded(systemConfigurationLoadedEvent));
         return;
      }
      updateCameraFieldOfView();
      rescale();
      repaint();
      if (plateGui_.isCalibratedXY()) {
         try {
            refreshStagePosition();
         } catch (HCSException hcse) {
            studio_.logs().logError(hcse, "HCS-PlatePanel");
         }
      }
   }


   /**
    * Gets the current stage position from the hardware and draws the current position
    * on the plate picture.
    *
    * @throws HCSException thrown when the stage position cannot be retrieved.
    */
   @Subscribe
   public void stagePositionChanged(StagePositionChangedEvent stagePositionChangedEvent) {
      zStagePos_ = stagePositionChangedEvent.getPos();
      Point2D.Double pt = offsetCorrectedXYPosition(xyStagePos_);
      String well = plate_.getWellLabel(pt.x, pt.y);
      plateGui_.updateStagePositions(xyStagePos_.x, xyStagePos_.y, zStagePos_, well, "undefined");
   }

   /**
    * Updates the size of the position indicators based on the current camera field of
    * view.
    *
    * @throws HCSException thrown when the stage position cannot be retrieved.
    */
   @Subscribe
   public void pixelSizeChanged(PixelSizeChangedEvent psz) {
      updateCameraFieldOfView();
      SwingUtilities.invokeLater(() -> {
         rescale();
         repaint();
      });
   }

   private void updateCameraFieldOfView() {
      long width = studio_.core().getImageWidth();
      long height = studio_.core().getImageHeight();
      cameraXFieldOfView_ = studio_.core().getPixelSizeUm() * width;
      cameraYFieldOfView_ = studio_.core().getPixelSizeUm() * height;
   }

}