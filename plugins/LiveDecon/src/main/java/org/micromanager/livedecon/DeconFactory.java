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

package org.micromanager.livedecon;

import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.data.ProcessorFactory;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;

/**
 * This class creates copies of the Processor that actually performs the
 * deconvolution.
 */
public class DeconFactory implements ProcessorFactory {
   private PropertyMap settings_;
   private Studio studio_;

   public DeconFactory(PropertyMap settings, Studio studio) {
      settings_ = settings;
      studio_ = studio;
   }

   @Override
   public Processor createProcessor() {
      // If the processor needs to be configured, then values should be pulled
      // from the settings_ PropertyMap.
      return new DeconProcessor(studio_);
   }
}
