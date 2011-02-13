///////////////////////////////////////////////////////////////////////////////
//FILE:          DisplayMode.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, April 4, 2009
//
// COPYRIGHT:    University of California, San Francisco, 2009
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

public class DisplayMode {
   public static final int ALL = 0;
   public static final int LAST_FRAME = 1;
   public static final int SINGLE_WINDOW= 2;
   
   private int id_;
   
   public DisplayMode(int id) {
      id_ = id;
   }
   
   @Override
   public String toString() {
      if (id_ == ALL)
         return new String("All");
      else if (id_ == LAST_FRAME)
         return new String("Last Frame");
      else if (id_ == SINGLE_WINDOW)
         return new String("Single Window");

      return "undefined";
   }
   
   public int getID() {
      return id_;
   }
}
