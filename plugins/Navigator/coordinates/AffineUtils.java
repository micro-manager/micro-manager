package coordinates;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
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
   
   private static TreeMap<String, AffineTransform> affineTransforms_ = new TreeMap<String,AffineTransform>();
   
   //Only read from preferences one time, so that an inordinate amount of time isn't spent in native system calls
   public static AffineTransform getAffineTransform(String pixelSizeConfig, double xCenter, double yCenter) {
      try {
         AffineTransform transform = null;
         if (affineTransforms_.containsKey(pixelSizeConfig)) {
            transform = affineTransforms_.get(pixelSizeConfig);
            //copy transform so multiple referneces with different translations cause problems
            double[] newMat = new double[6];
            transform.getMatrix(newMat);
            transform = new AffineTransform(newMat);
         } else {
            //Get affine transform from prefs
            Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);
            transform = JavaUtils.getObjectFromPrefs(prefs, "affine_transform_" + pixelSizeConfig, (AffineTransform) null);
            affineTransforms_.put(pixelSizeConfig, transform);
         }
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

 
}
