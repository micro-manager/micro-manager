///////////////////////////////////////////////////////////////////////////////
//FILE:          ImageJUtils.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2015
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


package org.micromanager.asidispim.Utils;

import ij.IJ;

/**
 * Utility class for ImageJ interaction
 * 
 * 
 * @author Jon
 */
public class ImageJUtils {
   
   /**
    * Make it easy to execute an ImageJ command in its own thread (for speed).
    * After creating this object with the command (menu item) then call its start() method.
    * TODO: see if this would be faster using ImageJ's Executer class (http://rsb.info.nih.gov/ij/developer/api/ij/Executer.html)
    * @author Jon
    */
   public static class IJCommandThread extends Thread {
      private final String command_;
      private final String args_;
      public IJCommandThread(String command) {
         super(command);
         command_ = command;
         args_ = "";
      }
      public IJCommandThread(String command, String args) {
         super(command);
         command_ = command;
         args_ = args;
      }
      @Override
      public void run() {
         IJ.run(command_, args_);
      }
   }

}
