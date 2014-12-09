///////////////////////////////////////////////////////////////////////////////
//FILE:          MyStrings.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Data;



/**
 * Contains string constants.
 *  
 * @author Jon
 */
public class MyStrings {
   
   public static enum PanelNames {
     DEVICES("Devices"),
     ACQUSITION("Acquisition"),
     SETUP("Setup Path "),
     NAVIGATION("Navigation"),
     LIGHTSOURCE("Light"),
     SETTINGS("Settings"),
     DATAANALYSIS("Data Analysis"),
     HELP("Help"),
     BEAM_SUBPANEL("Beam_"),
     CAMERA_SUBPANEL("Camera_"),
     JOYSTICK_SUBPANEL("Joystick_"),
     STATUS_SUBPANEL("Status_")
      ;
      private final String text;
      PanelNames(String text) {
         this.text = text;
      }
      @Override
      public String toString() {
         return text;
      }
      
   }
   
  

}
