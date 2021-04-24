package org.micromanager.display.inspector.internal.panels.ImageJ;

import ij.gui.GUI;
import ij.gui.Toolbar;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.AbstractInspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;
import org.scijava.Priority;
import org.scijava.plugin.Plugin;


@Plugin(type = InspectorPanelPlugin.class,
      priority = Priority.VERY_HIGH,
      name = "ImageJ Tools",
      description = "Draw ROI's")
public class ImageJInspectorPlugin implements InspectorPanelPlugin {
   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer.getDataProvider() != null;
   }

   @Override
   public InspectorPanelController createPanelController(Studio studio) {
      return ImageJPanelController.create();
   }
}

class ImageJPanelController extends AbstractInspectorPanelController {
   private final JPanel panel_ = new ImageJPanel();

   private ImageJPanelController() {
   }

   public static InspectorPanelController create() {
      return new ImageJPanelController();
   }

   public String getTitle() {
      return "ImageJ Tools";
   }

   public JPanel getPanel() {
      return panel_;
   }

   public void attachDataViewer(DataViewer viewer) {

   }

   public void detachDataViewer() {

   }

   public boolean isVerticallyResizableByUser() {
      return false;
   }

   public boolean initiallyExpand() {
      return true;
   };

   public void setExpanded(boolean status) {

   }

}

class ImageJPanel extends JPanel {
   public ImageJPanel() {
      super(new MigLayout("insets 0 0 0 0"));
      Toolbar tb = new ij.gui.Toolbar();
      //tb.init(); // This is in the docs but not resolved. Maybe we need a newer ImageJ version?
      //tb.installStartupMacrosTools(); // Doesn't seem to have any effect.
      super.add(tb);

   }
}