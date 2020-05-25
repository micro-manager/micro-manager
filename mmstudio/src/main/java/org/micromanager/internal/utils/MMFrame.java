///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2006
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
// CVS:          $Id$
//
package org.micromanager.internal.utils;

import bibliothek.gui.dock.common.DefaultMultipleCDockable;
import bibliothek.gui.dock.common.action.CAction;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JFrame;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.menus.MMMenuBar;
import org.micromanager.propertymap.MutablePropertyMapView;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;  
import java.util.ArrayList;
import javax.swing.ImageIcon;

/**
 * Base class for Micro-Manager frame windows.
 * Saves and restores window size and position. 
 */
public class MMFrame extends DefaultMultipleCDockable {
   private static final long serialVersionUID = 1L;
   private final String profileKey_;
   private final MutablePropertyMapView settings_;
   private static ArrayList<MMFrame> applicationFrames = new ArrayList<MMFrame>();

   // MMFrame profile settings contain a key for each frame subtype (defined
   // by subclass), whose value is a property map containing these key(s):
   private static enum ProfileKey {
      WINDOW_BOUNDS,
   }

   public MMFrame() {
      this("");
   }

   public MMFrame(String profileKeyForSavingBounds) {
      this(profileKeyForSavingBounds, true);
   }

   /**
    * @param profileKeyForSavingBounds
    * @param usesMMMenus If true, then the Micro-Manager menubar will be shown
    * when the frame has focus. Is effectively true by default for the other
    * constructors.
    */
   public MMFrame(String profileKeyForSavingBounds, boolean usesMMMenus) {
      super(null, (CAction[]) null);
      settings_ =
            MMStudio.getInstance().profile().getSettings(getClass());
      profileKey_ = profileKeyForSavingBounds;
      if (usesMMMenus) {
         setupMenus();
      }
      super.setTitleIcon(new ImageIcon(Toolkit.getDefaultToolkit().getImage(
        getClass().getResource("/org/micromanager/icons/microscope.gif"))));
      MMStudio.getFrame().getControl().addDockable(this);
   }

   /**
    * Create a copy of the Micro-Manager menus for use by this frame. Only
    * needed on OSX with its global (non-window-specific) menu bar.
    */
   private void setupMenus() {
      if (JavaUtils.isMac()) {
         //setJMenuBar(MMMenuBar.createMenuBar(MMStudio.getInstance()));
      }
   }

   public void loadPosition(int x, int y, int width, int height) {
   }

   public void loadPosition(int x, int y) {
   }

   /**
    * Scan the program for other MMFrames that have the same type as this
    * MMFrame, and make certain we don't precisely overlap any of them.
    */
   private void offsetIfNecessary() {
   }

    /**
    * Load window position and size from profile if possible.
    * If not possible then sets them from arguments
    * Attaches a listener to the window that will save the position when the
    * window closing event is received
    * @param x - X position of this dialog if preference value invalid
    * @param y - y position of this dialog if preference value invalid
    * @param width - width of this dialog if preference value invalid
    * @param height - height of this dialog if preference value invalid
    */
   protected void loadAndRestorePosition(int x, int y, int width, int height) {
   }
   
    /**
    * Load window position from profile if possible.
    * If not possible then sets it from arguments
    * Attaches a listener to the window that will save the position when the
    * window closing event is received
    * @param x - X position of this dialog if preference value invalid
    * @param y - y position of this dialog if preference value invalid
    */
   protected void loadAndRestorePosition(int x, int y) {
   }
   

   public void savePosition() {}
}
