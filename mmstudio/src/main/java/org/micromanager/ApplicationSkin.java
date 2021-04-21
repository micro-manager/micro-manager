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


import java.awt.Color;


/**
 * Provides access to methods dealing with the "skin" or "look and feel" of the
 * user interface. In the majority of situations, you should not need to worry
 * about what skin the GUI is using; it is set by the user and automatically
 * applies to most GUI elements without any special effort on your part. This
 * module allows you to change the skin programmatically, and provides manual
 * access to certain color information for when you have a GUI element that
 * does not automatically respect the current skin.
 * This interface can be accessed via the Application by calling
 * Application.getSkin() or Application.skin().
 */
public interface ApplicationSkin {
   /**
    * Available skins used by the application.
    */
   enum SkinMode {
      DAY("Day"), 
      NIGHT("Night");
      private final String desc_;

      SkinMode(String desc) {
         desc_ = desc;
      }

      /**
       * 
       * @return string describing this skin
       */
      public String getDesc() {
         return desc_;
      }

      /**
       * 
       * @param desc string describing this skin
       * @return the skinmode associated with the given string (if any)
       * throws IllegalArgumentException when no matching mode is found
       */
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
   void setSkin(SkinMode mode);

   /**
    * @return the current Micro-Manager skin.
    */
   SkinMode getSkin();

   /**
    * Return the current background color for normal GUI elements.
    * @return current background color
    */
   Color getBackgroundColor();

   /**
    * Return the current "lighter" background color for highlighted or
    * otherwise differentiated GUI elements.
    * @return light background color
    */
   Color getLightBackgroundColor();

   /**
    * Return the current "disabled" background color.
    * @return "disabled" background color
    */
   Color getDisabledBackgroundColor();

   /**
    * Return the current color for enabled text.
    * @return current color for enabled text
    */
   Color getEnabledTextColor();

   /**
    * Return the current color for disabled text.
    * @return current color for disabled text.
    */
   Color getDisabledTextColor();
   
   /**
    * If the specified mode is not currently active, then we switch to that
    * mode without updating the UI. Useful if a component must be generated
    * with a nonstandard look-and-feel.
    * @param mode SkinMode to switch to (but without updating the UI)
    */
   void suspendToMode(SkinMode mode);
   
   /**
    * Restores the mode that was active before suspendToMode was called.
    */
   void resume();
}
