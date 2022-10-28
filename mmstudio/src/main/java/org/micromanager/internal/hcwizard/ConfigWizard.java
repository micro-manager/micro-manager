///////////////////////////////////////////////////////////////////////////////
//PROJECT:      Micro-Manager
//SUBSYSTEM:    mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:    Nenad Amodaj, nenad@amodaj.com, December 2, 2006
//
// COPYRIGHT: University of California, San Francisco, 2006
//            (c) 2016 Open Imaging, Inc.
//
// LICENSE:   This file is distributed under the BSD license.
//            License text is included with the source distribution.
//
//            This file is distributed in the hope that it will be useful,
//            but WITHOUT ANY WARRANTY; without even the implied warranty
//            of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//            IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//            CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//            INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.hcwizard;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.WindowPositioning;

/**
 * Configuration Wizard main panel.
 * Based on the dialog frame to be activated as part of the
 * MMStudio
 */
public final class ConfigWizard extends JDialog {

   private static final long serialVersionUID = 1L;
   private JPanel pagePanel_;
   private JButton backButton_;
   private JButton nextButton_;
   private PagePanel[] pages_;
   private int curPage_ = 0;
   private MicroscopeModel microModel_;
   private final CMMCore core_;
   private final Studio studio_;
   private JLabel titleLabel_;
   private final String defaultPath_;


   /**
    * Create the application.
    *
    * @param studio  Current Studio instance
    * @param defFile Configuration file to be used.
    */
   public ConfigWizard(Studio studio, String defFile) {
      super();
      studio_ = studio;
      core_ = studio_.core();
      defaultPath_ = defFile;
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      setModal(true);
      initialize();
   }

   /**
    * Initialize the contents of the frame.
    */
   private void initialize() {
      org.micromanager.internal.utils.HotKeys.active_ = false;

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            onCloseWindow();
         }
      });
      getContentPane().setLayout(new MigLayout());
      setTitle("Hardware Configuration Wizard");

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(50, 100);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);

      titleLabel_ = new JLabel();
      titleLabel_.setText("Title");
      add(titleLabel_, "span, wrap");

      pagePanel_ = new JPanel(new MigLayout("fill, insets 0"));
      pagePanel_.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
      add(pagePanel_, "width 700!, height 600!, span, wrap");

      backButton_ = new JButton();
      backButton_.addActionListener((ActionEvent arg0) -> setPage(curPage_ - 1));
      backButton_.setText("< Back");
      add(backButton_, "span, split, alignx right");

      nextButton_ = new JButton("Next >");
      nextButton_.addActionListener((ActionEvent arg0) -> {
         if (curPage_ == pages_.length - 1) {
            // call the last page's exit
            pages_[curPage_].exitPage(true);
            onCloseWindow();
         } else {
            setPage(curPage_ + 1);
         }
      });

      add(nextButton_, "wrap");

      // Create microscope model used by pages.
      microModel_ = new MicroscopeModel();
      microModel_.loadAvailableDeviceList(core_);
      microModel_.setFileName(defaultPath_);

      // Set up page panels.
      pages_ = new PagePanel[6];

      int pageNumber = 0;
      pages_[pageNumber++] = new IntroPage();
      pages_[pageNumber++] = new DevicesPage();
      pages_[pageNumber++] = new RolesPage();
      pages_[pageNumber++] = new DelayPage();
      pages_[pageNumber++] = new LabelsPage();
      pages_[pageNumber++] = new FinishPage();
      for (int i = 0; i < pages_.length; i++) {
         try {
            pages_[i].setModel(microModel_, studio_);
            pages_[i].loadSettings();
            pages_[i].setTitle("Step " + (i + 1) + " of "
                  + pages_.length + ": " + pages_[i].getTitle());
            pages_[i].setParentDialog(this);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }

      setPage(0);

      // Don't make the model think we've done anything yet.
      microModel_.setModified(false);
   }

   private void setPage(final int i) {
      // Only invoke from off the EDT, so that pages may do heavy work without
      // hanging the UI.
      if (SwingUtilities.isEventDispatchThread()) {
         new Thread(() -> setPage(i)).start();
         return;
      }
      // try to exit the current page
      if (i > 0) {
         if (!pages_[curPage_].exitPage(curPage_ < i)) {
            return;
         }
      }

      int newPage = Math.max(0, Math.min(pages_.length - 1, i));

      // try to enter the new page
      if (!pages_[newPage].enterPage(curPage_ > newPage)) {
         return;
      }

      // everything OK so we can proceed with swapping pages
      pagePanel_.removeAll();
      curPage_ = newPage;

      pagePanel_.add(pages_[curPage_], "grow");
      pack();
      getContentPane().repaint();
      pages_[curPage_].refresh();

      backButton_.setEnabled(curPage_ != 0);

      if (curPage_ == pages_.length - 1) {
         nextButton_.setText("Finish");
      } else {
         nextButton_.setText("Next >");
      }

      titleLabel_.setText(pages_[curPage_].getTitle());
   }


   private void onCloseWindow() {
      for (int i = 0; i < pages_.length; i++) {
         pages_[i].saveSettings();
      }

      if (microModel_.isModified()) {
         String[] buttons = new String[] {"Save As...", "Discard", "Cancel"};
         int result = JOptionPane.showOptionDialog(this,
               "Save changes to the configuration file?\n",
               "Hardware Config Wizard",
               JOptionPane.WARNING_MESSAGE, 0, null, buttons, buttons[2]);
         switch (result) {
            case 0: // Save As...
               saveConfiguration();
               break;
            case 1: // Discard
               break;
            case 2: // Cancel
               return;
            default:
               return;
         }
      }

      org.micromanager.internal.utils.HotKeys.active_ = true;
      dispose();
   }

   private void saveConfiguration() {
      File f = FileDialogs.save(this,
            "Create a config file", FileDialogs.MM_CONFIG_FILE);

      if (f == null) {
         return;
      }

      try {
         microModel_.saveToFile(f.getAbsolutePath());
      } catch (MMConfigFileException e) {
         JOptionPane.showMessageDialog(this, e.getMessage());
      }
   }

   public String getFileName() {
      return microModel_.getFileName();
   }
}
