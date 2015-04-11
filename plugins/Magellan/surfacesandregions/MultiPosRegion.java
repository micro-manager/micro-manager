/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package surfacesandregions;

import acq.FixedAreaAcquisitionSettings;
import coordinates.AffineUtils;
import coordinates.XYStagePosition;
import gui.SettingsDialog;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import imageconstruction.CoreCommunicator;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class MultiPosRegion implements XYFootprint{

   private volatile Point2D.Double center_; //stored in stage space
   private volatile int overlapX_, overlapY_, rows_, cols_;
   private RegionManager manager_;
   private String name_;
   private String pixelSizeConfig_;

   public MultiPosRegion(RegionManager manager, int r, int c, Point2D.Double center)  {
      manager_ = manager;
      name_ = manager.getNewName();
      center_ = center;
      try {
         pixelSizeConfig_ = MMStudio.getInstance().getCore().getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         ReportingUtils.showError("couldnt get pixel size config");
      }
      updateParams(r, c);
   }

   @Override
   public String toString() {
      return name_;
   }

   public String getName() {
      return name_;
   }

   public void rename(String newName) {
      name_ = newName;
      manager_.updateRegionTableAndCombos();
   }

   public double getWidth_um() {
      double pixelSize = MMStudio.getInstance().getCore().getPixelSizeUm();
      int pixelWidth = cols_ * (CoreCommunicator.getImageWidth() - overlapX_) + overlapX_;
      return pixelSize * pixelWidth;
   }

   public double getHeight_um() {
      double pixelSize = MMStudio.getInstance().getCore().getPixelSizeUm();
      int imageHeight = (int) MMStudio.getInstance().getCore().getImageHeight();
      int pixelHeight = rows_ * (imageHeight - overlapY_) + overlapY_;
      return pixelSize * pixelHeight;
   }

   public void updateParams(int rows, int cols) {
      updateOverlap(FixedAreaAcquisitionSettings.getStoredTileOverlapPercentage() / 100);
      rows_ = rows;
      cols_ = cols;
      manager_.updateRegionTableAndCombos();
      manager_.drawRegionOverlay(this);
   }
   
   private void updateOverlap(double overlapPercent) {
      overlapX_ = (int) (CoreCommunicator.getImageWidth() * overlapPercent);
      overlapY_ = (int) (CoreCommunicator.getImageHeight() * overlapPercent);
   }

   @Override
   public ArrayList<XYStagePosition> getXYPositions(double tileOverlapPercent) {
      try {
         AffineTransform transform = AffineUtils.getAffineTransform(MMStudio.getInstance().getCore().getCurrentPixelSizeConfig(), center_.x, center_.y);
         ArrayList<XYStagePosition> positions = new ArrayList<XYStagePosition>();
         int fullTileWidth = (int) CoreCommunicator.getImageWidth();
         int fullTileHeight = (int) CoreCommunicator.getImageHeight();
         updateOverlap(tileOverlapPercent / 100);
         int tileWidthMinusOverlap = fullTileWidth - overlapX_;
         int tileHeightMinusOverlap = fullTileHeight - overlapY_;
         for (int col = 0; col < cols_; col++) {
            double xPixelOffset = (col - (cols_ - 1) / 2.0) * tileWidthMinusOverlap;
            for (int row = 0; row < rows_; row++) {
               double yPixelOffset = (row - (rows_ - 1) / 2.0) * tileHeightMinusOverlap;
               Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
               Point2D.Double stagePos = new Point2D.Double();
               transform.transform(pixelPos, stagePos);
               positions.add(new XYStagePosition(stagePos, tileWidthMinusOverlap, tileHeightMinusOverlap,
                       fullTileWidth, fullTileHeight, row, col, pixelSizeConfig_));
            }
         }
         return positions;
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't get affine transform");
         throw new RuntimeException();
      }
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

   public Point2D.Double center() {
      return center_;
   }

   public void translate(double dx, double dy) {
      center_ = new Point2D.Double(center_.x + dx, center_.y + dy);
      manager_.drawRegionOverlay(this);
   }
}
