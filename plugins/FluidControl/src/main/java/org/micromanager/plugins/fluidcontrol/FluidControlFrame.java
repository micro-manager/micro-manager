package org.micromanager.plugins.fluidcontrol;

import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.WindowConstants;

import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.internal.utils.WindowPositioning;

public class FluidControlFrame extends JFrame {
   private final Studio studio_;
   private Config config_;
   private PropertyChangeListener pcl_;

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
      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(
            this,
            this.getClass(),
            null);

      super.pack();
      studio_.events().registerForEvents(this);
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
      newConfig.addActionListener(e -> configureAction());

      // Action to be performed upon clicking "Menu->About"
      openConfig.addActionListener(e -> aboutAction());

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
      aboutFrame_.setLocation(this.getLocation());
      aboutFrame_.setVisible(true);
   }

   @Subscribe
   public void onConfigurationLoaded(SystemConfigurationLoadedEvent scle) {
      redrawPumps();
   }
}
