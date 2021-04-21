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
import org.micromanager.internal.propertymap.DefaultPropertyMap;
import org.micromanager.internal.utils.PopupButton;
import org.scijava.plugin.Plugin;

/** @author mark */
public final class OverlaysInspectorPanelController extends AbstractInspectorPanelController {
  private final JPanel panel_ = new JPanel();
  private final JPanel configsPanel_;
  private final PopupButton addOverlayButton_;
  private final JPopupMenu addOverlayMenu_;

  private final UserProfile profile_;
  private static final String CONFIGPMAPKEY = "OverlayConfig";
  private static final String VISIBLEPMAPKEY = "OverlayVisible";
  private static final String TITLEPMAPKEY = "OverlayTitle";

  private static boolean expanded_ = false;

  // These two lists are kept colinear
  private final List<Overlay> overlays_ = new ArrayList<>();
  private final List<OverlayConfigPanelController> configPanelControllers_ = new ArrayList<>();

  private DisplayWindow viewer_;
  private final List<OverlayPlugin> plugins_;

  public static OverlaysInspectorPanelController create(Studio studio) {
    return new OverlaysInspectorPanelController(studio);
  }

  private OverlaysInspectorPanelController(Studio studio) {
    profile_ = studio.profile();
    plugins_ = new ArrayList<>(studio.plugins().getOverlayPlugins().values());
    Collections.sort(
        plugins_,
        (OverlayPlugin o1, OverlayPlugin o2) -> {
          Plugin p1 = o1.getClass().getAnnotation(Plugin.class);
          Plugin p2 = o2.getClass().getAnnotation(Plugin.class);
          return -Double.compare(p1.priority(), p2.priority());
        });

    addOverlayMenu_ = new JPopupMenu();
    for (final OverlayPlugin plugin : plugins_) {
      String name = plugin.getClass().getAnnotation(Plugin.class).name();
      JMenuItem item = new JMenuItem(name);
      item.addActionListener(
          (ActionEvent e) -> {
            handleAddOverlay(plugin);
          });
      addOverlayMenu_.add(item);
    }

    // Temporarily set text to 'Remove' to compute size
    addOverlayButton_ =
        PopupButton.create(
            "Remove",
            IconLoader.getIcon("/org/micromanager/icons/plus_green.png"),
            addOverlayMenu_);
    addOverlayButton_.setHorizontalAlignment(SwingConstants.LEFT);
    addOverlayButton_.setPreferredSize(
        new Dimension(addOverlayButton_.getPreferredSize().width, 22));
    addOverlayButton_.setText("Add");

    configsPanel_ = new JPanel(new MigLayout(new LC().insets("0").gridGap("0", "0").fill()));

    panel_.setLayout(new MigLayout(new LC().insets("0").gridGap("0", "0").fill()));
    panel_.add(configsPanel_, new CC().growX().pushX().wrap());
    panel_.add(
        addOverlayButton_,
        new CC().gapBefore("push").gapAfter("rel").gapY("rel", "rel").height("pref:pref:pref"));
  }

  private void loadSettings(DisplayWindow viewer) {
    // Load the overlays from the profile.
    String providerName = viewer.getDataProvider().getName();
    List<PropertyMap> settings =
        profile_
            .getSettings(this.getClass())
            .getPropertyMapList(providerName, (PropertyMap[]) null);
    if (settings == null) {
      return;
    }
    for (PropertyMap pMap : settings) {
      for (OverlayPlugin p :
          plugins_) { // We must loop through overlay plugins to determine if they are a match for
        // this setting.
        Overlay o = p.createOverlay();
        if (pMap.getString(TITLEPMAPKEY, "loadFailed")
            .equals(o.getTitle())) { // Checking against Overlay 'Title; is the best way we have to
          // link settings with an overlay.
          PropertyMap config = pMap.getPropertyMap(CONFIGPMAPKEY, null);
          o.setConfiguration(config);
          o.setVisible(pMap.getBoolean(VISIBLEPMAPKEY, false));
          viewer_.addOverlay(
              o); // The viewer will fire an event that will trigger adding the UI components to the
          // inspector
          break;
        }
      }
    }
  }

  private void saveSettings(DisplayWindow viewer) {
    List<PropertyMap> configList = new ArrayList<>();
    for (Overlay o : this.overlays_) {
      PropertyMap map =
          new DefaultPropertyMap.Builder()
              .putPropertyMap(CONFIGPMAPKEY, o.getConfiguration())
              .putBoolean(VISIBLEPMAPKEY, o.isVisible())
              .putString(TITLEPMAPKEY, o.getTitle())
              .build();
      configList.add(map);
    }
    String providerName = viewer.getDataProvider().getName();
    profile_.getSettings(this.getClass()).putPropertyMapList(providerName, configList);
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

    OverlayConfigPanelController cc = OverlayConfigPanelController.create(this, overlay);
    configPanelControllers_.add(cc);

    fireInspectorPanelWillChangeHeight();
    configsPanel_.add(
        cc.getConfigPanel(), new CC().height("pref:pref:pref").growX().pushX().wrap());
    configsPanel_.add(
        new JSeparator(JSeparator.HORIZONTAL), new CC().height("pref:pref:pref").growX().wrap());
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

    int index = Arrays.asList(configsPanel_.getComponents()).indexOf(cc.getConfigPanel());
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
    loadSettings(viewer_);
  }

  @Override
  public void detachDataViewer() {
    if (viewer_ != null) {
      saveSettings(viewer_);
      List<Overlay> overlays =
          new ArrayList<>(
              overlays_); // We iterate over a copy of the overlays_ list to avoid causing a
      // ConcurrentModificationException by removing items from the list while
      // iterating.
      for (Overlay o :
          overlays) { // We can't manually remove the overlays from `overlays_` we need to allow the
        // `viewer_` to fire off the relevant events so that everything is properly
        // handled.
        this.handleRemoveOverlay(
            o); // The viewer will fire an event that will also remove the UI components from the
        // inspector.
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
