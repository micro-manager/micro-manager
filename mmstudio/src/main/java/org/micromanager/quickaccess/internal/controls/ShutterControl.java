///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.quickaccess.internal.controls;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.events.AutoShutterEvent;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.events.ShutterEvent;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.quickaccess.WidgetPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Allows you to open and close the shutter, and toggle autoshutter.
 */
@Plugin(type = WidgetPlugin.class)
public final class ShutterControl extends WidgetPlugin implements SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Shutter Control";
   }

   @Override
   public String getHelpText() {
      return "Open and close the shutter, or toggle autoshutter.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) 2015 Open Imaging, Inc.";
   }

   @Override
   public ImageIcon getIcon() {
      return new ImageIcon(IconLoader.loadFromResource(
               "/org/micromanager/icons/shutter_open.png"));
   }

   // We are not actually configurable; config is ignored.
   @Override
   public JComponent createControl(PropertyMap config) {
      JPanel panel = new JPanel(new MigLayout("flowy, gap 0, insets 0"));
      panel.add(makeShutterIcon(studio_), "aligny center, wrap");
      panel.add(makeAutoShutterCheckBox(studio_), "alignx center, split 2");
      panel.add(makeShutterButton(studio_));
      return panel;
   }

   /**
    * Create an icon that indicates the current shutter state.
    */
   public static JLabel makeShutterIcon(final Studio studio) {
      final JLabel icon = new JLabel();
      // Must create a separate object to register for events, because
      // otherwise the Java compiler complains when we modify "icon" that
      // it might not have been initialized.
      Object registrant = new Object() {
         @Subscribe
         public void onShutter(ShutterEvent event) {
            updateIcon(icon, studio);
         }
         @Subscribe
         public void onAutoShutter(AutoShutterEvent event) {
            updateIcon(icon, studio);
         }
         @Subscribe
         public void onGUIRefresh(GUIRefreshEvent event) {
            updateIcon(icon, studio);
         }
      };
      // Toggle icon state when mouse is pressed.
      icon.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            try {
               if (studio.shutter().getAutoShutter()) {
                  studio.shutter().setAutoShutter(false);
               }
               studio.shutter().setShutter(!studio.shutter().getShutter());
            }
            catch (Exception ex) {
               studio.logs().logError(ex, "Error toggling shutter icon");
            }
         }
      });
      updateIcon(icon, studio);
      studio.events().registerForEvents(registrant);
      return icon;
   }

   /**
    * Update the JLabel's icon based on the current state of the shutter and
    * autoshutter.
    */
   private static void updateIcon(JLabel icon, Studio studio) {
      String path = "/org/micromanager/icons/shutter_";
      String tooltip = "The shutter is ";
      // Note that we use the mode both for the tooltip and for
      // constructing the image file path, since it happens to work out.
      try {
         String mode = studio.shutter().getShutter() ? "open" : "closed";
         path += mode;
         tooltip += mode;
         if (studio.shutter().getAutoShutter()) {
            path += "_auto";
            tooltip += ". Autoshutter is enabled";
         }
         path += ".png";
         tooltip += ". Click to open or close the shutter.";
         icon.setIcon(IconLoader.getIcon(path));
         icon.setToolTipText(tooltip);
      }
      catch (Exception e) {
         studio.logs().logError(e, "Unable to update shutter state display");
      }
   }

   /**
    * Create a checkbox for turning autoshutter on/off.
    */
   public static JCheckBox makeAutoShutterCheckBox(final Studio studio) {
      final JCheckBox toggle = new JCheckBox("Auto");
      // Must create a separate object to register for events, because
      // otherwise the Java compiler complains when we modify "toggle" that
      // it might not have been initialized.
      Object registrant = new Object() {
         @Subscribe
         public void onAutoShutter(AutoShutterEvent event) {
            toggle.setSelected(event.getAutoShutter());
         }
         @Subscribe
         public void onGUIRefresh(GUIRefreshEvent event) {
            toggle.setSelected(studio.shutter().getAutoShutter());
         }
      };
      toggle.setFont(GUIUtils.buttonFont);
      toggle.setSelected(studio.shutter().getAutoShutter());
      toggle.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               studio.shutter().setAutoShutter(toggle.isSelected());
            }
            catch (Exception ex) {
               studio.logs().showError(ex, "Error setting auto shutter");
            }
         }
      });
      studio.events().registerForEvents(registrant);
      return toggle;
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      return PropertyMaps.builder().build();
   }

   /**
    * Create a button for opening/closing the shutter.
    */
   public static JButton makeShutterButton(final Studio studio) {
      final JButton button = new JButton();
      // Must create a separate object to register for events, because
      // otherwise the Java compiler complains when we modify "button" that
      // it might not have been initialized.
      Object registrant = new Object() {
         @Subscribe
         public void onShutter(ShutterEvent event) {
            button.setText(event.getShutter() ? "Close" : "Open");
         }

         @Subscribe
         public void onAutoShutter(AutoShutterEvent event) {
            button.setEnabled(!event.getAutoShutter());
         }

         @Subscribe
         public void onGUIRefresh(GUIRefreshEvent event) {
            try {
               button.setText(studio.shutter().getShutter() ? "Close" : "Open");
            }
            catch (Exception e) {
               studio.logs().logError(e, "Error getting shutter state");
            }
            button.setEnabled(!studio.shutter().getAutoShutter());
         }
      };
      button.setEnabled(!studio.shutter().getAutoShutter());
      try {
         button.setText(studio.shutter().getShutter() ? "Close" : "Open");
      }
      catch (Exception e) {
         studio.logs().logError(e, "Unable to get shutter state");
      }
      button.setFont(GUIUtils.buttonFont);
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               studio.shutter().setShutter(!studio.shutter().getShutter());
            }
            catch (Exception ex) {
               studio.logs().logError(ex, "Unable to toggle shutter");
            }
         }
      });
      studio.events().registerForEvents(registrant);
      return button;
   }
}
