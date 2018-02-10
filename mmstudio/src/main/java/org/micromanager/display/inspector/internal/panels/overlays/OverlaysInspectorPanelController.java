/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.inspector.internal.panels.overlays;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.display.internal.event.DisplayWindowDidAddOverlayEvent;
import org.micromanager.display.internal.event.DisplayWindowDidRemoveOverlayEvent;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayPlugin;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.PopupButton;
import org.scijava.plugin.Plugin;

/**
 *
 * @author mark
 */
public final class OverlaysInspectorPanelController
      extends AbstractInspectorPanelController
{
   private final JPanel panel_ = new JPanel();

   private final PopupButton addOverlayButton_;
   private final JPopupMenu addOverlayMenu_;

   private final JPanel configsPanel_;

   // These two lists are kept colinear
   private final List<Overlay> overlays_ =
         new ArrayList<Overlay>();
   private final List<OverlayConfigPanelController> configPanelControllers_ =
         new ArrayList<OverlayConfigPanelController>();

   private DisplayWindow viewer_;

   public static OverlaysInspectorPanelController create() {
      return new OverlaysInspectorPanelController();
   }

   private OverlaysInspectorPanelController() {
      List<OverlayPlugin> plugins = new ArrayList<OverlayPlugin>(
            MMStudio.getInstance().plugins().getOverlayPlugins().values());
      Collections.sort(plugins, new Comparator<OverlayPlugin>() {
         @Override
         public int compare(OverlayPlugin o1, OverlayPlugin o2) {
            Plugin p1 = o1.getClass().getAnnotation(Plugin.class);
            Plugin p2 = o2.getClass().getAnnotation(Plugin.class);
            return -Double.compare(p1.priority(), p2.priority());
         }
      });

      addOverlayMenu_ = new JPopupMenu();
      for (final OverlayPlugin plugin : plugins) {
         String name = plugin.getClass().getAnnotation(Plugin.class).name();
         JMenuItem item = new JMenuItem(name);
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               handleAddOverlay(plugin);
            }
         });
         addOverlayMenu_.add(item);
      }

      // Temporarily set text to 'Remove' to compute size
      addOverlayButton_ = PopupButton.create("Remove",
            IconLoader.getIcon("/org/micromanager/icons/plus_green.png"),
            addOverlayMenu_);
      addOverlayButton_.setHorizontalAlignment(SwingConstants.LEFT);
      addOverlayButton_.setPreferredSize(new Dimension(addOverlayButton_.getPreferredSize().width, 22));
      addOverlayButton_.setText("Add");

      configsPanel_ = new JPanel(
            new MigLayout(new LC().insets("0").gridGap("0", "0").fill()));

      panel_.setLayout(new MigLayout(
            new LC().insets("0").gridGap("0", "0").fill()));
      panel_.add(configsPanel_, new CC().growX().pushX().wrap());
      panel_.add(addOverlayButton_,
            new CC().gapBefore("push").gapAfter("rel").
                  gapY("rel", "rel").
                  height("pref:pref:pref"));
   }

   private void handleAddOverlay(OverlayPlugin plugin) {
      Overlay overlay = plugin.createOverlay();
      viewer_.addOverlay(overlay);
   }

   void handleRemoveOverlay(Overlay overlay) {
      viewer_.removeOverlay(overlay);
   }

   void handleEnableOverlay(Overlay overlay, boolean show) {
      overlay.setVisible(show);
   }

   private void addConfigPanel(Overlay overlay) {
      overlays_.add(overlay);

      OverlayConfigPanelController cc =
            OverlayConfigPanelController.create(this, overlay);
      configPanelControllers_.add(cc);

      fireInspectorPanelWillChangeHeight();
      configsPanel_.add(cc.getConfigPanel(),
            new CC().height("pref:pref:pref").growX().pushX().wrap());
      configsPanel_.add(new JSeparator(JSeparator.HORIZONTAL),
            new CC().height("pref:pref:pref").growX().wrap());
      fireInspectorPanelDidChangeHeight();
   }

   private void removeConfigPanel(Overlay overlay) {
      int i = overlays_.indexOf(overlay);
      if (i < 0) {
         return;
      }
      overlays_.remove(overlay);
      OverlayConfigPanelController cc = configPanelControllers_.get(i);
      configPanelControllers_.remove(cc);

      int index = Arrays.asList(configsPanel_.getComponents()).
            indexOf(cc.getConfigPanel());
      JSeparator separator = (JSeparator) configsPanel_.getComponent(index + 1);

      fireInspectorPanelWillChangeHeight();
      configsPanel_.remove(separator);
      configsPanel_.remove(cc.getConfigPanel());
      fireInspectorPanelDidChangeHeight();
   }

   @Override
   public String getTitle() {
      return "Overlays";
   }

   @Override
   public void attachDataViewer(DataViewer viewer) {
      Preconditions.checkState(viewer_ == null);
      Preconditions.checkArgument(viewer instanceof DisplayWindow);
      viewer_ = (DisplayWindow) viewer;
      viewer_.registerForEvents(this);

      for (Overlay overlay : viewer_.getOverlays()) {
         addConfigPanel(overlay);
      }
   }

   @Override
   public void detachDataViewer() {
      viewer_.unregisterForEvents(this);
      viewer_ = null;
      configsPanel_.removeAll();
      overlays_.clear();
      configPanelControllers_.clear();
   }

   @Override
   public boolean isVerticallyResizableByUser() {
      return false;
   }

   @Override
   public JPanel getPanel() {
      return panel_;
   }

   @Override
   public boolean initiallyExpand() {
      return false;
      // TODO: remember last setting and restore
   }

   @Subscribe
   public void onEvent(DisplayWindowDidAddOverlayEvent e) {
      addConfigPanel(e.getOverlay());
   }

   @Subscribe
   public void onEvent(DisplayWindowDidRemoveOverlayEvent e) {
      removeConfigPanel(e.getOverlay());
   }
}