///////////////////////////////////////////////////////////////////////////////
//FILE:          DisplayControls.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
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
package mmcloneclasses.internalinterfaces;

import java.awt.Panel;
import org.json.JSONObject;

public abstract class DisplayControls extends Panel {

   public DisplayControls() {};

   public DisplayControls(java.awt.LayoutManager manager) {
      super(manager);
   }
   
   abstract public void imagesOnDiskUpdate(boolean onDisk);
   
   abstract public void acquiringImagesUpdate(boolean acquiring);
   
   abstract public void setImageInfoLabel(String text);

   abstract public void newImageUpdate(JSONObject tags);
   
   abstract public void prepareForClose();

   public void setChannel(int c) {}

   public int getPosition() {
      return 0;
   }

   public void setPosition(int p) {}

   public int getNumPositions() {
      return 1;
   }
}
