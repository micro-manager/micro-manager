package org.micromanager.display.internal.displaywindow;

import java.awt.event.ActionEvent;
import java.io.IOException;
import javax.swing.AbstractAction;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.display.ImageExporter;
import org.micromanager.display.internal.gearmenu.DefaultImageExporter;

/**
 * Utility that copies the current image as displayed to the System Clipboard.
 * Initialized by the DisplayUIController.
 */
public class CopyAsDisplayed extends AbstractAction {
   private final Studio studio_;
   private final DisplayUIController displayUIController_;

   /**
    * Constructor, gets access to Studio and the DisplayUIController
    * (from which it uses the DisplayController).
    *
    * @param studio Used for logging
    * @param displayUIController DisplayUIController owns the RootPane, and
    *                            has access to the DisplayController.
    */
   public CopyAsDisplayed(Studio studio, DisplayUIController displayUIController) {
      studio_ = studio;
      displayUIController_ = displayUIController;
   }

   @Override
   public void actionPerformed(ActionEvent e) {
      ImageExporter exporter = new DefaultImageExporter(studio_.getLogManager());
      exporter.setOutputFormat(ImageExporter.OutputFormat.OUTPUT_CLIPBOARD);
      exporter.setDisplay(displayUIController_.getDisplayController());
      Coords displayedImage = displayUIController_.getDisplayController().getDisplayPosition();
      for (String axis : displayUIController_.getDisplayController().getDataProvider().getAxes()) {
         exporter.loop(axis, displayedImage.getIndex(axis), displayedImage.getIndex(axis));
      }
      try {
         exporter.export();
      } catch (IOException | IllegalArgumentException exc) {
         studio_.logs().logError(exc, "MMKeyDispatcher: error should never happen");
      }
   }


}
