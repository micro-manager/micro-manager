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

import java.util.List;
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
   private final List presets_;
   private final String backgroundFile_;
   private final List files_;

   public MultiChannelShadingFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      channelGroup_ = settings.getString(
            MultiChannelShadingMigForm.CHANNELGROUP, "Channels");
      useOpenCL_ = settings.getBoolean(MultiChannelShadingMigForm.USEOPENCL,
            false);
      presets_ = settings.getStringList("Presets", "");
      backgroundFile_ = settings.getString(
            MultiChannelShadingMigForm.DARKFIELDFILENAME, "");
      files_ = settings.getStringList("PresetFiles", "");
   }

   @Override
   public Processor createProcessor() {
      return new ShadingProcessor(studio_, channelGroup_, useOpenCL_,
            backgroundFile_, presets_, files_);
   }
}
