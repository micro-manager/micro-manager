///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    (c) 2016 Open Imaging, Inc.
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
//

package org.micromanager.alerts.internal;

import com.bulenkov.iconloader.IconLoader;
import net.miginfocom.swing.MigLayout;
import org.micromanager.alerts.UpdatableAlert;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DefaultAlert extends JPanel implements UpdatableAlert {

  protected AlertsWindow parent_;
  private final String title_;
  private JLabel textLabel_ = null;
  protected String text_;
  protected JComponent contents_;
  private JToggleButton muteButton_;
  private boolean isUsable_ = true;

  /**
   * @param parent Alerts Window that created this alert
   * @param title Title of the alert
   * @param contents contents to be added to the alert
   */
  protected DefaultAlert(AlertsWindow parent, String title, JComponent contents) {
    super();
    super.setLayout(new MigLayout("flowx, fill, insets 1, gap 0", "[]2[]"));

    parent_ = parent;
    title_ = title;
    contents_ = contents;
    // HACK: if contents are a JLabel, store their text.
    if (contents instanceof JLabel) {
      textLabel_ = (JLabel) contents;
      text_ = textLabel_.getText();
    }

    // Create a header with title (if available), close button, and mute
    // button.
    JPanel header = new JPanel(new MigLayout("flowx, fillx, insets 0, gap 2"));
    if (title != null && !title.contentEquals("")) {
      JLabel titleLabel = new JLabel(title);
      titleLabel.setFont(new Font("Arial", Font.BOLD, 12));
      header.add(titleLabel);
    }

    // This icon based on the public-domain icon at
    // https://commons.wikimedia.org/wiki/File:Echo_bell.svg
    muteButton_ = new JToggleButton(IconLoader.getIcon("/org/micromanager/icons/bell_mute.png"));
    muteButton_.setToolTipText(
        "Mute this message source, so that it will no longer cause the Messages window to be shown if it recurs");
    muteButton_.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            parent_.setMuted(DefaultAlert.this, muteButton_.isSelected());
          }
        });
    header.add(muteButton_, "gapleft push, growx, pushx, width 32!, height 32!");

    JButton closeButton =
        new JButton(IconLoader.getIcon("/org/micromanager/icons/cancel_gray.png"));
    closeButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            dismiss();
          }
        });
    header.add(closeButton, "width 32!, height 32!");
    super.add(header, "growx, pushx 100, span, wrap");

    contents.setBorder(BorderFactory.createLineBorder(Color.BLACK));
    super.add(contents_, "grow, pushx 100");
  }

  @Override
  public void dismiss() {
    isUsable_ = false;
    parent_.removeAlert(this);
  }

  /**
   * Returns whether or not this alert can have more content added to it.
   *
   * @return true if more content can be added
   */
  @Override
  public boolean isUsable() {
    return isUsable_;
  }

  @Override
  public void setText(String text) {
    text_ = text;
    if (textLabel_ != null) {
      textLabel_.setText(text);
    }
    parent_.textUpdated(this);
  }

  @Override
  public String getText() {
    return text_;
  }

  public JComponent getContents() {
    return contents_;
  }

  public String getTitle() {
    return title_;
  }

  public void setMuteButtonState(boolean isMuted) {
    muteButton_.setSelected(isMuted);
  }
}
