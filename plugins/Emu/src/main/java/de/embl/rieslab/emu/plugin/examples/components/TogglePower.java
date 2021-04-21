package de.embl.rieslab.emu.plugin.examples.components;

import java.awt.Dimension;

import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

public class TogglePower extends JToggleButton {

  private static final long serialVersionUID = 1L;

  public TogglePower() {
    this.setPreferredSize(new Dimension(30, 30));
    this.setFocusPainted(false);

    if (getClass().getResource("/images/TogglePower-off.png") != null) {
      this.setIcon(new ImageIcon(getClass().getResource("/images/TogglePower-off.png")));
      this.setSelectedIcon(new ImageIcon(getClass().getResource("/images/TogglePower-on.png")));
      this.setRolloverIcon(
          new ImageIcon(getClass().getResource("/images/TogglePower-rollover.png")));
      this.setDisabledIcon(
          new ImageIcon(getClass().getResource("/images/TogglePower-disabled.png")));
      this.setBorderPainted(false);
      this.setBorder(null);
      this.setFocusable(false);
      this.setContentAreaFilled(false);
    }
  }
}
