/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal;

import java.awt.geom.AffineTransform;
import org.micromanager.CompatibilityInterface;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.internal.propertymap.DefaultPropertyMap;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author nicke
 */
public class DefaultCompatibilityInterface implements CompatibilityInterface {
   private final Studio studio_;
   private static final String AFFINE_TRANSFORM_LEGACY = "affine transform for mapping camera coordinates to stage coordinates for a specific pixel size config: ";
   private static final String AFFINE_TRANSFORM = "affine transform parameters for mapping camera coordinates to stage coordinates for a specific pixel size config: ";
   
   
   public DefaultCompatibilityInterface(Studio studio) {
      studio_ = studio;
   }
   
   @Override
   public String getVersion() {
      return MMVersion.VERSION_STRING;
   }
   
   @Override
   public boolean versionLessThan(String version) throws NumberFormatException {
      String[] v = MMVersion.VERSION_STRING.split(" ", 2);
      String[] m = v[0].split("\\.", 3);
      String[] v2 = version.split(" ", 2);
      String[] m2 = v2[0].split("\\.", 3);
      for (int i=0; i < 3; i++) {
         if (Integer.parseInt(m[i]) < Integer.parseInt(m2[i])) {
            ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
            return true;
         }
         if (Integer.parseInt(m[i]) > Integer.parseInt(m2[i])) {
            return false;
         }
      }
      if (v2.length < 2 || v2[1].equals("") ) {
         return false;
      }
      if (v.length < 2 ) {
         ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
         return true;
      }
      if (Integer.parseInt(v[1]) < Integer.parseInt(v2[1])) {
         ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
         return false;
      }
      return true;
   }

   @Override
   @Deprecated
   public AffineTransform getCameraTransform(String config) {
      // Try the modern way first
      double[] defaultParams = new double[0];
      double[] params = studio_.profile().getSettings(MMStudio.class).
              getDoubleList(AFFINE_TRANSFORM + config, defaultParams);
      if (params != null && params.length == 6) {
         return new AffineTransform(params);
      }

      // The early 2.0-beta way of storing as a serialized object.
      PropertyMap studioSettings = studio_.profile().
            getSettings(MMStudio.class).toPropertyMap();
      AffineTransform result = (AffineTransform)
         ((DefaultPropertyMap) studioSettings).getLegacySerializedObject(
               AFFINE_TRANSFORM_LEGACY + config, null);
      if (result != null) {
         // Save it the new way
         setCameraTransform(result, config);
         return result;
      }

      // For backwards compatibility, try retrieving it from the 1.4
      // Preferences instead.
      AffineTransform tfm = org.micromanager.internal.utils.UnpleasantLegacyCode.
              legacyRetrieveTransformFromPrefs("affine_transform_" + config);
      if (tfm != null) {
         // Save it the new way.
         setCameraTransform(tfm, config);
      }
      return tfm;
   }

   @Override
   @Deprecated
   public void setCameraTransform(AffineTransform transform, String config) {
      double[] params = new double[6];
      transform.getMatrix(params);
      studio_.profile().getSettings(MMStudio.class).putDoubleList(AFFINE_TRANSFORM + config, params);
   }

}
