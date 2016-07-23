///////////////////////////////////////////////////////////////////////////////
//FILE:          SplitViewProcessor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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



package org.micromanager.splitview;

import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class SplitViewFactory implements ProcessorFactory {
   private Studio studio_;
   private String orientation_;
   private int numSplits_;
   public SplitViewFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      orientation_ = settings.getString("orientation");
      numSplits_ = settings.getInt("splits", 2);
   }

   @Override
   public Processor createProcessor() {
      return new SplitViewProcessor(studio_, orientation_, numSplits_);
   }
}
