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
// CVS:           $Id$

import ij.plugin.PlugIn;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;

/**
 * ImageJ plugin wrapper for Micro-Manager.
 */
public class MMStudioPlugin implements PlugIn {
   static MMStudioMainFrame frame_;
    
    public void run(String arg) {
        try {
            // create and display control panel frame
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
    
    public static CMMCore getMMCoreInstance() {
       if (frame_ == null || !frame_.isRunning())
          return null;
       else
          return frame_.getMMCore();
    }

}
