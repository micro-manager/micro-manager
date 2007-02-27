///////////////////////////////////////////////////////////////////////////////
//FILE:          Configurator.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
// NOTE:         OBSOLETE
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 1, 2006
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
// CVS:          $Id$
//

package org.micromanager.conf;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

import mmcorej.CMMCore;

/**
 * Standalone configuration wizard utility.
 *
 */
public class Configurator {

   private JFrame frame;
   private JLabel pagesLabel_;
   private JButton backButton_;
   private JButton nextButton_;
   private PagePanel pages_[];
   private int curPage_ = 0;
   private MicroscopeModel microModel_;
   private CMMCore core_;
   private Preferences prefs_;
   private static final String APP_NAME = "Configurator";
   private JLabel titleLabel_;
   private JTextPane helpTextPane_;

   /**
    * Launch the application
    * @param args
    */
   public static void main(String args[]) {
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         Configurator window = new Configurator();
         window.frame.setVisible(true);
         window.frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   /**
    * Create the application
    */
   public Configurator() {
      initialize();
   }

   /**
    * Initialize the contents of the frame
    */
   private void initialize() {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      frame = new JFrame();
      frame.addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
            onCloseWindow();
         }
      });
      frame.setResizable(false);
      frame.getContentPane().setLayout(null);
      frame.setTitle("Micro-Manager Configurator");
      frame.setBounds(100, 100, 602, 519);
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      nextButton_ = new JButton();
      nextButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (curPage_ == pages_.length - 1)
               onCloseWindow();
            else
               setPage(curPage_ + 1);
         }
      });
      nextButton_.setText("Next >");
      nextButton_.setBounds(494, 462, 93, 23);
      frame.getContentPane().add(nextButton_);

      backButton_ = new JButton();
      backButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            setPage(curPage_ - 1);
         }
      });
      backButton_.setText("< Back");
      backButton_.setBounds(395, 462, 93, 23);
      frame.getContentPane().add(backButton_);

      pagesLabel_ = new JLabel();
      pagesLabel_.setBorder(new LineBorder(Color.black, 1, false));
      pagesLabel_.setBounds(9, 28, 578, 286);
      frame.getContentPane().add(pagesLabel_);
      
      // add page panels
      pages_ = new PagePanel[9];
      pages_[0] = new IntroPage(prefs_);
      //pages_[1] = new ComPortsPage(prefs_);
      pages_[1] = new DevicesPage(prefs_);
      pages_[2] = new EditPropertiesPage(prefs_);
      pages_[3] = new RolesPage(prefs_);
      pages_[4] = new DelayPage(prefs_);
      pages_[5] = new SynchroPage(prefs_);
      pages_[6] = new LabelsPage(prefs_);
      pages_[7] = new PresetsPage(prefs_);
      pages_[8] = new FinishPage(prefs_);
            
      core_ = new CMMCore();
      microModel_ = new MicroscopeModel();
      microModel_.loadAvailableDeviceList(core_);
      Rectangle r = pagesLabel_.getBounds();

      titleLabel_ = new JLabel();
      titleLabel_.setText("Title");
      titleLabel_.setBounds(9, 4, 578, 21);
      frame.getContentPane().add(titleLabel_);

      helpTextPane_ = new JTextPane();
      helpTextPane_.setEditable(false);
      helpTextPane_.setBorder(new LineBorder(Color.black, 1, false));
      helpTextPane_.setBounds(9, 320, 578, 136);
      frame.getContentPane().add(helpTextPane_);
      for (int i=0; i<pages_.length; i++) {
         pages_[i].setModel(microModel_, core_);
         pages_[i].loadSettings();
         pages_[i].setBounds(r);
         pages_[i].setTitle("Step " + (i+1) + " of " + pages_.length + ": " + pages_[i].getTitle());
      }
      setPage(0);
   }

   private void setPage(int i) {
      // try to exit the current page
      
      if (!pages_[curPage_].exitPage(curPage_ < i ? true : false))
         return;
      
      int newPage = 0;
      if (i < 0)
         newPage = 0;
      else if (i >= pages_.length)
         newPage = pages_.length - 1;
      else
         newPage = i;
      
      // try to enter the new page
      if (!pages_[newPage].enterPage(curPage_ > newPage ? true : false))
         return;
    
      // everything OK so we can proceed with swapping pages
      frame.getContentPane().remove(pages_[curPage_]);
      curPage_ = newPage;
      
      frame.getContentPane().add(pages_[curPage_]);
      // Java 2.0 specific, uncomment once we go for Java 2
      //frame.getContentPane().setComponentZOrder(pages_[curPage_], 0);
      frame.getContentPane().repaint();
      pages_[curPage_].refresh();
      
      if (curPage_ == 0)
         backButton_.setEnabled(false);
      else
         backButton_.setEnabled(true);
      
      if (curPage_ == pages_.length-1)
         nextButton_.setText("Exit");
      else
         nextButton_.setText("Next >");
      
      titleLabel_.setText(pages_[curPage_].getTitle());
      helpTextPane_.setText(pages_[curPage_].getHelpText());
 
   }
   
   private void onCloseWindow() {
      for (int i=0; i<pages_.length; i++) {
         pages_[i].saveSettings();
      }
      
      if (microModel_.isModified()) {
         int result = JOptionPane.showConfirmDialog(Configurator.this.frame,
               "Save changes to the configuration?",
               "APP_NAME", JOptionPane.YES_NO_CANCEL_OPTION,
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
      frame.dispose();      
   }

   private void saveConfiguration() {
      JFileChooser fc = new JFileChooser();
      boolean saveFile = true;
      File f;
      
      do {         
         fc.setSelectedFile(new File(microModel_.getFileName()));
         int retVal = fc.showSaveDialog(this.frame);
         if (retVal == JFileChooser.APPROVE_OPTION) {
            f = fc.getSelectedFile();
            
            // check if file already exists
            if( f.exists() ) { 
               int sel = JOptionPane.showConfirmDialog(this.frame,
                     "Overwrite " + f.getName(),
                     "File Save",
                     JOptionPane.YES_NO_OPTION);
               
               if(sel == JOptionPane.YES_OPTION)
                  saveFile = true;
               else
                  saveFile = false;
            }
         } else {
            return; 
         }
      } while (saveFile == false);
      
      try {
         microModel_.saveToFile(f.getAbsolutePath());
      } catch (MMConfigFileException e) {
         JOptionPane.showMessageDialog(this.frame, e.getMessage());           
      }
   }   
}
