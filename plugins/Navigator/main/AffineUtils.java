package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import org.micromanager.MMStudio;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.StagePosition;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class AffineUtils {
   
   public static AffineTransform getAffineTransform(double xCenter, double yCenter) {
      //Get affine transform
      Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);

      AffineTransform transform = null;
      try {
         transform = JavaUtils.getObjectFromPrefs(prefs, "affine_transform_"
                 + MMStudio.getInstance().getMMCore().getCurrentPixelSizeConfig(), (AffineTransform) null);
         //set map origin to current stage position
         double[] matrix = new double[6];
         transform.getMatrix(matrix);
         matrix[4] = xCenter;
         matrix[5] = yCenter;
         return new AffineTransform(matrix);     
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         ReportingUtils.showError("Couldnt get affine transform");
         return null;
      }
   }

   /**
    *
    * @param xCenter grid center x (stage coordinates)
    * @param yCenter grid center y (stage coordinates)
    * @param numCols
    * @param numRows
    * @param pixelOverlapX
    * @param pixelOverlapY
    */
   public static void createGrid(double xCenter, double yCenter, int numCols, int numRows, int pixelOverlapX, int pixelOverlapY) {
      ScriptInterface app = MMStudio.getInstance();
      AffineTransform transform = getAffineTransform(xCenter, yCenter);

      long height = app.getMMCore().getImageHeight();
      long width = app.getMMCore().getImageWidth();
      ArrayList<MultiStagePosition> positions = new ArrayList<MultiStagePosition>();
      //due to affine transform, xindex and yindex correspond to image space
      for (int col = 0; col < numCols; col++) {
         double xPixelOffset = (col - (numCols - 1) / 2.0) * (width - pixelOverlapX);
         for (int row = 0; row < numRows; row++) {
            double yPixelOffset = (row - (numRows - 1) / 2.0) * (height - pixelOverlapY);

            Point2D.Double pixelPos = new Point2D.Double(xPixelOffset, yPixelOffset);
            Point2D.Double stagePos = new Point2D.Double();
            transform.transform(pixelPos, stagePos);

            MultiStagePosition mpl = new MultiStagePosition();
            StagePosition sp = new StagePosition();
            sp.numAxes = 2;
            sp.stageName = app.getMMCore().getXYStageDevice();
            sp.x = stagePos.x;
            sp.y = stagePos.y;
            mpl.add(sp);

            //label should be Grid_(x index of tile)_(y index of tile) (in image space)
            String lab = new String("Grid_" + col + "_" + row);

            mpl.setLabel(lab);
            //row, column (in image space)
            mpl.setGridCoordinates(row, col);
            positions.add(mpl);            
         }
      }
   
      try {
         PositionList list = app.getPositionList();
         list.clearAllPositions();
         for (MultiStagePosition p : positions ) {
            list.addPosition(p);
         }

         list.notifyChangeListeners();
      } catch (MMScriptException e) {
         ReportingUtils.showError(e.getMessage());
      }
   }
 
}
