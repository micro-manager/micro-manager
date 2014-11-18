///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiChannelShading.java
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

/**
 *
 * @author kthorn
 */
public class MultiChannelShading implements org.micromanager.api.MMProcessorPlugin {
   public static final String menuName = "Flat-Field Correction";
   public static final String tooltipDescription =
      "Apply dark subtraction and flat-field correction";

   public static String versionNumber = "0.2";

   public static Class<?> getProcessorClass() {
      return ShadingProcessor.class;
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }

   @Override
   public String getInfo() {
      return tooltipDescription;
   }

   @Override
   public String getVersion() {
      return versionNumber;
   }

   @Override
   public String getCopyright() {
      return "University of California, 2014";
   }
   
}
