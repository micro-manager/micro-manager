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
import javax.swing.JPanel;

import org.micromanager.navigation.PositionList;

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
   WellPositionList[] wells_;
   WellBox[] wellBoxes_;
   private Rectangle activeRect_;
   
   public static Color LIGHT_YELLOW = new Color(255,255,145);
   public static Color LIGHT_ORANGE = new Color(255,176,138);
   
   private class WellBox {
      public String label;
      public Color color;
      public Color activeColor;
      public Rectangle wellRect;
      public Rectangle siteRect;
      public boolean selected;
      public boolean active;
      
      private DrawingParams params_;
      private PositionList sites_;
      
      public WellBox(PositionList pl) {
         label = new String("undef");
         color = LIGHT_YELLOW;
         activeColor = LIGHT_ORANGE;
         wellRect = new Rectangle(0, 0, 100, 100);
         siteRect = new Rectangle(4, 4);
         selected = false;
         active = false;
         params_ = new DrawingParams();
         sites_ = pl;
      }
      
      public void draw(Graphics2D g, DrawingParams dp) {
         params_ = dp;
         draw(g);
      }
      
      public void dump() {
         System.out.println(label + ":" + wellRect.x + "," + wellRect.y);
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
         Rectangle r = new Rectangle(wellRect);
         r.grow(-1, -1);
         g.fill(r);
         
         // draw sites
         int siteOffsetX = siteRect.width / 2;
         int siteOffsetY = siteRect.height / 2;
         
         g.setPaint(Color.black);
         g.setStroke(new BasicStroke((float)1));
         
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
   
   public PlatePanel(SBSPlate plate) {
      plate_ = plate;
      wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME);
      wellBoxes_ = new WellBox[plate_.getNumRows() * plate_.getNumColumns()];
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());
   }
   public PlatePanel(SBSPlate plate, PositionList pl) {
      plate_ = plate;
      wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, pl);
      wellBoxes_ = new WellBox[plate_.getNumRows() * plate_.getNumColumns()];
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());      
   }

   public void paintComponent(Graphics g) {
      
      super.paintComponent(g); // JPanel draws background
      Graphics2D  g2d = (Graphics2D) g;
      
      // get drawing rectangle
      Rectangle box = getBounds();
      activeRect_ = box;
 
      // shrink drawing area by the margin amount
      box.x = (int)xMargin_;
      box.y = (int)yMargin_;
      box.height -= 2*yMargin_;
      box.width -= 2*xMargin_;
      
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
      //drawImagingSites(g2d, box);
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
      dp.xOffset = plate_.getTopLeftX() * dp.xFactor;
      dp.yOffset = plate_.getTopLeftY() * dp.yFactor;
      dp.xTopLeft = box.x;
      dp.yTopLeft = box.y;
      
      double wellX = (box.getWidth() - 2.0*dp.xOffset) / plate_.getNumColumns();
      double wellY = (box.getHeight() - 2.0*dp.yOffset) / plate_.getNumRows();
                  
      for (int i=0; i<wells_.length; i++) {
         WellBox wb = wellBoxes_[i];
         wb.label = wells_[i].getLabel();
         wb.wellRect.setBounds((int)(box.getX() + wells_[i].getColumn()*wellX + dp.xOffset + 0.5), (int)(box.getY() + wells_[i].getRow()*wellY + dp.yOffset + 0.5),
               (int)wellX, (int)wellY);
         wb.draw(g, dp);
         wb.dump();
      }      
   }

   private void drawGrid(Graphics2D g, Rectangle box) {
      // calculate plate active area
      double xFact = box.getWidth()/plate_.getXSize();
      double yFact = box.getHeight()/plate_.getYSize();
      double xOffset = plate_.getTopLeftX() * xFact;
      double yOffset = plate_.getTopLeftY() * yFact;
      
      double wellX = (box.getWidth() - 2.0*xOffset) / plate_.getNumColumns();
      double wellY = (box.getHeight() - 2.0*yOffset) / plate_.getNumRows();
      
      double xStartHor = box.getX() + xOffset;
      double xEndHor = xStartHor + box.getWidth() - 2*xOffset;
      for (int i=0; i<= plate_.getNumRows(); i++) {
         double yStart = box.getY() + i*wellY + yOffset;
         double yEnd = yStart;
         Point2D.Double ptStart = new Point2D.Double(xStartHor, yStart);
         Point2D.Double ptEnd = new Point2D.Double(xEndHor, yEnd);
         g.draw(new Line2D.Double(ptStart, ptEnd));      

      }
      
      double yStartV = box.getY() + yOffset;
      double yEndV = yStartV + box.getHeight() - 2*yOffset;
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
      double xOffset = plate_.getTopLeftX() * xFact;
      double yOffset = plate_.getTopLeftY() * yFact;
      
      double wellX = (box.getWidth() - 2.0*xOffset) / plate_.getNumColumns();
      double wellY = (box.getHeight() - 2.0*yOffset) / plate_.getNumRows();
      
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
      
      try {
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
      } catch (HCSException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
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
      
      if (sites == null)
         wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME);
      else
         wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, sites);
      
      wellBoxes_ = new WellBox[wells_.length];
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());  
      
      // calculate drawing parameters based on the active area
      
      DrawingParams dp = new DrawingParams();
      dp.xFactor = activeRect_.getWidth()/plate_.getXSize();
      dp.yFactor = activeRect_.getHeight()/plate_.getYSize();
      dp.xOffset = plate_.getTopLeftX() * dp.xFactor;
      dp.yOffset = plate_.getTopLeftY() * dp.yFactor;
      dp.xTopLeft = activeRect_.x;
      dp.yTopLeft = activeRect_.y;
      
      double wellX = (activeRect_.getWidth() - 2.0*dp.xOffset) / plate_.getNumColumns();
      double wellY = (activeRect_.getHeight() - 2.0*dp.yOffset) / plate_.getNumRows();
                  
      for (int i=0; i<wells_.length; i++) {
         WellBox wb = wellBoxes_[i];
         wb.label = wells_[i].getLabel();
         wb.wellRect.setBounds((int)(activeRect_.getX() + wells_[i].getColumn()*wellX + dp.xOffset + 0.5),
                               (int)(activeRect_.getY() + wells_[i].getRow()*wellY + dp.yOffset + 0.5), (int)wellX, (int)wellY);
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

}
