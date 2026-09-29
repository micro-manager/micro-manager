package org.micromanager.display.inspector.internal.panels.overlays;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.display.internal.event.DisplayWindowDidAddOverlayEvent;
import org.micromanager.display.internal.event.DisplayWindowDidRemoveOverlayEvent;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayPlugin;
import org.micromanager.display.overlay.OverlaySupport;
import org.micromanager.internal.propertymap.DefaultPropertyMap;
import org.micromanager.internal.utils.PopupButton;
import org.micromanager.propertymap.MutablePropertyMapView;
import org.scijava.plugin.Plugin;

/**
 * @author mark
 */
public final class OverlaysInspectorPanelController
      extends AbstractInspectorPanelController {
   private final JPanel panel_ = new JPanel();
   private final JPanel configsPanel_;
   private final PopupButton addOverlayButton_;
   private final JPopupMenu addOverlayMenu_;

   private final UserProfile profile_;
   private static final String CONFIGPMAPKEY = "OverlayConfig";
   private static final String VISIBLEPMAPKEY = "OverlayVisible";
   private static final String TITLEPMAPKEY = "OverlayTitle";
   private static final String OVERLAYDEFAULT = "OverlayDefault";
   // Backstops against runaway growth of the profile.  A display realistically has a handful of
   // overlays and a user a few dozen datasets; these caps only ever bite when something is wrong.
   private static final int MAXOVERLAYSPERKEY = 20;
   private static final int MAXPERDISPLAYKEYS = 50;


   private static boolean expanded_ = false;

   // These two lists are kept colinear
   private final List<Overlay> overlays_ = new ArrayList<>();
   private final List<OverlayConfigPanelController> configPanelControllers_ =
         new ArrayList<>();

   private DataViewer viewer_;
   private final List<OverlayPlugin> plugins_;

   public static OverlaysInspectorPanelController create(Studio studio) {
      return new OverlaysInspectorPanelController(studio);
   }

   private OverlaysInspectorPanelController(Studio studio) {
      profile_ = studio.profile();
      plugins_ = new ArrayList<>(
            studio.plugins().getOverlayPlugins().values());
      Collections.sort(plugins_, (OverlayPlugin o1, OverlayPlugin o2) -> {
         Plugin p1 = o1.getClass().getAnnotation(Plugin.class);
         Plugin p2 = o2.getClass().getAnnotation(Plugin.class);
         return -Double.compare(p1.priority(), p2.priority());
      });

      addOverlayMenu_ = new JPopupMenu();
      for (final OverlayPlugin plugin : plugins_) {
         String name = plugin.getClass().getAnnotation(Plugin.class).name();
         JMenuItem item = new JMenuItem(name);
         item.addActionListener((ActionEvent e) -> {
            handleAddOverlay(plugin);
         });
         addOverlayMenu_.add(item);
      }

      // Temporarily set text to 'Remove' to compute size
      addOverlayButton_ = PopupButton.create("Remove",
            IconLoader.getIcon("/org/micromanager/icons/plus_green.png"),
            addOverlayMenu_);
      addOverlayButton_.setHorizontalAlignment(SwingConstants.LEFT);
      addOverlayButton_
            .setPreferredSize(new Dimension(addOverlayButton_.getPreferredSize().width, 22));
      addOverlayButton_.setText("Add");

      configsPanel_ = new JPanel(
            new MigLayout(new LC().insets("0").gridGap("0", "0").fill()));

      panel_.setLayout(new MigLayout(
            new LC().insets("0").gridGap("0", "0").fill()));
      panel_.add(configsPanel_, new CC().growX().pushX().wrap());
      panel_.add(addOverlayButton_,
            new CC().gapBefore("push").gapAfter("rel")
                  .gapY("rel", "rel")
                  .height("pref:pref:pref"));
   }

   /**
    * Return the overlay support interface for the current viewer.
    * Works for both DisplayWindow and OverlaySupport implementations.
    */
   private OverlaySupport getOverlaySupport() {
      if (viewer_ instanceof OverlaySupport) {
         return (OverlaySupport) viewer_;
      }
      if (viewer_ instanceof DisplayWindow) {
         final DisplayWindow dw = (DisplayWindow) viewer_;
         return new OverlaySupport() {
            @Override
            public void addOverlay(Overlay overlay) {
               dw.addOverlay(overlay);
            }

            @Override
            public void removeOverlay(Overlay overlay) {
               dw.removeOverlay(overlay);
            }

            @Override
            public List<Overlay> getOverlays() {
               return dw.getOverlays();
            }
         };
      }
      return null;
   }

   private void loadSettings(DataViewer viewer) {
      // Loading appends to overlays_ (by way of the events fired by addOverlay), so refuse to
      // load on top of a non-empty list.  Doing so would duplicate every overlay on each
      // attach/detach cycle, which is how profiles grew to hundreds of megabytes.
      if (!overlays_.isEmpty()) {
         return;
      }
      //Load the overlays from the profile.
      String providerName = viewer.getDataProvider().getName();
      // first look for settings for this display, if not found, revert to DEFAULT settings
      // which is the last saved settings
      List<PropertyMap> settings = profile_.getSettings(this.getClass())
            .getPropertyMapList(providerName,
                    profile_.getSettings(this.getClass()).getPropertyMapList(
                            OVERLAYDEFAULT, (PropertyMap[]) null));
      if (settings == null) {
         return;
      }
      OverlaySupport support = getOverlaySupport();
      if (support == null) {
         return;
      }
      for (PropertyMap pMap : settings) {
         for (OverlayPlugin p : plugins_) { // We must loop through overlay plugins to
            // determine if they are a match for this setting.
            Overlay o = p.createOverlay();
            if (pMap.getString(TITLEPMAPKEY, "loadFailed").equals(
                  o.getTitle())) {  // Checking against Overlay 'Title; is the best
               // way we have to link settings with an overlay.
               PropertyMap config = pMap.getPropertyMap(CONFIGPMAPKEY, null);
               o.setConfiguration(config);
               o.setVisible(pMap.getBoolean(VISIBLEPMAPKEY, false));
               support.addOverlay(
                     o); // The viewer will fire an event that will trigger adding the
               // UI components to the inspector
               break;
            }
         }
      }
   }

   private void saveSettings(DataViewer viewer) {
      List<PropertyMap> configList = new ArrayList<>();
      for (Overlay o : this.overlays_) {
         PropertyMap map = new DefaultPropertyMap.Builder()
               .putPropertyMap(CONFIGPMAPKEY, o.getConfiguration())
               .putBoolean(VISIBLEPMAPKEY, o.isVisible())
               .putString(TITLEPMAPKEY, o.getTitle())
               .build();
         // Never store the same overlay twice.  PropertyMap.equals() compares by value.
         if (configList.contains(map)) {
            continue;
         }
         configList.add(map);
         if (configList.size() >= MAXOVERLAYSPERKEY) {
            break;
         }
      }
      MutablePropertyMapView settings = profile_.getSettings(this.getClass());
      String providerName = viewer.getDataProvider().getName();
      // Every write copies the whole settings map and schedules a save of the entire profile,
      // so only write when the stored value actually changes.
      List<PropertyMap> defaultList =
            settings.getPropertyMapList(OVERLAYDEFAULT, (PropertyMap[]) null);
      // The per-display entry is only worth storing when it differs from the default that
      // loadSettings() already falls back to.  Otherwise every dataset ever opened would
      // leave a redundant key behind forever.
      if (configList.isEmpty() || configList.equals(defaultList)) {
         if (settings.containsKey(providerName)) {
            settings.remove(providerName);
         }
      } else if (!configList.equals(
            settings.getPropertyMapList(providerName, (PropertyMap[]) null))) {
         settings.putPropertyMapList(providerName, configList);
      }
      if (!configList.equals(defaultList)) {
         settings.putPropertyMapList(OVERLAYDEFAULT, configList);
      }
      prunePerDisplayKeys(settings, providerName);
   }

   /**
    * Caps the number of per-display keys stored in the profile.  Keys accumulate one per dataset
    * ever opened; without a bound the profile grows without limit.
    *
    * <p>keySet() here is a chained view over this profile and the global fallback profile, and
    * its iteration order is neither insertion order nor otherwise meaningful, so there is no way
    * to tell which keys are oldest.  Rather than evict at random, this drops every per-display
    * key except the one being saved.  That is blunt, but it only triggers after 50 displays have
    * each stored overlays differing from the default, and anything dropped simply falls back to
    * OverlayDefault.  Removal is batched into one write because each write copies the whole map
    * and schedules a save of the entire profile.
    *
    * @param settings    the profile settings for this class
    * @param keepKey     the per-display key that must survive, or null to keep none
    */
   private void prunePerDisplayKeys(MutablePropertyMapView settings, String keepKey) {
      List<String> perDisplayKeys = new ArrayList<>(settings.keySet());
      perDisplayKeys.remove(OVERLAYDEFAULT);
      if (perDisplayKeys.size() <= MAXPERDISPLAYKEYS) {
         return;
      }
      perDisplayKeys.remove(keepKey);
      settings.removeAll(perDisplayKeys);
   }

   private void handleAddOverlay(OverlayPlugin plugin) {
      Overlay overlay = plugin.createOverlay();
      org.micromanager.internal.utils.ReportingUtils.logMessage(
            "OverlaysInspectorPanelController: handleAddOverlay " + overlay.getTitle()
            + " viewer_=" + (viewer_ == null ? "null" : viewer_.getClass().getSimpleName()));
      OverlaySupport support = getOverlaySupport();
      org.micromanager.internal.utils.ReportingUtils.logMessage(
            "OverlaysInspectorPanelController: getOverlaySupport returned "
            + (support == null ? "null" : support.getClass().getSimpleName()));
      if (support != null) {
         support.addOverlay(overlay);
      }
   }

   void handleRemoveOverlay(Overlay overlay) {
      OverlaySupport support = getOverlaySupport();
      if (support != null) {
         support.removeOverlay(overlay);
      }
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

   /**
    * Drops any overlay UI left over from a previous viewer.  Unlike removeConfigPanel() this does
    * not go through the viewer, since the viewer that owned these overlays may already be gone.
    */
   private void clearConfigPanels() {
      if (overlays_.isEmpty() && configPanelControllers_.isEmpty()) {
         return;
      }
      fireInspectorPanelWillChangeHeight();
      overlays_.clear();
      configPanelControllers_.clear();
      configsPanel_.removeAll();
      configsPanel_.revalidate();
      configsPanel_.repaint();
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

      int index = Arrays.asList(configsPanel_.getComponents())
            .indexOf(cc.getConfigPanel());
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
      if (viewer_ != null) {
         detachDataViewer();
      }
      Preconditions.checkArgument(viewer instanceof DisplayWindow
            || viewer instanceof OverlaySupport);
      viewer_ = viewer;
      org.micromanager.internal.utils.ReportingUtils.logMessage(
            "OverlaysInspectorPanelController: attachDataViewer "
            + viewer.getClass().getSimpleName()
            + " isOverlaySupport=" + (viewer instanceof OverlaySupport));
      // detachDataViewer() normally empties these, but it relies on the previous viewer firing
      // removal events.  If that did not happen we must not carry overlays over into the new
      // viewer, or loadSettings() would refuse to load and saveSettings() would persist
      // overlays belonging to a display that is no longer shown.
      clearConfigPanels();
      viewer_.registerForEvents(this);
      loadSettings(viewer_);
   }

   @Override
   public void detachDataViewer() {
      if (viewer_ != null) {
         saveSettings(viewer_);
         List<Overlay> overlays = new ArrayList<>(
               overlays_);  // We iterate over a copy of the overlays_ list to avoid causing
         // a ConcurrentModificationException by removing items from the list while iterating.
         for (Overlay o : overlays) { // We can't manually remove the overlays from `overlays_`
            // we need to allow the `viewer_` to fire off the relevant events so that everything
            // is properly handled.
            this.handleRemoveOverlay(o);  // The viewer will fire an event that will also
            // remove the UI components from the inspector.
         }
         viewer_.unregisterForEvents(this);
         viewer_ = null;
      }
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
   public void setExpanded(boolean state) {
      expanded_ = state;
   }

   @Override
   public boolean initiallyExpand() {
      return expanded_;
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