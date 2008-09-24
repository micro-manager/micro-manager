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
import ij.plugin.PlugIn;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.image5d.Crop_Image5D;
import org.micromanager.image5d.Duplicate_Image5D;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Make_Montage;
import org.micromanager.image5d.Z_Project;

/**
 * ImageJ plugin wrapper for Micro-Manager.
 */
public class MMStudioPlugin implements PlugIn, CommandListener {
   static MMStudioMainFrame frame_;
    
   @SuppressWarnings("unchecked")
   public void run(String arg) {
      if (!IJ.versionLessThan("1.39l")){
         Executer.addCommandListener(this);
      }

      try {
         // create and display control panel frame
         if (System.getProperty("os.name").indexOf("Mac OS X") != -1) {
            // on the Mac, try using a native file opener when it is present
            try {
               // test if quaqua is in the class path
               @SuppressWarnings("unused")
               Class c = Class.forName("ch.randelshofer.quaqua.QuaquaLookAndFeel");

               UIManager.setLookAndFeel(
                 "ch.randelshofer.quaqua.QuaquaLookAndFeel");
                 // set UI manager properties here that affect Quaqua
                 //          ...
                System.setProperty("apple.laf.useScreenMenuBar", "true");
            } catch (ClassNotFoundException e) {
               UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
               e.printStackTrace();
               UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
         } else
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

         if (frame_ == null || !frame_.isRunning()) {
            frame_ = new MMStudioMainFrame(true);
            frame_.setVisible(true);
            frame_.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
         } else {
            JOptionPane.showMessageDialog(frame_, "Another instance of Micro-Manager already running.\n" +
            "Only one instance allowed.");
         }
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
    
    public String commandExecuting(String command) { 
       if (command.equalsIgnoreCase("Quit") && frame_ != null) {
          frame_.closeSequence();
          return command;
       } else if (command.equals("Duplicate...") && IJ.getImage() instanceof Image5D) {
          Duplicate_Image5D duplicate = new Duplicate_Image5D();
          duplicate.run("");
          return null;
       } else if (command.equals("Crop") && IJ.getImage() instanceof Image5D) {
          Crop_Image5D crop = new Crop_Image5D();
          crop.run("");
          return null;
       } else if (command.equals("Z Project...") && IJ.getImage() instanceof Image5D) {
          Z_Project projection = new Z_Project();
          projection.run("");
          return null;
       } else if (command.equals("Make Montage...") && IJ.getImage() instanceof Image5D) {
          Make_Montage makeMontage = new Make_Montage();
          makeMontage.run("");
          return null;
       }
       return command;
    }
    
    public static CMMCore getMMCoreInstance() {
       if (frame_ == null || !frame_.isRunning())
          return null;
       else
          return frame_.getMMCore();
    }

}
