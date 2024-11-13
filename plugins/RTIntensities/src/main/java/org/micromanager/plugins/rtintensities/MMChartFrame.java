package org.micromanager.plugins.rtintensities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.Studio;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * Our own extension to ChartFrame to add an item to the pop-up menu.
 */
public class MMChartFrame extends ChartFrame {
   private final XYSeriesCollection xySeriesCollection_;
   private final DataProvider dataProvider_;
   private final MutablePropertyMapView settings_;
   private static final String PATH = "Save_Path";
   private final RTIntensitiesFrame frame_;


   public MMChartFrame(String title, JFreeChart chart, XYSeriesCollection xySeriesCollection,
                       Studio studio, DataProvider dataProvider, RTIntensitiesFrame frame) {
      super(title, chart);
      xySeriesCollection_ = xySeriesCollection;
      dataProvider_ = dataProvider;
      settings_ = studio.profile().getSettings(this.getClass());
      frame_ = frame;
   }


   public void modifyPopupMenu() {
      JPopupMenu pm = super.getChartPanel().getPopupMenu();
      JMenuItem saveItem = new JMenuItem("Save Data");
      saveItem.addActionListener(e -> {
         String csv = XYSeriesCollectionConverter.toCSV(xySeriesCollection_);
         String path = "";
         if (dataProvider_ instanceof Datastore) {
            Datastore datastore = (Datastore) dataProvider_;
            if (datastore.getSavePath() == null || datastore.getSavePath().isEmpty()) {
               File f = FileDialogs.openFile(this, "RTIntensities csv file",
                     new FileDialogs.FileType(
                           "RTIntensities csv file",
                           "csv",
                           settings_.getString(PATH, ""),
                           true,
                           "csv"));
               if (f != null) {
                  path = f.getAbsolutePath();
                  if (!path.endsWith(".csv")) {
                     path = path + ".csv";
                  }
                  settings_.putString(PATH, path);
               }
            } else {
               path = datastore.getSavePath() + File.separator
                     + datastore.getName() + "_chart.csv";
            }
            if (!path.isEmpty()) {
               File f = new File(path);
               if (f.exists()) {
                  int sel = JOptionPane.showConfirmDialog(this,
                        "Overwrite " + f.getName() + "?",
                        "Saving csv file",
                        JOptionPane.YES_NO_OPTION);
                  if (sel == JOptionPane.NO_OPTION) {
                     return;
                  }
               }
               try (PrintWriter out = new PrintWriter(path)) {
                  out.println(csv);
                  frame_.setTile("Saved data to: " + f.getName());
               } catch (FileNotFoundException fnfe) {
                  ReportingUtils.showError(fnfe, "Failed to save " + path);
               }
            }
         }

      });
      pm.add(saveItem);
      super.getChartPanel().setPopupMenu(pm);
   }

}
