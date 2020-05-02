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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.HttpUtils;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Configuration Wizard main panel.
 * Based on the dialog frame to be activated as part of the
 * MMStudio
 */
public final class ConfigWizard extends MMDialog {

   private static final long serialVersionUID = 1L;
   private JPanel pagePanel_;
   private JButton backButton_;
   private JButton nextButton_;
   private PagePanel pages_[];
   private int curPage_ = 0;
   private MicroscopeModel microModel_;
   private final CMMCore core_;
   private final Studio studio_;
   private JLabel titleLabel_;
   private final String defaultPath_;

   private static final String CFG_OKAY_TO_SEND = "CFG_Okay_To_Send";


   /**
    * Create the application
    * @param studio Current Studio instance
    * @param defFile
    */
   public ConfigWizard(Studio studio, String defFile) {
      super("hardware configuration wizard");
      studio_ = studio;
      core_ = studio_.core();
      defaultPath_ = defFile;
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      setModal(true);
      initialize();
   }

   /**
    * Initialize the contents of the frame
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
      loadPosition(50, 100);

      titleLabel_ = new JLabel();
      titleLabel_.setText("Title");
      add(titleLabel_, "span, wrap");

      pagePanel_ = new JPanel(new MigLayout("fill, insets 0"));
      pagePanel_.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
      add(pagePanel_, "width 700!, height 600!, span, wrap");

      backButton_ = new JButton();
      backButton_.addActionListener((ActionEvent arg0) -> {
         setPage(curPage_ - 1);
      });
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
      microModel_.setSendConfiguration(studio_.profile().
              getSettings(ConfigWizard.class).getBoolean(CFG_OKAY_TO_SEND, true));
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
            pages_[i].setTitle("Step " + (i + 1) + " of " + pages_.length + ": " + pages_[i].getTitle());
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
         new Thread(() -> {
            setPage(i);
         }).start();
         return;
      }
      // try to exit the current page
      if (i > 0) {
         if (!pages_[curPage_].exitPage(curPage_ < i ? true : false)) {
            return;
         }
      }

      int newPage = Math.max(0, Math.min(pages_.length - 1, i));

      // try to enter the new page
      if (!pages_[newPage].enterPage(curPage_ > newPage ? true : false)) {
         return;
      }

      // everything OK so we can proceed with swapping pages
      pagePanel_.removeAll();
      curPage_ = newPage;

      pagePanel_.add(pages_[curPage_], "grow");
      pack();
      getContentPane().repaint();
      pages_[curPage_].refresh();

      if (curPage_ == 0) {
         backButton_.setEnabled(false);
      } else {
         backButton_.setEnabled(true);
      }

      if (curPage_ == pages_.length - 1) {
         nextButton_.setText("Finish");
      } else {
         nextButton_.setText("Next >");
      }

      titleLabel_.setText(pages_[curPage_].getTitle());
   }

   private String UploadCurrentConfigFile() {
      String returnValue = "";
      try {
         HttpUtils httpu = new HttpUtils();
         if (this.getFileName() == null) {
            return "No config file";
         }
         File conff = new File(this.getFileName());
         if (conff.exists()) {

            // contruct a filename for the configuration file which is extremely
            // likely to be unique as follows:
            // yyyyMMddHHmmss + timezone + ip address
            String prependedLine = "#";
            String qualifiedConfigFileName = "";
            try {
               SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
               qualifiedConfigFileName += df.format(new Date());
               String shortTZName = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT);
               qualifiedConfigFileName += shortTZName;
               qualifiedConfigFileName += "@";
               try {
                  // a handy, unique ID for the user's computer
                  String physicalAddress = "00-00-00-00-00-00";

                  StrVector ss = core_.getMACAddresses();
                  if (0 < ss.size()){
                     String pa2 = ss.get(0);
                     if(null != pa2){
                        if( 0 <  pa2.length()){
                           physicalAddress = pa2;
                        }
                     }
                  }
                  qualifiedConfigFileName += physicalAddress;
                  prependedLine += "Host: " + InetAddress.getLocalHost().getHostName() + " ";
               } catch (UnknownHostException e) {
               }
               prependedLine += "User: " + core_.getUserId() + " configuration file: " + conff.getName() + "\n";
            } catch (Throwable t) {
            }

            // can raw IP address have :'s in them? (ipv6??)
            // try ensure valid and convenient UNIX file name
            qualifiedConfigFileName = qualifiedConfigFileName.replace(':', '_');
            qualifiedConfigFileName = qualifiedConfigFileName.replace(';', '_');

            File fileToSend = new File(qualifiedConfigFileName);

            FileReader reader = new FileReader(conff);
            FileWriter writer = new FileWriter(fileToSend);
            writer.append(prependedLine);
            int c;
            while (-1 != (c = reader.read())) {
               writer.write(c);
            }
            try{
               reader.close();
            } catch(IOException e) {
               ReportingUtils.logError(e);
            }
            try {
               writer.close();
            } catch(IOException e) {
               ReportingUtils.logError(e);
            }
            try {

               URL url = new URL("http://valelab.ucsf.edu/~MM/upload_file.php");

               List<File> flist = new ArrayList<>();
               flist.add(fileToSend);
               // for each of a colleciton of files to send...
               for (Object o0 : flist) {
                  File f0 = (File) o0;
                  try {
                     httpu.upload(url, f0);
                  } catch (java.net.UnknownHostException e) {
                     returnValue = e.toString();

                  } catch (IOException e) {
                     returnValue = e.toString();
                  } catch (SecurityException e) {
                     returnValue = e.toString();
                  } catch (Exception e) {
                     returnValue = e.toString();
                  }
               }
            } catch (MalformedURLException e) {
               returnValue = e.toString();
            }


            // now delete the temporary file
            if(!fileToSend.delete())
               ReportingUtils.logError("Couldn't delete temporary file " +qualifiedConfigFileName );

         }
      } catch (IOException e) {
         returnValue = e.toString();
      }
      return returnValue;

   }

   private void onCloseWindow() {
      for (int i = 0; i < pages_.length; i++) {
         pages_[i].saveSettings();
      }
      savePosition();

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
         }
      }
      if (microModel_.getSendConfiguration()) {
         class Uploader extends Thread {

            private String statusMessage_;

            public Uploader() {
               super("uploader");
               statusMessage_ = "";
            }

            @Override
            public void run() {
               statusMessage_ = UploadCurrentConfigFile();
            }

            public String Status() {
               return statusMessage_;
            }
         }
         Uploader u = new Uploader();
         u.start();
         try {
            u.join();
         } catch (InterruptedException ex) {
            Logger.getLogger(ConfigWizard.class.getName()).log(Level.SEVERE, null, ex);
         }
         if (0 < u.Status().length()) {
            ReportingUtils.logError("Error uploading configuration file: " + u.Status());
            //ReportingUtils.showMessage("Error uploading configuration file:\n" + u.Status());
         }
      }

      studio_.profile().getSettings(ConfigWizard.class).putBoolean(
           CFG_OKAY_TO_SEND, microModel_.getSendConfiguration());

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

   /**
    * Read string out of stream
    */
   private static String readStream(InputStream is) throws IOException {
      StringBuffer bf = new StringBuffer();
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      String line;
      //Note: not exactly the original
      while ((line = br.readLine()) != null) {
         bf.append(line);
         bf.append("\n");
      }
      return bf.toString();
   }
}
