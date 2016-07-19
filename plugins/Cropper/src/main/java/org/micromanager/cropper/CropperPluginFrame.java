///////////////////////////////////////////////////////////////////////////////
//FILE:          CropperPluginFrame.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     Cropper plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2016
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

package org.micromanager.cropper;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.MMDialog;

/**
 *
 * @author nico
 */
public class CropperPluginFrame extends MMDialog {
   private final Studio studio_;
   private final Font font_;
   private final Dimension buttonSize_;
   private final DisplayWindow ourWindow_;
   private final Datastore ourStore_;
   
   public CropperPluginFrame (Studio studio, DisplayWindow window) {
      studio_ = studio;
      
      this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      this.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            dispose();
         }
      }
      );
      font_ = new Font("Arial", Font.PLAIN, 12);
      buttonSize_ = new Dimension(70, 21);
      
      ourWindow_ = window;
      ourStore_ = ourWindow_.getDatastore();
      
      // Not sure if this is needed, be safe for now
      if (!ourStore_.getIsFrozen()) {
         studio_.logs().showMessage("Can not crop ongoing acquisitions");
         super.dispose();
         return;
      }
      
      this.setLayout(new MigLayout("flowx, fill, insets 8"));
      this.setTitle(CropperPlugin.MENUNAME);

      super.loadAndRestorePosition(100, 100, 375, 275);
      
      List<String> axes = ourStore_.getAxes();
      
      for (String axis : axes) {
         
      }
      
      
      
   }
   
}
