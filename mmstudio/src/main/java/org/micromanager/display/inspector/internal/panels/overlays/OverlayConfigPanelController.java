/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.panels.overlays;

import com.bulenkov.iconloader.IconLoader;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayListener;
import org.micromanager.internal.utils.ReportingUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** @author mark */
final class OverlayConfigPanelController implements OverlayListener {
  private final OverlaysInspectorPanelController parentController_;
  private final Overlay overlay_;

  private final JPanel panel_;

  private final JLabel titleLabel_;
  private final JButton removeButton_;
  private final JCheckBox enabledCheckBox_;

  static OverlayConfigPanelController create(
      OverlaysInspectorPanelController parent, Overlay overlay) {
    return new OverlayConfigPanelController(parent, overlay);
  }

  private OverlayConfigPanelController(OverlaysInspectorPanelController parent, Overlay overlay) {
    parentController_ = parent;
    overlay_ = overlay;

    panel_ = new JPanel(new MigLayout(new LC().insets("0").gridGap("0", "0").fill()));

    titleLabel_ = new JLabel(overlay_.getTitle());
    titleLabel_.setFont(titleLabel_.getFont().deriveFont(Font.BOLD));
    titleLabel_.setOpaque(true);
    titleLabel_.setBackground(Color.GRAY);
    titleLabel_.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
    panel_.add(titleLabel_, new CC().gapBefore("rel").growX().pushX().split(3));

    enabledCheckBox_ = new JCheckBox("Show");
    enabledCheckBox_.setSelected(overlay_.isVisible());
    enabledCheckBox_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            parentController_.handleEnableOverlay(overlay_, enabledCheckBox_.isSelected());
          }
        });
    panel_.add(enabledCheckBox_, new CC());

    removeButton_ = new JButton("Remove", IconLoader.getIcon("/org/micromanager/icons/cross.png"));
    removeButton_.setHorizontalAlignment(SwingConstants.LEFT);
    removeButton_.setPreferredSize(new Dimension(removeButton_.getPreferredSize().width, 20));
    removeButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            parentController_.handleRemoveOverlay(overlay_);
          }
        });
    panel_.add(removeButton_, new CC().height("pref:pref:pref").gapAfter("rel").wrap());

    // Overlays are allowed not to have a UI
    JComponent configUI = overlay_.getConfigurationComponent();
    if (configUI != null) {
      panel_.add(configUI, new CC().grow().push());
    }
    panel_.validate();

    ReportingUtils.logMessage("Class: " + this.getClass());
    ReportingUtils.logMessage("Classloader: " + this.getClass().getClassLoader());
  }

  JPanel getConfigPanel() {
    return panel_;
  }

  @Override
  public void overlayTitleChanged(Overlay overlay) {
    titleLabel_.setText(overlay.getTitle());
  }

  @Override
  public void overlayConfigurationChanged(Overlay overlay) {}

  @Override
  public void overlayVisibleChanged(Overlay overlay) {
    enabledCheckBox_.setSelected(overlay.isVisible());
  }
}
