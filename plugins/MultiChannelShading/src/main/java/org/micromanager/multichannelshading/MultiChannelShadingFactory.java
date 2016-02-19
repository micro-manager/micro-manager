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

import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.internal.MMStudio;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.MMException;

/**
 *
 * @author Chris Weisiger
 */
public class MultiChannelShadingFactory implements ProcessorFactory {
   private Studio studio_;
   private String channelGroup_;
   private String[] presets_;
   private String backgroundFile_;
   private String[] files_;

   public MultiChannelShadingFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      channelGroup_ = settings.getString(
            MultiChannelShadingMigForm.CHANNELGROUP);
      presets_ = settings.getStringArray("Presets");
      backgroundFile_ = settings.getString(
            MultiChannelShadingMigForm.DARKFIELDFILENAME);
      files_ = settings.getStringArray("PresetFiles");
   }

   @Override
   public Processor createProcessor() {
      return new ShadingProcessor(studio_, channelGroup_, backgroundFile_,
            presets_, files_);
   }
}
