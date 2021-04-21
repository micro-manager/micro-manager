package org.micromanager.internal.dialogs;

import org.micromanager.Studio;
import org.micromanager.acquisition.internal.AcquisitionEngine;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.WindowPositioning;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class provides a dialog available from the "Advanced" button on the AcqConrolDlg (MDA setup
 * window) in the timepoints panel. It allows users to set custom time intervals, i.e. nonuniform
 * timing for a series of frames.
 */
public final class CustomTimesDialog extends JDialog {

  private static final long serialVersionUID = 1L;
  private final AcquisitionEngine acqEng_;
  private final JPanel closeButtonPanel_;
  private final CustomTimeIntervalsPanel customTimeIntervalsPanel_;

  public CustomTimesDialog(AcquisitionEngine acqEng, Studio gui) {
    this.setModal(true);
    acqEng_ = acqEng;
    super.setBounds(50, 50, 500, 450);
    super.setMinimumSize(new Dimension(400, 300));
    WindowPositioning.setUpBoundsMemory(this, this.getClass(), "CustomDialog");

    setTitle("Custom Timepoints Configuration");
    super.setIconImage(
        Toolkit.getDefaultToolkit()
            .getImage(MMStudio.class.getResource("/org/micromanager/icons/microscope.gif")));

    customTimeIntervalsPanel_ = new CustomTimeIntervalsPanel(acqEng_, gui, this);
    getContentPane().add(customTimeIntervalsPanel_);

    closeButtonPanel_ = new JPanel();
    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(
        new ActionListener() {

          @Override
          public void actionPerformed(ActionEvent e) {
            close();
          }
        });
    closeButtonPanel_.add(closeButton);
    this.getContentPane().add(closeButtonPanel_, BorderLayout.PAGE_END);
  }

  @Override
  public void setVisible(boolean b) {
    updatePanels();
    super.setVisible(b);
  }

  // make sure that all panels display data currently in acqEng upon becoming visible
  private void updatePanels() {
    customTimeIntervalsPanel_.syncCheckBoxFromAcqEng();
    customTimeIntervalsPanel_.syncIntervalsFromAcqEng();
  }

  private void close() {
    this.setVisible(false);
  }
}
