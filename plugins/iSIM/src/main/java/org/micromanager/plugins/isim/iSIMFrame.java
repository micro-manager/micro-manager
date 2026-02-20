package org.micromanager.plugins.isim;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;

public class iSIMFrame extends JFrame {
   private static final Color STATUS_NORMAL_BG = new Color(0, 160, 0);
   private static final Color STATUS_ALIGNMENT_BG = new Color(0, 120, 200);
   private static final Color STATUS_TEXT_FG = Color.WHITE;

   private final Studio studio_;
   private final AlignmentPanel alignmentPanel_;
   private final JLabel statusLabel_;

   public iSIMFrame(Studio studio, String deviceLabel) {
      super("iSIM Control Panel");
      studio_ = studio;

      setLayout(new MigLayout("fill, insets 0, gap 0, flowy"));
      setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));

      AlignmentModel model = new AlignmentModel(
            studio_.profile().getSettings(AlignmentModel.class));
      alignmentPanel_ = new AlignmentPanel(studio_, model, this, deviceLabel);

      JPanel waveformsPlaceholder = new JPanel();
      waveformsPlaceholder.add(new JLabel("Waveforms — coming soon"));

      JTabbedPane tabbedPane = new JTabbedPane();
      tabbedPane.addTab("Waveforms", waveformsPlaceholder);
      tabbedPane.addTab("Alignment", alignmentPanel_);
      add(tabbedPane, "grow, push");

      // Status panel
      statusLabel_ = new JLabel("Ready");
      statusLabel_.setForeground(STATUS_TEXT_FG);
      statusLabel_.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
      JPanel statusPanel = new JPanel(new MigLayout("fill, insets 0"));
      statusPanel.setBackground(STATUS_NORMAL_BG);
      statusPanel.add(statusLabel_, "aligny center");
      add(statusPanel, "growx, pushx");

      alignmentPanel_.syncWithDeviceState();

      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            alignmentPanel_.onWindowClosing();
            studio_.events().unregisterForEvents(iSIMFrame.this);
         }
      });

      pack();
   }

   /**
    * Updates the status panel color and text to reflect the current state.
    *
    * @param inAlignmentMode true if alignment mode is active
    */
   public void setStatus(boolean inAlignmentMode) {
      if (inAlignmentMode) {
         statusLabel_.getParent().setBackground(STATUS_ALIGNMENT_BG);
         statusLabel_.setText("Alignment mode active");
      } else {
         statusLabel_.getParent().setBackground(STATUS_NORMAL_BG);
         statusLabel_.setText("Ready");
      }
   }
}
