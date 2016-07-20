///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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

package org.micromanager;


import java.awt.geom.AffineTransform;
import java.awt.Rectangle;
import javax.swing.JFrame;

import org.micromanager.data.Datastore;


/**
 * Provides access to methods dealing with the "skin" or "look and feel" of the
 * user interface. This interface can be accessed via the Studio by
 * calling Studio.getSkin() or Studio.skin().
 */
public interface ApplicationSkin {
   /**
    * Available skins used by the application.
    */
   public enum SkinMode {
      DAY("Day"), NIGHT("Night");
      private final String desc_;

      SkinMode(String desc) {
         desc_ = desc;
      }

      public String getDesc() {
         return desc_;
      }

      public static SkinMode fromString(String desc) {
         for (SkinMode mode : SkinMode.values()) {
            if (mode.getDesc().contentEquals(desc)) {
               return mode;
            }
         }
         throw new IllegalArgumentException("Invalid skin mode " + desc);
      }
   }

   /**
    * Sets the background color of the GUI to the selected mode. This will
    * provoke a refresh of the GUI and post a ApplicationSkinEvent to the
    * application-wide event bus.
    * @param mode The mode to use.
    */
   public void setSkin(SkinMode mode);

   /**
    * @return the current Micro-Manager skin.
    */
   public SkinMode getSkin();
}
