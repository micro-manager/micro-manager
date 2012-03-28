///////////////////////////////////////////////////////////////////////////////
// FILE:          MMStudioPlugin.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//
// COPYRIGHT:     University of California, San Francisco, 2006
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
import ij.ImagePlus;
import ij.plugin.Duplicator;
import ij.plugin.PlugIn;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.AcquisitionVirtualStack;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * ImageJ plugin wrapper for Micro-Manager.
 */
public class MMStudioPlugin implements PlugIn, CommandListener {
   static MMStudioMainFrame frame_;
    
   @SuppressWarnings("unchecked")
   public void run(String arg) {


      try {
         if (frame_ == null || !frame_.isRunning()) {

            // OS-specific stuff
            if (JavaUtils.isMac()) {
               System.setProperty("apple.laf.useScreenMenuBar", "true");

               // TODO: this code looks bad!!
               try {
                  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
               } catch (Exception e) {
                  ReportingUtils.logError(e);
                  UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
               }
            } else {
               UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }

            // create and display control panel frame
            if (!IJ.versionLessThan("1.46e")){
               Executer.addCommandListener(this);
            }
            frame_ = new MMStudioMainFrame(true);
            frame_.setVisible(true);
            frame_.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
         }
         if (arg.equals("OpenAcq")) {
            frame_.openAcquisitionData(true);
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }
    
   public String commandExecuting(String command) { 
      if (command.equalsIgnoreCase("Quit") && frame_ != null) {
         frame_.closeSequence();
         return command;
      }  else if (command.equals("Crop")) {
         if (IJ.getImage().getStack() instanceof AcquisitionVirtualStack) {

            new Duplicator().run(IJ.getImage()).show();

            // abort further processing of the Crop command
            return null;
         } 
      } else if (command.equals("Add Noise")) {
         // blanket method to make sure that ImageJ filters do not execute on disk-cached images
         // this may backfire!
         if (IJ.getImage().getStack() instanceof AcquisitionVirtualStack) {
            AcquisitionVirtualStack avs = (AcquisitionVirtualStack) IJ.getImage().getStack();
            if (avs.getVirtualAcquisitionDisplay().isDiskCached()) {
               // duplicate the image and then run the ImageJ command on what is now the new image
               new Duplicator().run(IJ.getImage()).show();
            } else {
               // Image is not disk chached.  Warn that data will be lost
               if (!IJ.showMessageWithCancel("Micro-Manager data not saved", "Data are not saved and Undo is impossible. \n" +
                       "Do you really want to execute the command?") ) {
                  return null;
               }
            }
         }
      }

      return command;
   }

   public static MMStudioMainFrame getMMStudioMainFrameInstance() {
       return frame_;
   }

   public static CMMCore getMMCoreInstance() {
      if (frame_ == null || !frame_.isRunning())
         return null;
      else
         return frame_.getMMCore();
   }

   public static AutofocusManager getAutofocusManager() {
      if (frame_ == null || !frame_.isRunning())
         return null;
      else
         return frame_.getAutofocusManager();
   }

}
