package org.micromanager.display.inspector.internal.panels.navigation;

import com.google.common.base.Preconditions;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import net.miginfocom.layout.CC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.micromanager.MultiStagePosition;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.inspector.AbstractInspectorPanelController;

/**
 * Draws the Panel and executes its functionality.
 */
public class NavigationInspectorPanelController extends AbstractInspectorPanelController {

   private final UserProfile profile_;
   private final Studio studio_;
   private DisplayWindow viewer_;
   private final JPanel panel_;


   public static NavigationInspectorPanelController create(Studio studio) {
      return new NavigationInspectorPanelController(studio);
   }

   private NavigationInspectorPanelController(Studio studio) {
      studio_ = studio;
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
      final DataProvider provider = viewer_.getDataProvider();
      for (final String axis : provider.getSummaryMetadata().getOrderedAxes()) {
         if (provider.getSummaryMetadata().getIntendedDimensions().getIndex(axis) <= 1) {
            continue;
         }
         panel_.add(new JLabel(axis + ":"), new CC().split().gapAfter("10"));
         JTextField axisField = new JTextField();
         axisField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
               Coords oldCoords = viewer_.getDisplayPosition();
               try {
                  int val = Integer.parseInt(axisField.getText());
                  val -= 1;
                  Coords newCoords;
                  if (oldCoords != null) {
                     newCoords = oldCoords.copyBuilder().index(axis, val).build();
                  } else {
                     newCoords = studio_.data().coordsBuilder().index(axis, val).build();
                  }
                  viewer.setDisplayPosition(newCoords);
               } catch (NumberFormatException nfe) {
                  // studio_.logs().logMessage("Non Integer entered in Navigation Inspector");
                  if (axis.equals("position")) {
                     for (int p = 0;
                           p < provider.getSummaryMetadata().getStagePositionList().size(); p++) {
                        if (provider.getSummaryMetadata().getStagePositionList().get(p)
                              .getLabel().contains(axisField.getText())) {
                           Coords newCoords;
                           if (oldCoords != null) {
                              newCoords = oldCoords.copyBuilder().index(axis, p).build();
                           } else {
                              newCoords = studio_.data().coordsBuilder().p(p).build();
                           }
                           viewer.setDisplayPosition(newCoords);
                           return;
                        }
                     }
                  } else if (axis.equals("channel")) {
                     List<String> channels = provider.getSummaryMetadata().getChannelNameList();
                     for (int c = 0; c < channels.size(); c++) {
                        String channel = channels.get(c);
                        if (channel.contains(axisField.getText())) {
                           Coords newCoords = null;
                           if (oldCoords != null) {
                              newCoords = oldCoords.copyBuilder().c(c).build();
                           } else {
                              newCoords = studio_.data().coordsBuilder().c(c).build();
                           }
                           viewer.setDisplayPosition(newCoords);
                           return;
                        }
                     }
                  }
               }
            }
         });
         panel_.add(axisField, new CC().split().gapAfter("rel").growX().gapAfter("20"));
      }
   }

   @Override
   public void detachDataViewer() {
      if (viewer_ != null) {
         viewer_.unregisterForEvents(this);
         viewer_ = null;
      }
      for (Component comp : panel_.getComponents()) {
         panel_.remove(comp);
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
