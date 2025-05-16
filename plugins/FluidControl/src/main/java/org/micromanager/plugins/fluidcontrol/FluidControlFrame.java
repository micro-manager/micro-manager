package org.micromanager.plugins.fluidcontrol;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * Main frame of the Fluid Control GUI.
 */
public class FluidControlFrame extends JFrame {
   private final Studio studio_;
   private final Config config_;
   private final PropertyChangeListener pcl_;

   // Display members
   private JMenuBar menuBar_;
   private SelectionFrame selectionFrame_;
   private AboutFrame aboutFrame_;
   private FluidControlPanel fluidControlPanel_;

   public FluidControlFrame(Studio studio) {
      super("Fluid Controller GUI");
      super.setLayout(new MigLayout("fill, insets, gap 2, flowx"));
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      studio_ = studio;
      config_ = new Config();

      // Setup listener for change in config
      pcl_ = new PropertyChangeListener() {
         @Override
         public void propertyChange(PropertyChangeEvent evt) {
            redrawPumps();
         }
      };
      config_.addPropertyChangeListener(pcl_);

      // Setup menubar
      setupMenuBar();

      // Draw pumps
      fluidControlPanel_ = new FluidControlPanel(studio_, config_);
      this.add(fluidControlPanel_);

      // Some window positioning
      super.setIconImage(Toolkit.getDefaultToolkit()
            .getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      super.pack();
   }

   private void redrawPumps() {
      this.getContentPane().removeAll();
      fluidControlPanel_ = new FluidControlPanel(studio_, config_);
      this.add(fluidControlPanel_);
      this.repaint();
      this.pack();
   }

   private void setupMenuBar() {
      menuBar_ = new JMenuBar();
      setJMenuBar(menuBar_);
      JMenu fileMenu = new JMenu("Menu");
      JMenuItem newConfig = fileMenu.add("Configure");
      JMenuItem openConfig = fileMenu.add("About");

      // Action to be performed upon clicking "Menu->Configure"
      newConfig.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            configureAction();
         }
      });

      // Action to be performed upon clicking "Menu->About"
      openConfig.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            aboutAction();
         }
      });

      menuBar_.add(fileMenu);
   }

   /**
    * Action taken upon clicking "Menu->Configure". Opens the device selection wizard
    */
   private void configureAction() {
      selectionFrame_ = new SelectionFrame(studio_, config_);
      selectionFrame_.setVisible(true);
   }

   /**
    * Action taken upon clicking "Menu->About". Opens the device selection wizard
    */
   private void aboutAction() {
      aboutFrame_ = new AboutFrame();
      aboutFrame_.setVisible(true);
   }
}
