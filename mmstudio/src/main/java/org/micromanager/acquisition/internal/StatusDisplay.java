///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    Open Imaging, Inc. 2016
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

package org.micromanager.acquisition.internal;

import com.google.common.eventbus.Subscribe;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Datastore;
import org.micromanager.data.NewImageEvent;
import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.DisplayAboutToShowEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.Studio;

/**
 * This class shows a status display when an acquisition is started, to let
 * the user know that something is happening prior to images being displayed.
 * It disappears as soon as any images are available for the acquisition.
 */
public class StatusDisplay extends JFrame {
   private static final int DISPLAY_DELAY_MS = 1000;

   private Datastore store_;
   private Studio studio_;
   private boolean hasVisibleContent_ = false;

   public StatusDisplay(Studio studio, Datastore store) {
      studio_ = studio;
      store_ = store;

      // Start a thread to show us after a short delay.
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               Thread.sleep(DISPLAY_DELAY_MS);
            }
            catch (InterruptedException e) {
               return;
            }
            if (!hasVisibleContent_ &&
               studio_.acquisitions().isAcquisitionRunning()) {
               SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                     showStatusDisplay();
                  }
               });
            }
         }
      }).start();
      studio_.events().registerForEvents(this);
   }

   /**
    * No visible content for this acquisition has shown up, so show our
    * status display.
    */
   private void showStatusDisplay() {
      setUndecorated(true);
      JPanel contents = new JPanel(new MigLayout("flowy"));
      contents.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
      contents.add(new JLabel("Acquisition started, waiting for images..."));
      add(contents);
      pack();
      // Put us centered, on the same display as the main window.
      JFrame mainFrame = ((MMStudio) studio_).getFrame();
      GraphicsConfiguration config = GUIUtils.getGraphicsConfigurationContaining(mainFrame.getLocation().x, mainFrame.getLocation().y);
      Dimension size = getSize();
      Rectangle bounds = config.getBounds();
      setLocation(bounds.x + bounds.width / 2 - size.width / 2,
            bounds.y + bounds.height / 2 - size.height / 2);
      setVisible(true);
   }

   @Subscribe
   public void onDisplayAboutToShow(DisplayAboutToShowEvent event) {
      if (event.getDisplay().getDatastore() == store_) {
         // There is visible content for our datastore.
         hasVisibleContent_ = true;
         dispose();
      }
   }

   @Subscribe
   public void onAcquisitionEnded(AcquisitionEndedEvent event) {
      if (event.getStore() == store_) {
         // All done here.
         studio_.events().unregisterForEvents(this);
         dispose();
      }
   }
}
