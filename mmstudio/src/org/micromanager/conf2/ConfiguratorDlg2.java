///////////////////////////////////////////////////////////////////////////////
//FILE:          ConfiguratorDlg2.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 2, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:          $Id: ConfiguratorDlg.java 7760 2011-09-19 18:20:49Z ziah $
//
package org.micromanager.conf2;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
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
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.WindowConstants;
import javax.swing.border.LineBorder;
import javax.swing.text.DefaultCaret;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import org.micromanager.MMStudio;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.HttpUtils;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.ReportingUtils;

/**
 * Configuration Wizard main panel.
 * Based on the dialog frame to be activated as part of the
 * MMStudio
 */
public class ConfiguratorDlg2 extends MMDialog {

    private static final long serialVersionUID = 1L;
    private JLabel pagesLabel_;
    private JButton backButton_;
    private JButton nextButton_;
    private PagePanel pages_[];
    private int curPage_ = 0;
    private MicroscopeModel microModel_;
    private final CMMCore core_;
    private Preferences prefs_;
    private static final String APP_NAME = "Configurator";
    private JLabel titleLabel_;
    private JEditorPane helpTextPane_;
    private final String defaultPath_;

    private static final String CFG_OKAY_TO_SEND = "CFG_Okay_To_Send";


    /**
     * Create the application
     */
    public ConfiguratorDlg2(CMMCore core, String defFile) {
        super();
        core_ = core;
        defaultPath_ = defFile;
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setModal(true);
        initialize();
    }

    /**
     * Initialize the contents of the frame
     */
    private void initialize() {
        prefs_ = Preferences.userNodeForPackage(this.getClass());
        
        org.micromanager.utils.HotKeys.active_ = false;

        addWindowListener(new WindowAdapter() {

           @Override
           public void windowClosing(WindowEvent arg0) {
              onCloseWindow();
           }
        });
        setResizable(false);
        getContentPane().setLayout(null);
        setTitle("Hardware Configuration Wizard");
        setBounds(50, 100, 859, 641);
        loadPosition(50, 100);

        final JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(584, 28, 259, 526);
        getContentPane().add(scrollPane);
        scrollPane.getViewport().setViewPosition(new Point(0,0));

        helpTextPane_ = new JEditorPane();
        scrollPane.setViewportView(helpTextPane_);
        helpTextPane_.setEditable(false);
        //helpTextPane_.setBorder(new LineBorder(Color.black, 1, false));

        DefaultCaret caret = (DefaultCaret)helpTextPane_.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        
        helpTextPane_.setContentType("text/html; charset=ISO-8859-1");

        nextButton_ = new JButton();
        nextButton_.addActionListener(new ActionListener() {          
            public void actionPerformed(ActionEvent arg0) {
                if (curPage_ == pages_.length - 1) {
                    
                    // call the last page's exit
                    pages_[curPage_].exitPage(true);
                    onCloseWindow();
                } else {
                    setPage(curPage_ + 1);
                }
            }
        });


        nextButton_.setText("Next >");
        nextButton_.setBounds(750, 565, 93, 23);
        getContentPane().add(nextButton_);
        getRootPane().setDefaultButton(nextButton_);

        backButton_ = new JButton();
        backButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                setPage(curPage_ - 1);
            }
        });
        backButton_.setText("< Back");
        backButton_.setBounds(647, 565, 93, 23);
        getContentPane().add(backButton_);
        pagesLabel_ = new JLabel();
        pagesLabel_.setBorder(new LineBorder(Color.black, 1, false));
        pagesLabel_.setBounds(9, 28, 565, 560);
        getContentPane().add(pagesLabel_);

        // add page panels
        pages_ = new PagePanel[6];

        int pageNumber = 0;
        pages_[pageNumber++] = new IntroPage(prefs_);
        pages_[pageNumber++] = new DevicesPage(prefs_);
        pages_[pageNumber++] = new RolesPage(prefs_);
        pages_[pageNumber++] = new DelayPage(prefs_);
        pages_[pageNumber++] = new LabelsPage(prefs_);
        pages_[pageNumber++] = new FinishPage(prefs_);

        microModel_ = new MicroscopeModel();
        // default to allow sending of configuration to micro-manager.org server
        boolean bvalue = prefs_.getBoolean(CFG_OKAY_TO_SEND, true);
        microModel_.setSendConfiguration( bvalue);
        microModel_.loadAvailableDeviceList(core_);
        microModel_.setFileName(defaultPath_);
        Rectangle r = pagesLabel_.getBounds();

        titleLabel_ = new JLabel();
        titleLabel_.setText("Title");
        titleLabel_.setBounds(9, 4, 578, 21);
        getContentPane().add(titleLabel_);
        for (int i = 0; i < pages_.length; i++) {
            try {
                pages_[i].setModel(microModel_, core_);
                pages_[i].loadSettings();
                pages_[i].setBounds(r);
                pages_[i].setTitle("Step " + (i + 1) + " of " + pages_.length + ": " + pages_[i].getTitle());
                pages_[i].setParentDialog(this);
            } catch (Exception e) {
                ReportingUtils.logError(e);
            }
        }
        setPage(0);

    }

    private void setPage(int i) {
        // try to exit the current page

        if (i > 0) {
            if (!pages_[curPage_].exitPage(curPage_ < i ? true : false)) {
                return;
            }
        }

        int newPage;
        if (i < 0) {
            newPage = 0;
        } else if (i >= pages_.length) {
            newPage = pages_.length - 1;
        } else {
            newPage = i;
        }

        // try to enter the new page
        if (!pages_[newPage].enterPage(curPage_ > newPage ? true : false)) {
            return;
        }

        // everything OK so we can proceed with swapping pages
        getContentPane().remove(pages_[curPage_]);
        curPage_ = newPage;

        getContentPane().add(pages_[curPage_]);
        // Java 2.0 specific, uncomment once we go for Java 2
        //frame.getContentPane().setComponentZOrder(pages_[curPage_], 0);
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

        // By default, load plain text help
        helpTextPane_.setContentType("text/plain");
        helpTextPane_.setText(pages_[curPage_].getHelpText());
         
        // Try to load html help text
        try {
            File curDir = new File(".");
            String helpFileName = pages_[curPage_].getHelpFileName();
            if (helpFileName == null) {
                return;
            }
            URL htmlURL = ConfiguratorDlg2.class.getResource(helpFileName);
            String helpText = readStream(ConfiguratorDlg2.class.getResourceAsStream(helpFileName));
            helpTextPane_.setContentType("text/html; charset=ISO-8859-1");
            helpTextPane_.setText(helpText);
            
            
        } catch (MalformedURLException e1) {
            ReportingUtils.showError(e1);
        } catch (IOException e) {
            ReportingUtils.showError(e);
        }
    }

    private String UploadCurrentConfigFile() {
        String returnValue = "";
        try {
            HttpUtils httpu = new HttpUtils();
            List<File> list = new ArrayList<File>();
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
                }catch(Exception e){
                    ReportingUtils.logError(e);
                }
                try {
                writer.close();
                }catch(Exception e){
                    ReportingUtils.logError(e);
                }
                try {

                    URL url = new URL("http://valelab.ucsf.edu/~MM/upload_file.php");

                    List<File> flist = new ArrayList<File>();
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
            int result = JOptionPane.showConfirmDialog(this,
                    "Save changes to the configuration file?\nIf you press YES you will get a chance to change the file name.",
                    APP_NAME, JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);

            switch (result) {
                case JOptionPane.YES_OPTION:
                    saveConfiguration();
                    break;
                case JOptionPane.NO_OPTION:
                    break;
                case JOptionPane.CANCEL_OPTION:
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
                Logger.getLogger(ConfiguratorDlg2.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (0 < u.Status().length()) {
                ReportingUtils.logError("Error uploading configuration file: " + u.Status());
                //ReportingUtils.showMessage("Error uploading configuration file:\n" + u.Status());
            }
        }

        prefs_.putBoolean(CFG_OKAY_TO_SEND, microModel_.getSendConfiguration());
        
        org.micromanager.utils.HotKeys.active_ = true;
        dispose();
    }

    private void saveConfiguration() {
        File f = FileDialogs.save(this,
                "Create a config file", MMStudio.MM_CONFIG_FILE);

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
