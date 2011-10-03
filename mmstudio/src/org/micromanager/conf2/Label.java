///////////////////////////////////////////////////////////////////////////////
//FILE:          Label.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
// CVS:          $Id: Label.java 2 2007-02-27 23:33:17Z nenad $
//
package org.micromanager.conf2;

/**
 * Label data for state devices
 *
 */
public class Label {
   String label_;
   int state_;
   
   public Label() {
      label_ = new String("Undefined");
      state_ = 0;
   }
   
   public Label(String lab, int pos) {
      label_ = lab;
      state_ = pos;
   }
}
