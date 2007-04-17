///////////////////////////////////////////////////////////////////////////////
//FILE:          MMScriptFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, November 10, 2005
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
package org.micromanager.script;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SpringLayout;

import org.micromanager.utils.DeviceControlGUI;
import org.micromanager.utils.MMFrame;

import com.swtdesigner.SwingResourceManager;

/**
 * Scripting window. 
 */
public class MMScriptFrame extends MMFrame {

   private static final long serialVersionUID = 1L;
   
   private MMScriptView scriptView_;
   private SpringLayout springLayout_;
   private DeviceControlGUI parentGUI_;
   private static final String SCRIPT_DIR = "script_dir";

   /**
    * Constructor. Initalizes the GUI. 
    */
   public MMScriptFrame() {
      super();
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/ScriptFrame"));
      setIconImage(SwingResourceManager.getImage(MMScriptFrame.class, "/icons/script.png"));
      loadPosition(100, 100, 500, 375);
            
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      setTitle("MMScript Console");
      springLayout_ = new SpringLayout();
      getContentPane().setLayout(springLayout_);
      
      final JMenuBar menuBar = new JMenuBar();
      setJMenuBar(menuBar);
      
      final JMenu fileMenu = new JMenu();
      fileMenu.setText("File");
      menuBar.add(fileMenu);
      
      final JMenuItem newMenuItem = new JMenuItem();
      newMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.CreateNewScript();
            UpdateTitle();
         }
      });
      newMenuItem.setText("New");
      fileMenu.add(newMenuItem);
      
      // Open
      final JMenuItem openMenuItem = new JMenuItem();
      openMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.openScriptFile();
            UpdateTitle();
         }
      });
      
      openMenuItem.setText("Open...");
      fileMenu.add(openMenuItem);

      fileMenu.addSeparator();
      
      // Save Script
      final JMenuItem saveMenuItem = new JMenuItem();
      saveMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.saveScriptFile(false);
            UpdateTitle();
         }
      });
      saveMenuItem.setActionCommand("SaveScript");
      saveMenuItem.setText("Save");
      fileMenu.add(saveMenuItem);
      
      // Save as...
      final JMenuItem saveAsMenuItem = new JMenuItem();
      saveAsMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.saveScriptFile(true);
            UpdateTitle();
         }
      });
      saveAsMenuItem.setText("Save As...");
      fileMenu.add(saveAsMenuItem);
      
      final JMenu editMenu = new JMenu();
      editMenu.setText("Edit");
      menuBar.add(editMenu);
      
      final JMenuItem cutMenuItem = new JMenuItem();
      cutMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.cut();
         }
      });
      cutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_MASK));
      editMenu.add(cutMenuItem);
      cutMenuItem.setText("Cut");
      
      final JMenuItem copyMenuItem = new JMenuItem();
      copyMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK));
      copyMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.copy();
         }
      });
      copyMenuItem.setText("Copy");
      editMenu.add(copyMenuItem);
      
      final JMenuItem pasteMenuItem = new JMenuItem();
      pasteMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.paste();
         }
      });
      pasteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK));
      pasteMenuItem.setText("Paste");
      editMenu.add(pasteMenuItem);

      editMenu.addSeparator();

      final JMenuItem selectAllMenuItem = new JMenuItem();
      selectAllMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.selectAll();
         }
      });
      selectAllMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_MASK));
      selectAllMenuItem.setText("Select All");
      editMenu.add(selectAllMenuItem);
      
      final JMenu toolsMenu = new JMenu();
      toolsMenu.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.runScript();
         }
      });
      toolsMenu.setText("Tools");
      menuBar.add(toolsMenu);
      
      JMenuItem menuItem_7 = new JMenuItem();
      menuItem_7.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.runScript();
         }
      });
      menuItem_7.setText("Run Script");
      toolsMenu.add(menuItem_7);
      
      menuItem_7 = new JMenuItem();
      menuItem_7.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            scriptView_.clearOutput();
         }
      });
      menuItem_7.setText("Clear Output");
      toolsMenu.add(menuItem_7);
      
      final JMenu helpMenu = new JMenu();
      helpMenu.setText("Help");
      menuBar.add(helpMenu);
      
      final JMenuItem menuItem = new JMenuItem();
      menuItem.setText("About...");
      helpMenu.add(menuItem);
      
      // Set-up the tabbed view
      // ----------------------
      
      scriptView_ = new MMScriptView();
      scriptView_.setScriptDir(getPrefsNode().get(SCRIPT_DIR, null));
      getContentPane().add(scriptView_);
      springLayout_.putConstraint(SpringLayout.EAST, scriptView_, -5, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, scriptView_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, scriptView_, 5, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, scriptView_, 5, SpringLayout.WEST, getContentPane());
            
      // prompt to save on closing the window
      WindowListener wndCloser = new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            if (scriptView_.getScriptDir() != null)
               getPrefsNode().put(SCRIPT_DIR, scriptView_.getScriptDir());
            if (!scriptView_.promptToSave())
               return;
            savePosition();
          }
      };
      addWindowListener(wndCloser);

      // add global console object to the scripting engine
      scriptView_.injectScriptingObject("console", this);
   }
               
   private void UpdateTitle() {
      this.setTitle("MMScript Console - " + scriptView_.getFileName());
   }
   
   public void insertScriptingObject(String name, Object obj) {
      scriptView_.injectScriptingObject(name, obj);
   }
   
   public void setParentGUI(DeviceControlGUI parent) {
      parentGUI_ = parent;
      scriptView_.injectScriptingObject("gui", parent);
   }

   public void clearOutput(){
      scriptView_.clearOutput();
   }

   public void message(String text){
      scriptView_.message(text);
   }

}








