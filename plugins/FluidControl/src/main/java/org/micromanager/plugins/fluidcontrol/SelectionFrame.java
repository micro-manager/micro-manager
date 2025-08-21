package org.micromanager.plugins.fluidcontrol;

import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;

public class SelectionFrame extends JFrame {
   private final Studio studio_;
   private final SelectionPanel selectionPanel;
   private final Config config_;

   SelectionFrame(Studio studio, Config config) {
      super("Select your pressure controllers.");
      studio_ = studio;
      config_ = config;

      super.setLayout(new MigLayout("fill, insets 2, gap 2, flowx"));
      super.setResizable(false);
      super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

      // Create panel
      selectionPanel = new SelectionPanel(studio_, config_);
      this.add(selectionPanel);

      // Some window positioning
      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(100, 100);
      WindowPositioning.setUpLocationMemory(
            this,
            this.getClass(),
            null);
      super.pack();
   }
}