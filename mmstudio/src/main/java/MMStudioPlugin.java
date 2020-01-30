///////////////////////////////////////////////////////////////////////////////
// FILE:          MMStudioPlugin.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//                Mark A. Tsuchida, 2016
//
// COPYRIGHT:     2006-2015 Regents of the University of California
//                2016 Open Imaging, Inc.
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

import ij.CommandListener;
import ij.Executer;
import ij.IJ;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.micromanager.display.internal.displaywindow.imagej.MMVirtualStack;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * ImageJ plugin wrapper for Micro-Manager.
 */
public class MMStudioPlugin implements PlugIn, CommandListener {
   volatile static MMStudio studio_;

   /**
    * Run Micro-Manager as an ImageJ plugin
    * @param arg the plugin argument (not used)
    */
   @Override
   public void run(final String arg) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            try {
               if (studio_ == null || !studio_.getIsProgramRunning()) {
                  // OS-specific stuff
                  // TODO Why here and not in MMStudio?
                  if (JavaUtils.isMac()) {
                     System.setProperty("apple.laf.useScreenMenuBar", "true");
                  }
                  try {
                     UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                  }
                  catch (ClassNotFoundException | IllegalAccessException | 
                          InstantiationException | UnsupportedLookAndFeelException e) {
                     ReportingUtils.logError(e, "Failed to set look-and-feel");
                  }

                  // warn user about old ImageJ version, but do not stop
                  // TODO Cf. check in MMStudio
                  IJ.versionLessThan("1.48g");

                  if (!IJ.versionLessThan("1.46e")) {
                     Executer.addCommandListener(MMStudioPlugin.this);
                  }

                  studio_ = new MMStudio(true);
               }
            } catch (RuntimeException e) {
               ReportingUtils.logError(e);
            } catch (Exception e) {
               ReportingUtils.logError(e);
            }
         }
      });
   }


   private boolean closed_;
   private boolean closeStudio() {
      try {
         GUIUtils.invokeAndWait(new Runnable() {
            @Override
            public void run() {
               closed_ = studio_.closeSequence(true);
            }
         });
      }
      catch (InterruptedException ex) {
         closed_ = false;
         Thread.currentThread().interrupt();
      }
      catch (InvocationTargetException ignore) {
         closed_ = false;
      }
      return closed_;
   }


   /**
    * Override ImageJ commands for Micro-Manager
    * @param command
    * @return command, or null if command is canceled
    */
   @Override
   public String commandExecuting(String command) {
      if (command.equalsIgnoreCase("Quit") && studio_ != null) {
         if (closeStudio()) {
            Executer.removeCommandListener(MMStudioPlugin.this);
            return command;
         }
         return null;
      }
      else if (command.equals("Crop")) {
         // Override in-place crop (which won't work) with cropped duplication
         // TODO Support stack cropping
         if (IJ.getImage().getStack() instanceof MMVirtualStack) {
            new Duplicator().run(IJ.getImage()).show();
            return null;
         } 
      }
      // TODO We could support some more non-modifying commands
      return command;
   }
}