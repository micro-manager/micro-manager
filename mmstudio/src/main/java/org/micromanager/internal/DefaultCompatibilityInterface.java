/*
Copyright (c) 2006 - 2013, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are 
permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of 
conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, this list of 
conditions and the following disclaimer in the documentation and/or other materials 
provided with the distribution.
    * Neither the name of the University of California, San Francisco nor the names of its 
contributors may be used to endorse or promote products derived from this software 
without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, 
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, 
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.micromanager.internal;

import java.awt.geom.AffineTransform;
import org.micromanager.CompatibilityInterface;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.internal.propertymap.DefaultPropertyMap;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * Implementation of the Compatibility Interface.
 * Is it time to retire this yet?
 */
public class DefaultCompatibilityInterface implements CompatibilityInterface {
   private final Studio studio_;
   private static final String AFFINE_TRANSFORM_LEGACY =
         "affine transform for mapping camera coordinates to stage coordinates for a specific pixel size config: ";
   private static final String AFFINE_TRANSFORM =
         "affine transform parameters for mapping camera coordinates to stage coordinates for a specific pixel size config: ";
   
   
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
      for (int i = 0; i < 3; i++) {
         if (Integer.parseInt(m[i]) < Integer.parseInt(m2[i])) {
            ReportingUtils.showError(
                  "This code needs Micro-Manager version " + version + " or greater");
            return true;
         }
         if (Integer.parseInt(m[i]) > Integer.parseInt(m2[i])) {
            return false;
         }
      }
      if (v2.length < 2 || v2[1].equals("")) {
         return false;
      }
      if (v.length < 2) {
         ReportingUtils.showError(
               "This code needs Micro-Manager version " + version + " or greater");
         return true;
      }
      if (Integer.parseInt(v[1]) < Integer.parseInt(v2[1])) {
         ReportingUtils.showError(
               "This code needs Micro-Manager version " + version + " or greater");
         return false;
      }
      return true;
   }

   @Override
   @Deprecated
   public AffineTransform getCameraTransform(String config) {
      // Try the modern way first
      double[] defaultParams = new double[0];
      double[] params = studio_.profile().getSettings(MMStudio.class)
            .getDoubleList(AFFINE_TRANSFORM + config, defaultParams);
      if (params != null && params.length == 6) {
         return new AffineTransform(params);
      }

      // The early 2.0-beta way of storing as a serialized object.
      PropertyMap studioSettings = studio_.profile()
            .getSettings(MMStudio.class).toPropertyMap();
      AffineTransform result =
            ((DefaultPropertyMap) studioSettings).getLegacySerializedObject(
               AFFINE_TRANSFORM_LEGACY + config, null);
      if (result != null) {
         // Save it the new way
         setCameraTransform(result, config);
         return result;
      }

      // For backwards compatibility, try retrieving it from the 1.4
      // Preferences instead.
      AffineTransform tfm = org.micromanager.internal.utils.UnpleasantLegacyCode
            .legacyRetrieveTransformFromPrefs("affine_transform_" + config);
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
      studio_.profile().getSettings(MMStudio.class).putDoubleList(
            AFFINE_TRANSFORM + config, params);
   }

}
