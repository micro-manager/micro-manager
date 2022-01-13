///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    University of California, San Francisco
//
// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.
//
// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.positionlist;

class AxisData {
   public enum AxisType {
      oneD, twoD
   }

   private boolean use_;
   private final String axisName_;
   private final AxisType type_;
   
   public AxisData(boolean use, String axisName, AxisType type) {
      use_ = use;
      axisName_ = axisName;
      type_ = type;
   }

   public boolean getUse() {
      return use_;
   }

   public String getAxisName() {
      return axisName_;
   }

   public AxisType getType() {
      return type_;
   }
   
   public void setUse(boolean use) {
      use_ = use;
   }
}
