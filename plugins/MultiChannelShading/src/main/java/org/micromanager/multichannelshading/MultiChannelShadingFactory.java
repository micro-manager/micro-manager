///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.multichannelshading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;

/**
 * @author Chris Weisiger
 */
public class MultiChannelShadingFactory implements ProcessorFactory {
   private final Studio studio_;
   private final String channelGroup_;
   private final Boolean useOpenCL_;
   private final String backgroundFile_;
   private final Map<String, List<String>> presetsByCalibration_;
   private final Map<String, List<String>> filesByCalibration_;

   public MultiChannelShadingFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      channelGroup_ = settings.getString(
            MultiChannelShadingMigForm.CHANNELGROUP, "Channels");
      useOpenCL_ = settings.getBoolean(MultiChannelShadingMigForm.USEOPENCL,
            false);
      backgroundFile_ = settings.getString(
            MultiChannelShadingMigForm.DARKFIELDFILENAME, "");

      presetsByCalibration_ = new HashMap<>();
      filesByCalibration_ = new HashMap<>();

      List<String> configs = settings.getStringList("PixelSizeConfigs",
            new ArrayList<String>());
      if (!configs.isEmpty()) {
         for (String cal : configs) {
            List<String> presets = settings.getStringList("Presets-" + cal,
                  new ArrayList<String>());
            List<String> files = settings.getStringList("PresetFiles-" + cal,
                  new ArrayList<String>());
            presetsByCalibration_.put(cal, presets);
            filesByCalibration_.put(cal, files);
         }
      } else {
         // backward-compat: old format stored a single flat Presets/PresetFiles list
         String cal = settings.getString(
               MultiChannelShadingMigForm.PIXELSIZECALIBRATION,
               MultiChannelShadingMigForm.ANY_PIXELSIZE);
         List<String> presets = settings.getStringList("Presets",
               new ArrayList<String>());
         List<String> files = settings.getStringList("PresetFiles",
               new ArrayList<String>());
         presetsByCalibration_.put(cal, presets);
         filesByCalibration_.put(cal, files);
      }
   }

   @Override
   public Processor createProcessor() {
      return new ShadingProcessor(studio_, channelGroup_, useOpenCL_,
            backgroundFile_, presetsByCalibration_, filesByCalibration_);
   }
}
