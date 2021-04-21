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

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Subclass of DefaultAlert intended for showing multiple lines of alert texts, broken out into
 * subcategories and using a scrollpane if necessary.
 */
public final class CategorizedAlert extends DefaultAlert {

  private String historyText_ = "";

  /** This class represents one row in the display, one category of alert type. */
  private class CategoryDisplay extends JPanel {
    private int numMessages_ = 0;
    private JToggleButton showAllButton_ = new JToggleButton("Show All");
    private final JTextArea mostRecentText_ = new JTextArea("");
    private JPanel historyPanel_ = new JPanel(new MigLayout("fillx, insets 0, gap 0, flowy"));

    public CategoryDisplay() {
      super(new MigLayout("fillx, insets 1, gap 0, flowx"));
      // These are only shown once we have at least 2 messages.
      showAllButton_.setVisible(false);
      historyPanel_.setVisible(false);
      super.add(historyPanel_, "growx, wrap, hidemode 2");

      showAllButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      showAllButton_.addActionListener(
          new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              boolean isSelected = showAllButton_.isSelected();
              setBorder(isSelected ? BorderFactory.createLineBorder(Color.GRAY, 1) : null);
              showAllButton_.setText(isSelected ? "Hide" : "Show All " + numMessages_);
              historyPanel_.setVisible(isSelected);
            }
          });

      mostRecentText_.setLineWrap(true);
      mostRecentText_.setWrapStyleWord(true);
      // NOTE: these width parameters (as with all JTextAreas we add) are
      // needed to work around issues with how JTextAreas grow/shrink when
      // placed inside of JScrollPanes. See
      // http://stackoverflow.com/questions/6023145/line-wrap-in-a-jtextarea-causes-jscrollpane-to-missbehave-with-miglayout
      super.add(mostRecentText_, "growx, pushx, alignx left, width 0:250:");
      // This will always be invisible if showOnlyText is true, as it means
      // that addText will only ever be called once for us.
      super.add(showAllButton_, "gapleft 2, width 90!, height 20!, hidemode 2");
    }

    /** Set the current text display, pushing any old text display into the history. */
    public void addText(String text) {
      if (!mostRecentText_.getText().contentEquals("")) {
        // Have some recent text that needs to be filed away.
        JTextArea newHistory = new JTextArea(mostRecentText_.getText());
        newHistory.setLineWrap(true);
        newHistory.setWrapStyleWord(true);
        historyPanel_.add(newHistory, "growx, alignx left, width 0:250:");
      }
      mostRecentText_.setText(text);
      historyText_ += text + System.getProperty("line.separator");
      numMessages_++;
      if (numMessages_ > 2) {
        showAllButton_.setText((showAllButton_.isSelected() ? "Hide" : "Show All " + numMessages_));
        if (!showAllButton_.isVisible()) {
          showAllButton_.setVisible(true);
        }
      }
    }
  }

  // This tracks all of our different categories and the message history for
  // each.
  private final HashMap<Class<?>, CategoryDisplay> categories_ =
      new HashMap<Class<?>, CategoryDisplay>();
  // CategoryDisplays that have a null category.
  private final ArrayList<CategoryDisplay> nullCategories_ = new ArrayList<CategoryDisplay>();

  /**
   * Sets up the contents of the alert before passing them to the constructor, so they can in turn
   * be passed to the DefaultAlert constructor.
   */
  public static CategorizedAlert createAlert(AlertsWindow parent, String title) {
    JPanel body = new JPanel(new MigLayout("fill, flowy, insets 0, gap 0"));
    return new CategorizedAlert(parent, title, body);
  }

  private CategorizedAlert(AlertsWindow parent, String title, JPanel contents) {
    super(parent, title, contents);
  }

  /**
   * Add a new label to our contents. Create a new DisplayCategory if necessary, otherwise move the
   * existing one to the bottom of the list.
   */
  public void addText(Class<?> category, String text) {
    CategoryDisplay display;
    if (category == null) {
      // Always create a new display for null categories.
      display = new CategoryDisplay();
      nullCategories_.add(display);
    } else if (!categories_.containsKey(category)) {
      display = new CategoryDisplay();
      categories_.put(category, display);
    } else {
      display = categories_.get(category);
      contents_.remove(display);
    }
    contents_.add(display, "growx");
    display.addText(text);
    // Update our summary text.
    text_ = text;

    // HACK: for some reason if we don't do this, our viewable area is tiny.
    parent_.pack();
    SwingUtilities.invokeLater(
        new Runnable() {
          @Override
          public void run() {
            invalidate();
            parent_.validate();
          }
        });
    parent_.textUpdated(this);
  }

  public String getAllText() {
    return historyText_;
  }
}
