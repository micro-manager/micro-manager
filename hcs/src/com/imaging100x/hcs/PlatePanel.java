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
   private final int lineThick_ = 1;
   
   private SBSPlate plate_;
   WellPositionList[] wells_;
   Rectangle siteRect_ = new Rectangle(3, 3);
   
   private class WellBox {
      String label_;
      Color color_;
      Rectangle rect_;
      
      public WellBox() {
         label_ = new String();
         //color_ = new Color();
      }
   }
   
   public PlatePanel(SBSPlate plate) {
      plate_ = plate;
      wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME);
   }
   public PlatePanel(SBSPlate plate, PositionList pl) {
      plate_ = plate;
      wells_ = plate_.generatePositions(SBSPlate.DEFAULT_XYSTAGE_NAME, pl);
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
      drawGrid(g2d, box);
      drawImagingSites(g2d, box);
           
      // restore settings
      g2d.setPaint(oldPaint);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
   }

   private void drawImagingSites(Graphics2D g, Rectangle box) {
      double xFactor = box.width / plate_.getXSize();
      double yFactor = box.height / plate_.getYSize();
      
      int siteOffsetX = siteRect_.width / 2;
      int siteOffsetY = siteRect_.height / 2;
            
      for (int i=0; i<wells_.length; i++) {
         PositionList pl = wells_[i].getSitePositions();
         try {
            double x = plate_.getWellXUm(wells_[i].getLabel());
            double y = plate_.getWellYUm(wells_[i].getLabel());
            //System.out.println("well " + wells_[i].getLabel() + " : " + x + "," + y);
         } catch (HCSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
         for (int j=0; j<pl.getNumberOfPositions(); j++) {
            siteRect_.x = (int)(pl.getPosition(j).getX() * xFactor + box.x - siteOffsetX + 0.5);
            siteRect_.y = (int)(pl.getPosition(j).getY() * yFactor + box.y - siteOffsetY + 0.5);
            //System.out.println(pl.getPosition(j).getX() + "," + pl.getPosition(j).getX());
            g.draw(siteRect_);
         }
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
}
