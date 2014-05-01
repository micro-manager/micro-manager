///////////////////////////////////////////////////////////////////////////////
//FILE:          SplitView.java
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

import org.micromanager.api.MMProcessorPlugin;

/** 
 * Micro-Manager plugin that can split the acquired image top-down or left-right
 * and display the split image as a two channel image.
 *
 * @author nico
 */
public class SplitView implements MMProcessorPlugin {
   public static final String menuName = "Split View";
   public static final String tooltipDescription =
      "Split images vertically or horizontally into two channels";

   public static Class<?> getProcessorClass() {
      return SplitViewProcessor.class;
   }

   @Override
   public String getInfo () {
      return "SplitView Plugin";
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }
   
   @Override
   public String getVersion() {
      return "0.2";
   }
   
   @Override
   public String getCopyright() {
      return "University of California, 2014";
   }
}
