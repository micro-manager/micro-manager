/**
 * ExampleFrame.java
 *
 * <p>This module shows an example of creating a GUI (Graphical User Interface).
 * There are many ways to do this in Java; this particular example uses the
 * MigLayout layout manager, which has extensive documentation online.
 *
 * <p>Nico Stuurman, copyright UCSF, 2012, 2015
 *
 * <p>LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * <p>This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.plugins.example;

import com.google.common.eventbus.Subscribe;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.internal.utils.WindowPositioning;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.


public class iSIMFrame extends JFrame {

   private Studio studio_;

   public iSIMFrame(Studio studio) {
      super("iSIM Control Panel");
      studio_ = studio;

      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));

      JLabel title = new JLabel("iSIM Controller");
      title.setFont(new Font("Arial", Font.BOLD, 14));
      super.add(title, "span, alignx center, wrap");

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      super.pack();

      // Registering this class for events means that its event handlers
      // (that is, methods with the @Subscribe annotation) will be invoked when
      // an event occurs. You need to call the right registerForEvents() method
      // to get events; this one is for the application-wide event bus, but
      // there's also Datastore.registerForEvents() for events specific to one
      // Datastore, and DisplayWindow.registerForEvents() for events specific
      // to one image display window.
      studio_.events().registerForEvents(this);
   }

   /**
    * To be invoked, this method must be public and take a single parameter
    * which is the type of the event we care about.
    *
    * @param event
    */
   @Subscribe
   public void onExposureChanged(ExposureChangedEvent event) {
      studio_.alerts().postAlert(
         "",
         iSIMFrame.class,
         String.format(
               "Camera %s exposure time set to %.2fms",
               event.getCameraName(),
               event.getNewExposureTime()
         )
      );
   }

   @Subscribe
   public void onLiveModeChanged(LiveModeEvent event) {
      if (event.isOn()) {
         studio_.alerts().postAlert("", iSIMFrame.class, "Live mode started");
      } else {
         studio_.alerts().postAlert("", iSIMFrame.class, "Live mode stopped");
      }
   }
}
