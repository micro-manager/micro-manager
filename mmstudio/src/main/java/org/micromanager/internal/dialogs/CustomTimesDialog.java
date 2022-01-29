package org.micromanager.internal.dialogs;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import org.micromanager.Studio;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * This class provides a dialog available from the "Advanced" button
 * on the AcqConrolDlg (MDA setup window) in the timepoints panel. It allows
 * users to set custom time intervals, i.e. nonuniform timing for a series of
 * frames.
 */
public final class CustomTimesDialog extends JDialog {

   private static final long serialVersionUID = 1L;
   private final CustomTimeIntervalsPanel customTimeIntervalsPanel_;

   /**
    * Creates the Custom Time Dialog.  Originally coded by Henry Pinkard.
    *
    * @param acqEng Acquisition Engine Object.
    * @param gui The omnipresent Studio object.
    */
   public CustomTimesDialog(AcquisitionEngine acqEng, Studio gui) {
      this.setModal(true);
      super.setBounds(50, 50, 500, 450);
      super.setMinimumSize(new Dimension(400, 300));
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), "CustomDialog");

      setTitle("Custom Timepoints Configuration");
      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            MMStudio.class.getResource(
                  "/org/micromanager/icons/microscope.gif")));


      customTimeIntervalsPanel_ = new CustomTimeIntervalsPanel(acqEng, gui,
           this);
      getContentPane().add(customTimeIntervalsPanel_);

      final JPanel closeButtonPanel = new JPanel();
      final JButton closeButton = new JButton("Close");
      closeButton.addActionListener(e -> close());
      closeButtonPanel.add(closeButton);
      this.getContentPane().add(closeButtonPanel, BorderLayout.PAGE_END);

   }

   @Override
   public void setVisible(boolean b) {
      updatePanels();
      super.setVisible(b);
   }

   //make sure that all panels display data currently in acqEng upon becoming visible
   private void updatePanels() {
      customTimeIntervalsPanel_.syncCheckBoxFromAcqEng();
      customTimeIntervalsPanel_.syncIntervalsFromAcqEng();
   }

   private void close() {
      this.setVisible(false);
   }
}
