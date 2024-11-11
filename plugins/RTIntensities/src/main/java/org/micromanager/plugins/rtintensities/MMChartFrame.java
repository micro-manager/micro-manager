package org.micromanager.plugins.rtintensities;

import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Our own extension to ChartFrame to add an item to the pop-up menu.
 */
public class MMChartFrame extends ChartFrame {
   XYSeriesCollection xySeriesCollection_;

   public MMChartFrame(String title, JFreeChart chart, XYSeriesCollection xySeriesCollection) {
      super(title, chart);
      xySeriesCollection_ = xySeriesCollection;
   }


   public void modifyPopupMenu() {
      JPopupMenu pm = super.getChartPanel().getPopupMenu();
      JMenuItem saveItem = new JMenuItem("Save");
      saveItem.addActionListener(e -> {
         List<XYSeries> lists = xySeriesCollection_.getSeries();
         for (XYSeries xySeries : lists) {

         }
      });
      pm.add(saveItem);
      super.getChartPanel().setPopupMenu(pm);
   }

}
