package org.micromanager.display.inspector.internal.panels.navigation;

import com.google.common.base.Preconditions;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelListener;

public class NavigationInspectorPanelController extends AbstractInspectorPanelController {

   private final UserProfile profile_;
   private DisplayWindow viewer_;
   private final JPanel panel_;


   public static NavigationInspectorPanelController create(Studio studio) {
      return new NavigationInspectorPanelController(studio);
   }

   private NavigationInspectorPanelController(Studio studio) {
      profile_ = studio.profile();
      panel_ = new JPanel();
      panel_.setLayout(new MigLayout(
            new LC().insets("0").gridGap("0", "0").fill()));
   }

   @Override
   public String getTitle() {
      return "Navigation";
   }

   @Override
   public JPanel getPanel() {
      return panel_;
   }

   @Override
   public JPopupMenu getGearMenu() {
      return null;
   }

   @Override
   public void attachDataViewer(DataViewer viewer) {
      Preconditions.checkState(viewer_ == null);
      Preconditions.checkArgument(viewer instanceof DisplayWindow);
      viewer_ = (DisplayWindow) viewer;
      viewer_.registerForEvents(this);
      // loadSettings(viewer_);
   }

   @Override
   public void detachDataViewer() {
      if (viewer_ != null) {
         viewer_.unregisterForEvents(this);
         viewer_ = null;
      }
   }

   @Override
   public boolean isVerticallyResizableByUser() {
      return false;
   }

   @Override
   public boolean initiallyExpand() {
      return false;
   }

   @Override
   public void setExpanded(boolean status) {

   }
}
