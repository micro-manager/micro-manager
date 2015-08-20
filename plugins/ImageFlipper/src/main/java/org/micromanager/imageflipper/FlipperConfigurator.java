///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.imageflipper;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.data.ProcessorFactory;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class FlipperConfigurator implements ProcessorConfigurator {
   private Studio studio_;

   public FlipperConfigurator(Studio studio) {
      studio_ = studio;
   }

   // HACK: providing hardcoded values for now (GUI hasn't been reimplemented)
   @Override
   public PropertyMap getSettings() {
      PropertyMap.PropertyMapBuilder builder = studio_.data().getPropertyMapBuilder();
      builder.putString("camera", "Camera");
      builder.putInt("rotation", 0);
      builder.putBoolean("shouldMirror", false);
      return builder.build();
   }
}
