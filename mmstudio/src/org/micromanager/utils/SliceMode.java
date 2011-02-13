///////////////////////////////////////////////////////////////////////////////
//FILE:          SliceMode.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 20, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2007
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
// CVS:          $Id: CfgFileFilter.java 2 2007-02-27 23:33:17Z nenad $
//
package org.micromanager.utils;

public class SliceMode {
   public static final int CHANNELS_FIRST = 0;
   public static final int SLICES_FIRST = 1;
   
   int id_;
   
   public SliceMode(int id) {
      id_ = id;
   }
   
   @Override
   public String toString() {
      if (id_ == CHANNELS_FIRST)
         return new String("Channels first");
      else if (id_ == SLICES_FIRST)
         return new String("Slices first");
      else
         return new String("Undefined");
   }  

   public int getID() {
      return id_;
   }
}
