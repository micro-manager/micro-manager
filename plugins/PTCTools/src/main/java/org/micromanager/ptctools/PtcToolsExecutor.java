///////////////////////////////////////////////////////////////////////////////
//FILE:          PtcToolsExecutor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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



package org.micromanager.ptctools;

import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class PtcToolsExecutor extends Thread  {
   private final Studio studio_;
   private final PropertyMap settings_;
   
   public PtcToolsExecutor(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
   }

   @Override
   public void run() {
      // Ask user to block all light, collect nrframes, and calculate
      // float offset, stdDev, and fixed pattern noise images.
      
      // then ask user to provide light, and do the same at increasing 
      // exposures
      // TODO   
   }
}
