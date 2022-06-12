// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.inspector.internal;

import com.bulenkov.iconloader.IconLoader;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelListener;
import org.micromanager.internal.utils.PopupButton;

/**
 * Controller for a section in the inspector window.
 *
 * @author Mark A. Tsuchida, based in part on Chris Weisiger's WrapperPanel
 */
final class InspectorSectionController implements InspectorPanelListener {
   private final InspectorController inspectorController_;
   private final InspectorPanelController panelController_;
   private final JPanel panel_;
   private final JPanel contentPanel_;
   private final JPanel headerPanel_;
   private final JLabel headerLabel_;
   private final PopupButton gearButton_;
   private boolean mouseClickenabled_ = true;

   public static InspectorSectionController create(
         InspectorController inspectorController,
         InspectorPanelController panelController) {
      return new InspectorSectionController(
            inspectorController, panelController);
   }

   private InspectorSectionController(InspectorController inspectorController,
                                      InspectorPanelController panelController) {
      inspectorController_ = inspectorController;
      panelController_ = panelController;

      contentPanel_ = panelController_.getPanel();

      headerPanel_ = new JPanel(new MigLayout(
            new LC().fillX().insets("0").gridGap("0", "0")));

      headerLabel_ = new JLabel(panelController.getTitle(),
            UIManager.getIcon(panelController.initiallyExpand() ?
                  "Tree.expandedIcon" : "Tree.collapsedIcon"),
            SwingConstants.LEFT);
      // Ignore day/night settings for the label text, since the background
      // (i.e. the header panel we're in) also ignores day/night settings.
      headerLabel_.setForeground(new Color(50, 50, 50));

      gearButton_ = PopupButton.create(
            IconLoader.getIcon("/org/micromanager/icons/gear.png"));
      if (panelController_.getGearMenu() != null) {
         gearButton_.addPopupButtonListener(new PopupButton.Listener() {
            @Override
            public void popupButtonWillShowPopup(PopupButton button) {
               button.setPopupComponent(panelController_.getGearMenu());
            }
         });
      }
      gearButton_.setVisible(panelController.initiallyExpand()
            && panelController_.getGearMenu() != null);

      headerPanel_.add(headerLabel_, new CC().growX().pushX());
      headerPanel_.add(gearButton_, new CC().hideMode(2));
      headerPanel_.setCursor(new Cursor(Cursor.HAND_CURSOR));
      headerPanel_.setBackground(new Color(220, 220, 220));
      headerPanel_.setBorder(BorderFactory.createLineBorder(new Color(127, 127, 127)));
      headerPanel_.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            if (mouseClickenabled_) {
               setExpanded(!isExpanded());
            }
         }
      });

      panel_ = new JPanel(new MigLayout(
            new LC().fill().insets("0").gridGap("0", "0")));
      panel_.add(headerPanel_, new CC().growX().pushX().wrap());
      if (panelController.initiallyExpand()) {
         panel_.add(contentPanel_, new CC().grow().push());
      }
      panel_.validate();
   }

   public JPanel getSectionPanel() {
      return panel_;
   }

   boolean isVerticallyResizableByUser() {
      return panelController_.isVerticallyResizableByUser();
   }

   boolean isExpanded() {
      return panel_.isAncestorOf(contentPanel_);
   }

   private void setExpanded(boolean expanded) {
      if (expanded == isExpanded()) {
         return;
      }

      inspectorController_.inspectorSectionWillChangeHeight(this);

      headerLabel_.setIcon(UIManager.getIcon(expanded ?
            "Tree.expandedIcon" : "Tree.collapsedIcon"));
      gearButton_.setVisible(expanded && panelController_.getGearMenu() != null);

      // We cannot use MigLayout hideMode(2), since that would set the content
      // panel's height to 0. We need to preserve its height for later.
      // So we add/remove the content panel as necessary.
      if (expanded) {
         panel_.add(contentPanel_, new CC().grow().push().gap("0"));
      }
      else {
         panel_.remove(contentPanel_);
      }
      panelController_.setExpanded(expanded);

      inspectorController_.inspectorSectionDidChangeHeight(this);
   }

   @Override
   public void inspectorPanelWillChangeHeight(InspectorPanelController controller) {
      if (!isExpanded()) {
         return;
      }
      inspectorController_.inspectorSectionWillChangeHeight(this);
   }

   @Override
   public void inspectorPanelDidChangeHeight(InspectorPanelController controller) {
      if (!isExpanded()) {
         return;
      }
      inspectorController_.inspectorSectionDidChangeHeight(this);
   }

   @Override
   public void inspectorPanelDidChangeTitle(InspectorPanelController controller) {
      headerLabel_.setText(controller.getTitle());
   }

   public void setEnabled(boolean enabled) {
      // When the section is disabled it will collapse and will not be able to be expanded.
      if (enabled) {
         mouseClickenabled_ = true;
         headerLabel_.setEnabled(true);
      }
      else {
         this.setExpanded(false);
         mouseClickenabled_ = false;
         headerLabel_.setEnabled(false);
      }
   }

   public boolean isEnabled() {
      return this.headerPanel_.isEnabled();
   }
}