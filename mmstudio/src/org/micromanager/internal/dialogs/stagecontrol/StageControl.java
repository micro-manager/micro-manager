/**
 * StageControl dialog
 * 
 * This dialog provides a simple interface to the currently active XY stage and
 * Z (focus) drive
 *
 * Created on Aug 19, 2010, 10:04:49 PM
 * Nico Stuurman, copyright UCSF, 2010
 * 
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 **/

package org.micromanager.internal.dialogs.stagecontrol;

import org.micromanager.Studio;

// TODO: this was adapted from a plugin, so its structure is a little odd.
public class StageControl {
   private static StageControlFrame myFrame_;

   public static void show() {
      Studio studio = org.micromanager.internal.MMStudio.getInstance();
      if (myFrame_ == null) {
         myFrame_ = new StageControlFrame(studio);
         studio.compat().addMMListener(myFrame_);
      }
      myFrame_.initialize();
      myFrame_.setVisible(true);
   }
}
