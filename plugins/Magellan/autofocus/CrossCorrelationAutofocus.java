/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package autofocus;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.math.ArgumentOutsideDomainException;
import org.apache.commons.math.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;
import org.json.JSONException;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class CrossCorrelationAutofocus {
   
     
   
 
    private ImagePlus createRefImage(int frame) throws JSONException {
        //downsample image by averaging squares of pixels into single pixels
        int width = MDUtils.getWidth(imageStorage_.getSummaryMetadata());
        int height = MDUtils.getHeight(imageStorage_.getSummaryMetadata());
        int dsFactor = Math.max(1, Math.max(width, height) / 256);
        int dsWidth = width / dsFactor, dsHeight = height / dsFactor;
        ImageStack stack = new ImageStack(dsWidth, dsHeight);
        int numSlices = MDUtils.getNumSlices(imageStorage_.getSummaryMetadata());
        for (int s = 0; s < numSlices; s++) {
            byte[] originalPix = (byte[]) imageStorage_.getImage(channel_, s, frame, 0).pix;        
            stack.addSlice(null,downsamplePix(originalPix,width,height,dsFactor,dsWidth,dsHeight));
        } 
        return new ImagePlus("AF image",stack);
    }
    
   
   public double fullFocus() throws MMException {
    
      if (eng_.shouldAFRun()) {
          try{
          double zPos1 = 1000000;
            double zPos2 = gui_.getMMCore().getPosition("Z"); 
//            int count = 0;
            while (Math.abs(zPos1 - zPos2) > 1) {
                zPos1 = zPos2;
                zPos2 = gui_.getMMCore().getPosition(gui_.getMMCore().getFocusDevice()); 
//                count++;
            }
          } catch (Exception e) {}
          
          
          if (imageStorage_ == null) {                      
              //first call comes before second TP--get image cache
              imageStorage_ = eng_.getStorage(); 
              //create text file for output
              File f = new File(imageStorage_.getDiskLocation());
              File txtFile = new File(f.getParent() + File.separator + f.getName() + "AutofocusLog.txt");
              try {
                  logger_ = new FileWriter(txtFile,true);
              } catch (IOException ex) {
                  IJ.log("Couldn't make AF log file");
              }
          } else {
              //second call and onward--run AF algorithm and adjust focus position
              try {
                int frame = imageStorage_.lastAcquiredFrame();
                ImagePlus tp0 = createRefImage(0);
                ImagePlus currentTP = createRefImage(frame);
                double displacement = calcFocusDrift(tp0,currentTP);
                //displacement is how far current position is from start             
                double currentOffset = eng_.getFocusOffset();
                double drift = displacement + currentOffset;
                
                if (Math.abs(drift) < Math.abs(lastDrift_) + 25 ) {
                    //Adjust reference in acqEng accordingly
                    eng_.setFocusOffset(drift);
                    logger_.write("frame: " + frame + "\t\tcurrent offset:  " +  currentOffset +
                            "\t\tdisplacemnet: " + displacement);
                    lastDrift_ = drift;
                } else {
                    logger_.write("calculated drift exceeds tolerance");
                } 
                
              } catch (Exception e) {
                  try {
                      logger_.write("Couldn't run autofocus");
                      logger_.write(e.getMessage());
                  } catch (IOException ex) {
                     
                  }
              }
          }
         //runnable in acq eng will then take over to apply depth list properly
      }
      
      return 0;
   }
   ///for debugging purposes

   private static double calcFocusDrift(ImagePlus baseline, ImagePlus current) throws JSONException {     
//      baseline.show();
//      current.show();
//      baseline.setTitle("baseline");
//      current.setTitle("current");
//      //run macro to do cross correlation
//      IJ.runMacro("run(\"3D Cross Correlation\", \"target=current pattern=baseline\");\n"
//              + "selectWindow(\"Cross_correlation_3D\");");
      ImagePlus cc = WindowManager.getImage("Cross_correlation_3D");
      double[] ccIntensity = new double[cc.getNSlices()], x = new double[cc.getNSlices()];
      for (int i = 1; i <= ccIntensity.length; i++) {
         cc.setSlice(i);
         ccIntensity[i - 1] = cc.getStatistics(ImagePlus.MIN_MAX).max;        
         x[i - 1] = i - 1;
         System.out.println(ccIntensity[i -1]);
      }

      
      PolynomialSplineFunction func = new SplineInterpolator().interpolate(x, ccIntensity);
      double[] preciseX = new double[ 100 * (x.length-1)];
      for (int i = 0; i < preciseX.length; i++) {
         preciseX[i] = i * 0.01;
      }
      
      System.out.println("\n");
      //find max value
      int maxIndex = 0;
      for (int i = 0; i < preciseX.length; i++) {
         try {

            System.out.println(preciseX[i] + "\t" + func.value( preciseX[i] ) );

            if (func.value( preciseX[i] ) > func.value(preciseX[maxIndex])) {
               maxIndex = i;
            }
         } catch (ArgumentOutsideDomainException ex) {
            ReportingUtils.showError("Spline value calculation outside range");
         }
      }
      double center = preciseX[maxIndex];

//
//      double z_step = imageStorage_.getSummaryMetadata().getDouble("z-step_um");
//      double drift_um = (center - (((double) cc.getNSlices()) / 2.0)) * z_step;
//      //negative means current focus needs to be moved up
//
//      //close calulation images
//      baseline.changes = false;
//      baseline.close();
//      current.changes = false;
//      current.close();
//      cc.changes = false;
//      cc.close();
//      
//      return drift_um;
      return 0;
   }
 
   
}
