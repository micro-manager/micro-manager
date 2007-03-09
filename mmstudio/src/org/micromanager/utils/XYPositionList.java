///////////////////////////////////////////////////////////////////////////////
//FILE:          XYPositionList.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
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
// CVS:          $Id$
//
package org.micromanager.utils;
import java.util.ArrayList;

/**
 * Navigation list of positions for the XYStage.
 * Used for multi-site acquistion support.
 */
public class XYPositionList {
   ArrayList positions_;
   
   public XYPositionList() {
      positions_ = new ArrayList();
   }

   public XYPosition getPosition(int idx) {
      return (XYPosition) positions_.get(idx);
   }
   
   public double getY(String posLabel) {
      return 0.0;
   }
   
   public void addPosition(XYPosition pos) {
      positions_.add(pos);
   }
   
   public int getNumberOfPositions() {
      return positions_.size();
   }
   
}

