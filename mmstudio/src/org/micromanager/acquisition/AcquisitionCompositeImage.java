/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import ij.CompositeImage;
import ij.ImagePlus;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionCompositeImage extends CompositeImage {

   public AcquisitionCompositeImage(ImagePlus imp, int mode) {
      super(imp, mode);
   }

   public void updateImage() {
      try {
         JavaUtils.setRestrictedFieldValue(this, super.getClass(), "currentFrame", -1);
         super.updateImage();
      } catch (NoSuchFieldException ex) {
         ReportingUtils.logError(ex);
      }
   }

}
