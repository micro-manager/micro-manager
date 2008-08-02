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
         siteRect = new Rectangle(3, 3);
         selected = false;
         active = false;
         params_ = new DrawingParams();
         sites_ = pl;
      }
      
      public void draw(Graphics2D g, DrawingParams dp) {
         params_ = dp;
         draw(g);
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
      wellBoxes_ = new WellBox[plate_.getNumberOfRows() * plate_.getNumberOfColumns()];
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());
   }
   public PlatePanel(SBSPlate plate, PositionList pl) {
      plate_ = plate;
      wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, pl);
      wellBoxes_ = new WellBox[plate_.getNumberOfRows() * plate_.getNumberOfColumns()];
      for (int i=0; i<wellBoxes_.length; i++)
         wellBoxes_[i] = new WellBox(wells_[i].getSitePositions());      
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
      
      // calculate drawing parameters based on the active area
      DrawingParams dp = new DrawingParams();
      dp.xFactor = box.getWidth()/plate_.getXSize();
      dp.yFactor = box.getHeight()/plate_.getYSize();
      dp.xOffset = plate_.getTopLeftX() * dp.xFactor;
      dp.yOffset = plate_.getTopLeftY() * dp.yFactor;
      dp.xTopLeft = box.x;
      dp.yTopLeft = box.y;
      
      double wellX = (box.getWidth() - 2.0*dp.xOffset) / plate_.getNumberOfColumns();
      double wellY = (box.getHeight() - 2.0*dp.yOffset) / plate_.getNumberOfRows();
                  
      try {
         for (int i=0; i<plate_.getNumberOfRows(); i++) {
            for (int j=0; j<plate_.getNumberOfColumns(); j++) {
               WellBox wb = wellBoxes_[i*plate_.getNumberOfColumns() + j];
               wb.label = plate_.getWellLabel(i+1, j+1);
               wb.wellRect.setBounds((int)(box.getX() + j*wellX + dp.xOffset + 0.5), (int)(box.getY() + i*wellY + dp.yOffset + 0.5),
                                 (int)wellX, (int)wellY);
               wb.draw(g, dp);
            }
         }
      } catch (HCSException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
   }

   private void drawGrid(Graphics2D g, Rectangle box) {
      // calculate plate active area
      double xFact = box.getWidth()/plate_.getXSize();
      double yFact = box.getHeight()/plate_.getYSize();
      double xOffset = plate_.getTopLeftX() * xFact;
      double yOffset = plate_.getTopLeftY() * yFact;
      
      double wellX = (box.getWidth() - 2.0*xOffset) / plate_.getNumberOfColumns();
      double wellY = (box.getHeight() - 2.0*yOffset) / plate_.getNumberOfRows();
      
      double xStartHor = box.getX() + xOffset;
      double xEndHor = xStartHor + box.getWidth() - 2*xOffset;
      for (int i=0; i<= plate_.getNumberOfRows(); i++) {
         double yStart = box.getY() + i*wellY + yOffset;
         double yEnd = yStart;
         Point2D.Double ptStart = new Point2D.Double(xStartHor, yStart);
         Point2D.Double ptEnd = new Point2D.Double(xEndHor, yEnd);
         g.draw(new Line2D.Double(ptStart, ptEnd));      

      }
      
      double yStartV = box.getY() + yOffset;
      double yEndV = yStartV + box.getHeight() - 2*yOffset;
      for (int i=0; i<= plate_.getNumberOfColumns(); i++) {
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
      
      double wellX = (box.getWidth() - 2.0*xOffset) / plate_.getNumberOfColumns();
      double wellY = (box.getHeight() - 2.0*yOffset) / plate_.getNumberOfRows();
      
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
         for (int i=0; i<plate_.getNumberOfColumns(); i++) {
            labelBoxX.x = (int)(i*wellX + 0.5 + xMargin_ + xOffset);
            TextLayout tl = new TextLayout(plate_.getColumnLabel(i+1), f, frc);
            Rectangle2D b = tl.getBounds();
            Point loc = getLocation(labelBoxX, b.getBounds());
            tl.draw(g, loc.x, loc.y);
         }
         
         for (int i=0; i<plate_.getNumberOfRows(); i++) {
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
      if (sites == null)
         wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME);
      else
         wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, sites);
   }
   
   WellPositionList[] getWellPositions() {
      return wells_;
   }
   
   void selectWell(int row, int col, boolean sel) {
      int index = row*plate_.getNumberOfColumns() + col;
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
      int index = row*plate_.getNumberOfColumns() + col;
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
