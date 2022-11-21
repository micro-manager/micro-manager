/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.panels;

import com.asiimaging.tirf.model.TIRFControlModel;
import com.asiimaging.tirf.ui.TIRFControlFrame;
import com.asiimaging.tirf.ui.components.Panel;
import com.asiimaging.tirf.ui.components.TabbedPane;
import com.asiimaging.tirf.ui.panels.tabs.DataTab;
import com.asiimaging.tirf.ui.panels.tabs.ScannerTab;
import java.util.Objects;

/**
 * The tabbed pane.
 */
public class TabPanel extends Panel {

   private ScannerTab scannerTab;
   private DataTab dataTab;

   private final TabbedPane tabbedPane;
   private final TIRFControlModel model;
   private final TIRFControlFrame frame;

   public TabPanel(final TIRFControlModel model, final TIRFControlFrame frame) {
      this.model = Objects.requireNonNull(model);
      this.frame = Objects.requireNonNull(frame);
      tabbedPane = new TabbedPane(600, 460);
      createUserInterface();
   }

   private void createUserInterface() {
      // create tabs
      scannerTab = new ScannerTab(model);
      dataTab = new DataTab(model);

      // add tabs to the panel
      tabbedPane.addTab(createTabTitle("Scanner"), scannerTab);
      tabbedPane.addTab(createTabTitle("Datastore"), dataTab);

      // add ui elements to the panel
      add(tabbedPane, "");
   }

   // use HTML to make the tab labels look nice
   private String createTabTitle(final String title) {
      return
            "<html><body leftmargin=15 topmargin=8 marginwidth=15 marginheight=5><b><font size=4>"
                  + title + "</font></b></body></html>";
   }

   public DataTab getDataTab() {
      return dataTab;
   }

   public ScannerTab getScannerTab() {
      return scannerTab;
   }
}
