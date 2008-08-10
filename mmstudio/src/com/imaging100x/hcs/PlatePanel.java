package com.imaging100x.hcs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import javax.swing.JPanel;

import org.micromanager.api.ScriptInterface;
import org.micromanager.navigation.PositionList;
import org.micromanager.utils.MMScriptException;

import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;


public class PlatePanel extends JPanel {
   private static final long serialVersionUID = 1L;
   // graphic parameters
   private final int xMargin_ = 30;
   private final int yMargin_ = 30;
   private final int fontSizePt_ = 12;

   private SBSPlate plate_;
   private WellPositionList[] wells_;
   private WellBox[] wellBoxes_;
   private Rectangle activeRect_;
   private Rectangle stagePointer_;
   public enum Tool {SELECT, MOVE}
   private Tool mode_;
   private ScriptInterface app_;
   private boolean lockAspect_;
   private ParentPlateGUI gui_;

   public static Color LIGHT_YELLOW = new Color(255,255,145);
   public static Color LIGHT_ORANGE = new Color(255,176,138);
   private DrawingParams drawingParams_;

   private class WellBox {
      public String label;
      public Color color;
      public Color activeColor;
      public Rectangle wellBoundingRect;
      public Rectangle wellRect;
      public Rectangle siteRect;
      public boolean circular;
      public boolean selected;
      public boolean active;

      private DrawingParams params_;
      private PositionList sites_;

      public WellBox(PositionList pl) {
         label = new String("undef");
         color = LIGHT_YELLOW;
         activeColor = LIGHT_ORANGE;
         wellBoundingRect = new Rectangle(0, 0, 100, 100);
         wellRect = new Rectangle(10, 10, 80, 80);
         siteRect = new Rectangle(4, 4);
         selected = false;
         active = false;
         params_ = new DrawingParams();
         sites_ = pl;
         circular = false;
      }

      public void draw(Graphics2D g, DrawingParams dp) {
         params_ = dp;
         draw(g);
      }

      public void dump() {
         System.out.println(label + ":" + wellBoundingRect.x + "," + wellBoundingRect.y);
      }

      public void draw(Graphics2D g) {
         Paint oldPaint = g.getPaint();
         Stroke oldStroke = g.getStroke();

         Color c = color;
         if (active)
            c = activeColor;

         if (selected)
            g.setPaint(c.darker());
         else
            g.setPaint(c);

         g.setStroke(new BasicStroke((float)0));
         Rectangle r = new Rectangle(wellBoundingRect);
         r.grow(-1, -1);
         g.fill(r);

         g.setPaint(Color.black);
         g.setStroke(new BasicStroke((float)1));
         if (circular)
            g.drawOval(wellRect.x, wellRect.y, wellRect.width, wellRect.height);
         else
            g.draw(wellRect);            

         // draw sites
         int siteOffsetX = siteRect.width / 2;
         int siteOffsetY = siteRect.height / 2;

         for (int j=0; j<sites_.getNumberOfPositions(); j++) {
            siteRect.x = (int)(sites_.getPosition(j).getX() * params_.xFactor + params_.xTopLeft - siteOffsetX + 0.5);
            siteRect.y = (int)(sites_.getPosition(j).getY() * params_.yFactor + params_.yTopLeft - siteOffsetY + 0.5);
            g.draw(siteRect);
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

   }

   public PlatePanel(SBSPlate plate, PositionList pl, ParentPlateGUI gui) {
      gui_ = gui;
      plate_ = plate;
      mode_ = Tool.SELECT;
      lockAspect_ = true;
      stagePointer_ = new Rectangle(3, 3);
      
      if (pl == null)
         wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME);
      else
         wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, pl);
      
      wellBoxes_ = new WellBox[plate_.getNumRows() * plate_.getNumColumns()];
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());

      addMouseListener(new MouseAdapter() {
         public void mouseClicked(final MouseEvent e) {
            onMouseClicked(e);
         }
      });

      addMouseMotionListener(new MouseMotionAdapter() {
         public void mouseMoved(final MouseEvent e) {
            onMouseMove(e);
         }
      });
   }

   protected void onMouseClicked(MouseEvent e) {
      if (mode_ == Tool.MOVE) {
         if (app_ == null)
            return;

         System.out.println("Mouse clicked: " + e.getX() + "," + e.getY());
         Point2D.Double pt = scalePixelToDevice(e.getX(), e.getY());

         try {
            app_.moveXYStage(pt.x, pt.y);
         } catch (MMScriptException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
         }
      }      
   }

   private void onMouseMove(MouseEvent e) {
      if (gui_ == null)
         return;

      Point2D.Double pt = scalePixelToDevice(e.getX(), e.getY());
      String well = plate_.getWellLabel(pt.x, pt.y);
      gui_.updatePointerXYPosition(pt.x, pt.y, well, "");

   }

   private Point2D.Double scalePixelToDevice(int x, int y) {
      int pixelPosX = y - activeRect_.y;
      int pixelPosY = x - activeRect_.x;
      return new Point2D.Double(pixelPosX/drawingParams_.xFactor, pixelPosY/drawingParams_.yFactor);
   }

   public void setTool(Tool t) {
      mode_ = t;
   }

   Tool getTool() {
      return mode_;
   }

   public void paintComponent(Graphics g) {

      super.paintComponent(g); // JPanel draws background
      Graphics2D  g2d = (Graphics2D) g;

      // get drawing rectangle
      Rectangle box = getBounds();

      // shrink drawing area by the margin amount
      box.x = (int)xMargin_;
      box.y = (int)yMargin_;
      box.height -= 2*yMargin_;
      box.width -= 2*xMargin_;
      activeRect_ = box;

      // save current settings
      Color oldColor = g2d.getColor();      
      Paint oldPaint = g2d.getPaint();
      Stroke oldStroke = g2d.getStroke();

      g2d.setPaint(Color.black);
      g2d.setStroke(new BasicStroke((float)1));

      // draw active area box
      g2d.draw(box);

      // draw content
      drawLabels(g2d, box);
      drawWells(g2d, box);
      drawGrid(g2d, box);

      // restore settings
      g2d.setPaint(oldPaint);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
   }

   private void drawWells(Graphics2D g, Rectangle box) {

      System.out.println("drawWells()");
      // calculate drawing parameters based on the active area
      DrawingParams dp = new DrawingParams();
      dp.xFactor = box.getWidth()/plate_.getXSize();
      dp.yFactor = box.getHeight()/plate_.getYSize();
      if (lockAspect_) {
         if (dp.xFactor < dp.yFactor)
            dp.yFactor = dp.xFactor;
         else
            dp.xFactor = dp.yFactor;
      }

      dp.xOffset = plate_.getTopLeftX() * dp.xFactor;
      dp.yOffset = plate_.getTopLeftY() * dp.yFactor;
      dp.xTopLeft = box.x;
      dp.yTopLeft = box.y;

      double wellX = plate_.getWellSpacingX() * dp.xFactor;
      double wellY = plate_.getWellSpacingY() * dp.yFactor;
      double wellInsideX = plate_.getWellSizeX() * dp.xFactor;
      double wellInsideY = plate_.getWellSizeY() * dp.yFactor;
      double wellOffsetX = (plate_.getWellSpacingX() - plate_.getWellSizeX()) / 2.0 * dp.xFactor;
      double wellOffsetY = (plate_.getWellSpacingY() - plate_.getWellSizeY()) / 2.0 * dp.yFactor;

      for (int i=0; i<wells_.length; i++) {
         WellBox wb = wellBoxes_[i];
         wb.label = wells_[i].getLabel();
         wb.circular = plate_.isWellCircular();
         wb.wellBoundingRect.setBounds((int)(box.getX() + wells_[i].getColumn()*wellX + dp.xOffset + 0.5), (int)(box.getY() + wells_[i].getRow()*wellY + dp.yOffset + 0.5),
               (int)wellX, (int)wellY);
         wb.wellRect.setBounds((int)(box.getX() + wells_[i].getColumn()*wellX + dp.xOffset + wellOffsetX + 0.5),
               (int)(box.getY() + wells_[i].getRow()*wellY + dp.yOffset + wellOffsetY + 0.5), (int)wellInsideX, (int)wellInsideY);
         wb.draw(g, dp);
         wb.dump();
      }      
   }

   private void drawGrid(Graphics2D g, Rectangle box) {
      // calculate plate active area
      double xFact = box.getWidth()/plate_.getXSize();
      double yFact = box.getHeight()/plate_.getYSize();
      if (lockAspect_) {
         if (xFact < yFact)
            yFact = xFact;
         else
            xFact = yFact;
      }

      double xOffset = plate_.getTopLeftX() * xFact;
      double yOffset = plate_.getTopLeftY() * yFact;

      double wellX = plate_.getWellSpacingX() * xFact;
      double wellY = plate_.getWellSpacingY() * yFact;

      double xStartHor = box.getX() + xOffset;
      double xEndHor = box.getX() + plate_.getBottomRightX() * xFact;
      for (int i=0; i<= plate_.getNumRows(); i++) {
         double yStart = box.getY() + i*wellY + yOffset;
         double yEnd = yStart;
         Point2D.Double ptStart = new Point2D.Double(xStartHor, yStart);
         Point2D.Double ptEnd = new Point2D.Double(xEndHor, yEnd);
         g.draw(new Line2D.Double(ptStart, ptEnd));      

      }

      double yStartV = box.getY() + yOffset;
      double yEndV = box.getY() + plate_.getBottomRightY() * yFact;
      for (int i=0; i<= plate_.getNumColumns(); i++) {
         double xStart = box.getX() + i*wellX + xOffset;
         double xEnd = xStart;
         Point2D.Double ptStart = new Point2D.Double(xStart, yStartV);
         Point2D.Double ptEnd = new Point2D.Double(xEnd, yEndV);
         g.draw(new Line2D.Double(ptStart, ptEnd));      

      }
   }

   private void drawLabels(Graphics2D g, Rectangle box) {
      double xFact = box.getWidth()/plate_.getXSize();
      double yFact = box.getHeight()/plate_.getYSize();
      if (lockAspect_) {
         if (xFact < yFact)
            yFact = xFact;
         else
            xFact = yFact;
      }

      double xOffset = plate_.getTopLeftX() * xFact;
      double yOffset = plate_.getTopLeftY() * yFact;

      double wellX = plate_.getWellSpacingX() * xFact;
      double wellY = plate_.getWellSpacingY() * yFact;

      FontRenderContext frc = g.getFontRenderContext();
      Font f = new Font("Helvetica",Font.BOLD, fontSizePt_);
      //g.setFont(f);

      Rectangle labelBoxX = new Rectangle();
      labelBoxX.width = (int)(wellX + 0.5);
      labelBoxX.height = yMargin_;
      labelBoxX.y = yMargin_;;

      Rectangle labelBoxY = new Rectangle();
      labelBoxY.height = (int)(wellY + 0.5);
      labelBoxY.width = xMargin_;
      labelBoxY.x = 0;

      for (int i=0; i<plate_.getNumColumns(); i++) {
         labelBoxX.x = (int)(i*wellX + 0.5 + xMargin_ + xOffset);
         TextLayout tl = new TextLayout(plate_.getColumnLabel(i+1), f, frc);
         Rectangle2D b = tl.getBounds();
         Point loc = getLocation(labelBoxX, b.getBounds());
         tl.draw(g, loc.x, loc.y);
      }

      for (int i=0; i<plate_.getNumRows(); i++) {
         labelBoxY.y = (int)(i*wellY + 0.5 + yMargin_ + wellY + yOffset);
         TextLayout tl = new TextLayout(plate_.getRowLabel(i+1), f, frc);
         Rectangle2D b = tl.getBounds();
         Point loc = getLocation(labelBoxY, b.getBounds());
         tl.draw(g, loc.x, loc.y);
      }         
   }

   private Point getLocation(Rectangle labelBox, Rectangle textBounds) {
      int xoffset = (labelBox.width - textBounds.width)/2;
      int yoffset = (labelBox.height - textBounds.height)/2;
      return new Point(labelBox.x + xoffset, labelBox.y - yoffset);
   }

   public void refreshImagingSites(PositionList sites) {
      System.out.println("refreshSites()");
      activeRect_ = getBounds();
      // shrink drawing area by the margin amount
      activeRect_.x = (int)xMargin_;
      activeRect_.y = (int)yMargin_;
      activeRect_.height -= 2*yMargin_;
      activeRect_.width -= 2*xMargin_;

      wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, sites);

      wellBoxes_ = new WellBox[wells_.length];
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());  

      // calculate drawing parameters based on the active area

      drawingParams_ = new DrawingParams();
      drawingParams_.xFactor = activeRect_.getWidth()/plate_.getXSize();
      drawingParams_.yFactor = activeRect_.getHeight()/plate_.getYSize();
      if (lockAspect_) {
         if (drawingParams_.xFactor < drawingParams_.yFactor)
            drawingParams_.yFactor = drawingParams_.xFactor;
         else
            drawingParams_.xFactor = drawingParams_.yFactor;
      }

      drawingParams_.xOffset = plate_.getTopLeftX() * drawingParams_.xFactor;
      drawingParams_.yOffset = plate_.getTopLeftY() * drawingParams_.yFactor;
      drawingParams_.xTopLeft = activeRect_.x;
      drawingParams_.yTopLeft = activeRect_.y;

      double wellX = plate_.getWellSpacingX() * drawingParams_.xFactor;
      double wellY = plate_.getWellSpacingY() * drawingParams_.yFactor;
      double wellInsideX = plate_.getWellSizeX() * drawingParams_.xFactor;
      double wellInsideY = plate_.getWellSizeY() * drawingParams_.yFactor;
      double wellOffsetX = (plate_.getWellSpacingX() - plate_.getWellSizeX()) / 2.0 * drawingParams_.xFactor;
      double wellOffsetY = (plate_.getWellSpacingY() - plate_.getWellSizeY()) / 2.0 * drawingParams_.yFactor;

      for (int i=0; i<wells_.length; i++) {
         WellBox wb = wellBoxes_[i];
         wb.label = wells_[i].getLabel();
         wb.circular = plate_.isWellCircular();
         wb.wellBoundingRect.setBounds((int)(activeRect_.getX() + wells_[i].getColumn()*wellX + drawingParams_.xOffset + 0.5),
               (int)(activeRect_.getY() + wells_[i].getRow()*wellY + drawingParams_.yOffset + 0.5), (int)wellX, (int)wellY);
         wb.wellRect.setBounds((int)(activeRect_.getX() + wells_[i].getColumn()*wellX + drawingParams_.xOffset + wellOffsetX + 0.5),
               (int)(activeRect_.getY() + wells_[i].getRow()*wellY + drawingParams_.yOffset + wellOffsetY + 0.5), (int)wellInsideX, (int)wellInsideY);
         wb.dump();
      }
   }

   WellPositionList[] getWellPositions() {
      return wells_;
   }

   void selectWell(int row, int col, boolean sel) {
      int index = row*plate_.getNumColumns() + col;
      wellBoxes_[index].selected = sel;
      Graphics2D g = (Graphics2D) getGraphics();
      wellBoxes_[index].draw(g);
   }

   void clearSelection() {
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i].selected = false;
      repaint();
   }

   void activateWell(int row, int col, boolean act) {
      int index = row*plate_.getNumColumns() + col;
      wellBoxes_[index].active = act;
      Graphics2D g = (Graphics2D) getGraphics();
      wellBoxes_[index].draw(g);
   }

   void clearActive() {
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i].active = false;
      repaint();
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
   }

   public void setLockAspect(boolean state) {
      lockAspect_ = state;
   }
}
