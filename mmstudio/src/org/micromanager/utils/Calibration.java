///////////////////////////////////////////////////////////////////////////////
//FILE:          Calibration.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Describes a single calibration.
//
// AUTHOR:       Nico Stuurmannico@cmp.ucsf.edu, June 2008
//
// COPYRIGHT:    University of California, San Francisco, 2008
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
//
//
package org.micromanager.utils;

import java.text.DecimalFormat;

import mmcorej.Configuration;

public class Calibration {
   private Double pixelSize_;
   private String calibrationName_;
   private Configuration configuration_;
   private static DecimalFormat fmt = new DecimalFormat("#0.000");
   
   public Calibration() {
      calibrationName_ = "Undefined";
      pixelSize_ = 0.0;
   }
   
   public String getVerbose() {
      return calibrationName_ + "(" + fmt.format(pixelSize_) + ")";
   }

   public void setLabel(String name) {
      calibrationName_ = name;
   }

   public String getLabel() {
      return calibrationName_;
   }

   public void setPixelSizeUm(double size) {
      pixelSize_ = size;
   }

   public Double getPixelSizeUm() {
      return pixelSize_;
   }

   public void setConfiguration(Configuration configuration) {
      configuration_ = configuration;
   }

   public Configuration getConfiguration() {
      return configuration_;
   }


}
