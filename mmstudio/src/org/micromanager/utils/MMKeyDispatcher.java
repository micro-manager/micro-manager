///////////////////////////////////////////////////////////////////////////////
// FILE:          MMKeyDispatcher.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

// COPYRIGHT:    University of California, San Francisco, 2008

// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.

// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.utils;

import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;

/**
 * Application-wide key dispatcher
 * It is necessary to use this approach since otherwise ImageJ will steal the
 * shortcuts before we get them
 * Downside is that all keyevents in the application will go through here
 * @author nico
 */
public class MMKeyDispatcher implements KeyEventDispatcher{
   Class textCanvasClass = null;
   final Class [] forbiddenClasses_;

   public MMKeyDispatcher() {
      try {
         textCanvasClass = ClassLoader.getSystemClassLoader().loadClass("ij.text.TextCanvas");
      } catch (ClassNotFoundException ex) {
         textCanvasClass = null;
         ReportingUtils.logError(ex);
      }

      /*
       * If there are other areas in the application in which keyevents should
       * not be processed, add those here
       */
      Class [] forbiddenClasses = {
         java.awt.TextComponent.class,
         javax.swing.text.JTextComponent.class,
         org.fife.ui.rsyntaxtextarea.RSyntaxTextArea.class,
         textCanvasClass
      };
      forbiddenClasses_ = forbiddenClasses;
   }

   /*
    * Exclude key events coming from specific sources (like text components)
    * Only way I could come up with was introspection
    */
   private boolean checkSource(KeyEvent ke) {
      Object source = ke.getSource();
      for (Class clazz:forbiddenClasses_) {
         if (clazz != null && clazz.isInstance(source))
            return false;
      }
      return true;
   }

   /*
    * 
    */
   @Override
   public boolean dispatchKeyEvent(KeyEvent ke) {
      if (!HotKeys.active_)
         return false;
      if (ke.getID() != KeyEvent.KEY_PRESSED)
         return false;

      // Since all key events in the application go through here
      // we need to efficiently determine whether or not to deal with this
      // key event will be dealt with.  CheckSource seems relatively expensive
      // so only call this when the key matches

      if (HotKeys.keys_.containsKey(ke.getKeyCode())) {
         if (checkSource(ke))
            return HotKeys.keys_.get(ke.getKeyCode()).ExecuteAction();
      }
      return false;
   }
}
