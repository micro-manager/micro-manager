/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import gui.SettingsDialog;
import java.awt.Point;
import org.micromanager.MMStudio;

/**
 *
 * @author Henry
 */
public class MultiPosRegion {
   
     private int centerX_, centerY_; //stored in pixel space
     private int overlapX_, overlapY_, rows_, cols_;

      public MultiPosRegion(int r, int c, int cx, int cy) {
         centerX_ = cx;
         centerY_ = cy;
         rows_ = r;
         cols_ = c;
         overlapX_ = SettingsDialog.getOverlapX();
         overlapY_ = SettingsDialog.getOverlapY();
      }
      
      public double getWidth_um() {
         double pixelSize = MMStudio.getInstance().getCore().getPixelSizeUm();
         int imageWidth = (int) MMStudio.getInstance().getCore().getImageWidth();
         int pixelWidth = cols_ * (imageWidth - overlapX_) + overlapX_;
         return pixelSize * pixelWidth;         
      }
      
      public double getHeight_um() {
         double pixelSize = MMStudio.getInstance().getCore().getPixelSizeUm();
         int imageHeight = (int) MMStudio.getInstance().getCore().getImageHeight();
         int pixelHeight = rows_ * (imageHeight - overlapY_) + overlapY_;
         return pixelSize * pixelHeight;
      }
      
      public void updateParams(int rows, int cols) {
         overlapX_ = SettingsDialog.getOverlapX();
         overlapY_ = SettingsDialog.getOverlapY();
         rows_ = rows;
         cols_ = cols;
      }
      
      public int numCols() {
         return cols_;
      }
      
      public int numRows() {
         return rows_;
      }
      
      public int overlapX() {
         return overlapX_;
      }
      
      public int overlapY() {
         return overlapY_;
      }
      
      public Point center() {
         return new Point(centerX_, centerY_);
      }
      
      public void translate(int dx, int dy) {
         System.out.println(centerX_ + "\t" + centerY_ + "\t" + dx + "\t" + dy);
         centerX_ += dx;
         centerY_ += dy;
      }
      
   
}
