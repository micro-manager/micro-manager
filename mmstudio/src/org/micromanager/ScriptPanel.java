///////////////////////////////////////////////////////////////////////////////
//FILE:          ScriptPanel.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, January 19, 2008

//COPYRIGHT:    University of California, San Francisco, 2008

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.PlainDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

import mmcorej.CMMCore;

import org.jeditsyntax.JEditTextArea;
import org.jeditsyntax.JavaTokenMarker;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ScriptingEngine;
import org.micromanager.utils.ScriptingGUI;
import org.micromanager.script.BeanshellEngine;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.MMScriptException;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.util.JConsole;

import com.swtdesigner.SwingResourceManager;
import java.io.BufferedReader;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.FileDialogs.FileType;
import org.micromanager.utils.HotKeysDialog;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.TooltipTextMaker;


public final class ScriptPanel extends MMFrame implements MouseListener, ScriptingGUI {
   private static final long serialVersionUID = 1L;
   private static final int HISTORYSIZE = 100;
   private JTable scriptTable_;
   private static ScriptTableModel model_;
   private final JEditTextArea scriptPane_;
   private boolean scriptPaneSaved_;
   private File scriptFile_;
   private JTextField immediatePane_;
   private JSplitPane rightSplitPane_;
   private JSplitPane splitPane_;
   private Vector<String> immediatePaneHistory_ = new Vector<String>(HISTORYSIZE);
   private int immediatePaneHistoryIndex_ = 0;
   private Preferences prefs_;
   private static ScriptingEngine interp_;
   private ScriptInterface parentGUI_;
   private JTextPane messagePane_;
   private StyleContext sc_;
   private Interpreter beanshellREPLint_;
   private JConsole cons_;
   
   private static final FileType BSH_FILE
           = new FileType("BSH_FILE","Beanshell files",
                    System.getProperty("user.home") + "/MyScript.bsh",
                    true, "bsh");

   private static final String SCRIPT_DIRECTORY = "script_directory";
   private static final String SCRIPT_FILE = "script_file_";
   private static final String RIGHT_DIVIDER_LOCATION = "right_divider_location";
   private static final String DIVIDER_LOCATION = "divider_location";
   private static final String EXT_POS = "bsh";
   private static final String EXT_ACQ = "xml";
   private static final String APP_NAME = "MMScriptPanel";
   private static final String blackStyleName_ = "blackStyle";
   private static final String redStyleName_ = "Red";
   private final MMStudioMainFrame gui_;

   /*
    * Table model that manages the shortcut script table
    */
   private class ScriptTableModel extends AbstractTableModel {
      private static final long serialVersionUID = 1L;
      private static final int columnCount_ = 1;
      private ArrayList<File> scriptFileArray_;
      private ArrayList<Long> lastModArray_;

      public ScriptTableModel () {
         scriptFileArray_ = new ArrayList<File>();
         lastModArray_ = new ArrayList<Long>();
      }
      
      public Boolean HasScriptAlready(File f) {
         Boolean preExisting = false;
         for (File scriptFile:scriptFileArray_) {
            if (scriptFile.getAbsolutePath().equals(f.getAbsolutePath()))
                  preExisting=true;
         }
         return preExisting;
      }
      
      public void AddScript(File f) {
         if (false == HasScriptAlready(f)) {
            scriptFileArray_.add(f);
            lastModArray_.add(f.lastModified());
         }
      }

      public void GetCell(File f, int[] cellAddress)
      {
         int index = scriptFileArray_.indexOf(f);
         if (index >= 0) {
            cellAddress[0] = index / columnCount_;
            cellAddress[1] = index % columnCount_;
         }
      }

      public void RemoveScript(int rowNumber, int columnNumber) {
         if ((rowNumber >= 0) && (isScriptAvailable(rowNumber, columnNumber)) ) {
            scriptFileArray_.remove((rowNumber * columnCount_) + columnNumber);
            lastModArray_.remove((rowNumber * columnCount_) + columnNumber);
         }
      }

      public File getScript(int rowNumber, int columnNumber) {
         if ((rowNumber >= 0) && (columnNumber >= 0) && ( isScriptAvailable(rowNumber, columnNumber)) )
            return scriptFileArray_.get( (rowNumber * columnCount_) + columnNumber);
         return null;
      }

      public Long getLastMod(int rowNumber, int columnNumber) {
         if ((rowNumber >= 0) && (columnNumber >= 0) && ( isScriptAvailable(rowNumber, columnNumber)) )
            return lastModArray_.get( (rowNumber * columnCount_) + columnNumber);
         return null;
      }

      public void setLastMod(int rowNumber, int columnNumber, Long lastMod) {
         if ((rowNumber >= 0) && (columnNumber >= 0) && ( isScriptAvailable(rowNumber, columnNumber)) )
            lastModArray_.set((rowNumber * columnCount_) + columnNumber, lastMod);
      }

      public boolean isScriptAvailable(int rowNumber, int columnNumber) {
         if ( (rowNumber >= 0) && (columnNumber >=0) && ((rowNumber * columnCount_) + columnNumber) < scriptFileArray_.size())
            return true;
         else
            return false;
      }

      public ArrayList<File> getFileArray() {
         return scriptFileArray_;
      }

      public int getRowCount() {
         if (scriptFileArray_ != null)
            return (int) Math.ceil (((double)scriptFileArray_.size() / (double) columnCount_));
         return 0;
      }

      public int getColumnCount() {
         return columnCount_;
      }

      @Override
      public String getColumnName(int columnIndex) {
         return "Script-Shortcuts";
      }

      public Object getValueAt(int rowIndex, int columnIndex) {
         if (rowIndex >= 0 && (isScriptAvailable(rowIndex, columnIndex) )) {
            return scriptFileArray_.get((rowIndex * columnCount_) + columnIndex).getName();
         } 
         return null;
      }
   }


   public class SelectionListener implements ListSelectionListener {
       JTable table_;
       int lastRowSelected_ = -1;
       int lastColumnSelected_ = -1;
       boolean multipleStarted_ = false;
   
       // It is necessary to keep the table since it is not possible
       // to determine the table from the event's source
       SelectionListener(JTable table) {
           this.table_ = table;
       }
       public void valueChanged(ListSelectionEvent e) {
           // we might get two events when both column and row are changed.  Repsond only to the second one 
           if (e.getValueIsAdjusting() && !multipleStarted_) {
              multipleStarted_ = true;
              return;
           }
           multipleStarted_ = false;
           int row = table_.getSelectedRow();
           int column = table_.getSelectedColumn();
           if ( (row >= 0) && (model_.isScriptAvailable(row, column)) ) {
              if (row != lastRowSelected_ || column != lastColumnSelected_) {
                  // check for changes and offer to save if needed
                  if (!promptToSave(lastRowSelected_)) {
                     table_.changeSelection(lastRowSelected_, lastColumnSelected_, false, false);
                     return;
                  }
                  File file = model_.getScript(row, column);
                  if (EXT_POS.equals(getExtension(file))) {
                     try {
                        FileReader in = new FileReader(file);
                        scriptPane_.read(in);
                        in.close();
                        scriptFile_ = file;
                        scriptPaneSaved_ = true;
                        setTitle(file.getName());
                        model_.setLastMod(table_.getSelectedRow(), 0, file.lastModified());
                     } catch (Exception ee) {
                        ReportingUtils.logError(ee);
                     }
                  } else if (EXT_ACQ.equals(getExtension(file))) {
                     scriptPane_.setText("gui.loadAcquisition(\"" + file.getAbsolutePath() + 
                        "\");\ngui.startAcquisition();");
                     scriptPaneSaved_ = true;
                  }
                  
               } 
           }
           lastRowSelected_ = row;
           lastColumnSelected_ = column;
       }
   }


   public final void createBeanshellREPL() {
      // Create console and REPL interpreter:
      cons_ = new JConsole();
      if (gui_.defaultScriptFont_ != null)
         cons_.setFont(gui_.defaultScriptFont_);
      
      beanshellREPLint_ = new Interpreter(cons_);
      new Thread(beanshellREPLint_, "Beanshell interpreter").start();

      try {
       File f = new File("scripts/mm_beanshell_startup.bsh");
       if (f.exists())
          beanshellREPLint_.source("scripts/mm_beanshell_startup.bsh");
        else {
          f = new File("../scripts/mm_beanshell_startup.bsh");
          if (f.exists())
             beanshellREPLint_.source("../scripts/mm_beanshell_startup.bsh");
        }
      } catch (FileNotFoundException e) {
         ReportingUtils.showError(e);
      } catch (IOException e) {
         ReportingUtils.showError(e);
      } catch (EvalError e) {
         ReportingUtils.showError(e);
      }
      
      // This command allows variables to be inspected in the command-line
      // (e.g., typing "x;" causes the value of x to be returned):
      beanshellREPLint_.setShowResults(true);
      
   }
   
   public JConsole getREPLCons() {
      return cons_;
   }
   
   /**
    * Create the dialog
    */
   public ScriptPanel(CMMCore core, MMOptions options, MMStudioMainFrame gui) {
      super();
      gui_ = gui;

      // Beanshell REPL Console
      createBeanshellREPL();
      
      // Needed when Cancel button is pressed upon save file warning
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            if (!promptToSave(-1))
               return;
            prefs_.putInt(RIGHT_DIVIDER_LOCATION, rightSplitPane_.getDividerLocation());
            prefs_.putInt(DIVIDER_LOCATION, splitPane_.getDividerLocation());
            saveScriptsToPrefs();
            savePosition();
            setVisible(false);
         }
      });

      setVisible(false);

      interp_ = new BeanshellEngine(this);
      interp_.setInterpreter(beanshellREPLint_);
      
      setTitle("Script Panel");
      setIconImage(SwingResourceManager.getImage(PropertyEditor.class, "icons/microscope.gif"));
      setBounds(100, 100, 550, 495);
      int buttonHeight = 15;
      Dimension buttonSize = new Dimension(80, buttonHeight);
      int gap = 5; // determines gap between buttons

      Preferences root = Preferences.userNodeForPackage(this.getClass());
      prefs_ = root.node(root.absolutePath() + "/ScriptPanel");
      setPrefsNode(prefs_);

      Rectangle r = getBounds();
      loadPosition(r.x, r.y, r.width, r.height);

      final JPanel leftPanel = new JPanel();
      SpringLayout spLeft = new SpringLayout();
      leftPanel.setLayout(spLeft);
      //leftPanel.setBackground(Color.gray);

      final JPanel topRightPanel = new JPanel();
      SpringLayout spTopRight = new SpringLayout();
      topRightPanel.setLayout(spTopRight);
      //topRightPanel.setBackground(Color.gray);

      final JPanel bottomRightPanel = new JPanel();
      bottomRightPanel.setLayout(new BoxLayout(bottomRightPanel, BoxLayout.Y_AXIS));
      bottomRightPanel.setBackground(Color.white);

      final JButton addButton = new JButton();
      addButton.setFont(new Font("", Font.PLAIN, 10));
      addButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            addScript();
         }
      });
      addButton.setText("Add");
      addButton.setToolTipText("Add shortcut to beanshell script in file system");
      addButton.setPreferredSize(buttonSize);
      spLeft.putConstraint(SpringLayout.NORTH, addButton, gap, SpringLayout.NORTH, leftPanel);
      spLeft.putConstraint(SpringLayout.WEST, addButton, gap, SpringLayout.WEST, leftPanel);
      leftPanel.add(addButton);

      final JButton removeButton = new JButton();
      removeButton.setMargin(new Insets(0,0,0,0));
      removeButton.setFont(new Font("", Font.PLAIN, 10));
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeScript();
         }
      });
      removeButton.setText("Remove");
      removeButton.setToolTipText("Remove currently selected shortcut");
      removeButton.setPreferredSize(buttonSize);
      spLeft.putConstraint(SpringLayout.NORTH, removeButton, gap, SpringLayout.NORTH, leftPanel);
      spLeft.putConstraint(SpringLayout.WEST, removeButton, gap, SpringLayout.EAST, addButton);
      leftPanel.add(removeButton);
     

      final JButton hotkeyButton = new JButton();
      hotkeyButton.setMargin(new Insets(0,0,0,0));
      hotkeyButton.setFont(new Font("", Font.PLAIN, 10));
      hotkeyButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            HotKeysDialog hk = new HotKeysDialog(getBackground());
            //hk.setBackground(getBackground());
         }
      });
      hotkeyButton.setText("ShortCuts");
      hotkeyButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip("Opens " +
      		"shortcuts manager window.  Allows the creation " +
      		"of keyboard shortcuts to automatically run scripts"));
      hotkeyButton.setPreferredSize(buttonSize);
      spLeft.putConstraint(SpringLayout.NORTH, hotkeyButton, gap, SpringLayout.NORTH, leftPanel);
      spLeft.putConstraint(SpringLayout.WEST, hotkeyButton, gap, SpringLayout.EAST, removeButton);
      leftPanel.add(hotkeyButton);
      
      // Scrollpane for shortcut table
      final JScrollPane scrollPane = new JScrollPane();
      leftPanel.add(scrollPane);
      spLeft.putConstraint(SpringLayout.EAST, scrollPane, 0, SpringLayout.EAST, leftPanel);
      spLeft.putConstraint(SpringLayout.SOUTH, scrollPane, -gap, SpringLayout.SOUTH, leftPanel);
      spLeft.putConstraint(SpringLayout.WEST, scrollPane, gap, SpringLayout.WEST, leftPanel);
      spLeft.putConstraint(SpringLayout.NORTH, scrollPane, gap, SpringLayout.SOUTH, removeButton);

      // Editor Pane
      scriptPane_ = new JEditTextArea();
      scriptPane_.setTokenMarker(new JavaTokenMarker());
      if (gui_.defaultScriptFont_ != null) {
         scriptPane_.getPainter().setFont(gui_.defaultScriptFont_);
      }
      
      scriptPane_.getDocument().putProperty(PlainDocument.tabSizeAttribute, 3);
      scriptPane_.setBackground(Color.WHITE);
      scriptPane_.getDocument().addDocumentListener(new MyDocumentListener());
      scriptPane_.setMinimumSize(new Dimension(300, 300));
      scriptPane_.setMaximumSize(new Dimension(800, 800));
      scriptPane_.setPreferredSize(new Dimension(800, 300));
      scriptPaneSaved_ = true;
      scriptPane_.setFocusTraversalKeysEnabled(false);

      spTopRight.putConstraint(SpringLayout.EAST, scriptPane_, 0, SpringLayout.EAST, topRightPanel);
      spTopRight.putConstraint(SpringLayout.SOUTH, scriptPane_, 0, SpringLayout.SOUTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, scriptPane_, 0, SpringLayout.WEST, topRightPanel);
      spTopRight.putConstraint(SpringLayout.NORTH, scriptPane_, buttonHeight + 2 * gap, SpringLayout.NORTH, topRightPanel);
      topRightPanel.add(scriptPane_);
      

      bottomRightPanel.add(cons_);
      
      // Immediate Pane (executes single lines of script)
      immediatePane_ = new JTextField();
      immediatePane_.setFont(new Font("Courier New", Font.PLAIN, 12));
      immediatePane_.setBackground(Color.WHITE);

      // 'Consume' the enter key if it was pressed in this pane
      immediatePane_.addActionListener(new immediatePaneListener());
      // Implement History with up and down keys
      immediatePane_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_KP_UP || e.getKeyCode() == KeyEvent.VK_UP) {
               doImmediatePaneHistoryUp();
            } else if (e.getKeyCode() == KeyEvent.VK_KP_DOWN || e.getKeyCode() == KeyEvent.VK_DOWN) {
               doImmediatePaneHistoryDown();
            }
         }
      });
      immediatePane_.setMinimumSize(new Dimension(100, 15));
      immediatePane_.setMaximumSize(new Dimension(2000, 15));

      // Message (output) pane
      messagePane_ = new JTextPane();
      messagePane_.setFont(new Font("Courier New", Font.PLAIN, 12));
      messagePane_.setBackground(Color.WHITE);
      final JScrollPane messageScrollPane = new JScrollPane(messagePane_);
      messageScrollPane.setMinimumSize(new Dimension(100, 30));
      messageScrollPane.setMaximumSize(new Dimension(2000, 2000));

      // Set up styles for the messagePane
      sc_ = new StyleContext();
      Style blackStyle_ = messagePane_.getLogicalStyle();
      blackStyle_ = sc_.addStyle(blackStyleName_, blackStyle_);
      StyleConstants.setForeground(blackStyle_, Color.black);
      Style redStyle_ = sc_.addStyle(redStyleName_, null);
      StyleConstants.setForeground(redStyle_, Color.red);

      // disable user input to the messagePane
      messagePane_.setKeymap(null);
          
      
      // Pane with buttons
      scriptTable_ = new JTable();
      scriptTable_.setFont(new Font("", Font.PLAIN, 12));
      model_ = new ScriptTableModel();
      scriptTable_.setModel(model_);
      scriptTable_.setCellSelectionEnabled(true);
      scriptTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      SelectionListener listener = new SelectionListener(scriptTable_);
      scriptTable_.getSelectionModel().addListSelectionListener(listener);
      scriptTable_.getColumnModel().getSelectionModel().addListSelectionListener(listener);
      // use the enter key to 'run' a script
      InputMap im = scriptTable_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      im.put (KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
      scrollPane.setViewportView(scriptTable_);
      // catch double clicks
      scriptTable_.addMouseListener(this);

      final JButton runPaneButton = new JButton();
      topRightPanel.add(runPaneButton);
      runPaneButton.setFont(new Font("", Font.PLAIN, 10));
      runPaneButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
             runPane();
         }
      });
      runPaneButton.setText("Run");
      runPaneButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, runPaneButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, runPaneButton, gap, SpringLayout.WEST, topRightPanel);
      
      final JButton stopButton = new JButton();
      topRightPanel.add(stopButton);
      stopButton.setFont(new Font("", Font.PLAIN, 10));
      stopButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            stopScript();
         }
      });
      stopButton.setText("Stop");
      stopButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, stopButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, stopButton, gap, SpringLayout.EAST, runPaneButton);

      final JButton newButton = new JButton();
      topRightPanel.add(newButton);
      newButton.setFont(new Font("", Font.PLAIN, 10));
      newButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            newPane();
         }
      });
      newButton.setText("New");
      newButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, newButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, newButton, gap, SpringLayout.EAST, stopButton);

      final JButton openButton = new JButton();
      topRightPanel.add(openButton);
      openButton.setFont(new Font("", Font.PLAIN, 10));
      openButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            openScriptInPane();
         }
      });
      openButton.setText("Open");
      openButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, openButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, openButton, gap, SpringLayout.EAST, newButton);
      
      final JButton saveButton = new JButton();
      topRightPanel.add(saveButton);
      saveButton.setFont(new Font("", Font.PLAIN, 10));
      saveButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            saveScript(-1);
         }
      });
      saveButton.setText("Save");
      saveButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, saveButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, saveButton, gap, SpringLayout.EAST, openButton);
      
      final JButton saveAsButton = new JButton();
      saveAsButton.setMargin(new Insets(0,0,0,0));
      topRightPanel.add(saveAsButton);
      saveAsButton.setFont(new Font("", Font.PLAIN, 10));
      saveAsButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
             saveScriptAs();
         }
      });
      saveAsButton.setText("Save As");
      saveAsButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, saveAsButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, saveAsButton, gap, SpringLayout.EAST, saveButton);


      final JButton helpButton = new JButton();
      helpButton.setMargin(new Insets(0,0,0,0));
      topRightPanel.add(helpButton);
      helpButton.setFont(new Font("", Font.PLAIN, 10));
      helpButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            try {
               ij.plugin.BrowserLauncher.openURL("https://valelab.ucsf.edu/~MM/MMwiki/index.php/Script_Panel_GUI");
            } catch (IOException e1) {
               ReportingUtils.showError(e1);
            }
         }
      });
      helpButton.setText("Help");
      helpButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, helpButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, helpButton, gap, SpringLayout.EAST, saveAsButton);

      // Set up basic structure
      leftPanel.setMinimumSize(new Dimension(180, 130));
      rightSplitPane_ = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topRightPanel, bottomRightPanel);
      rightSplitPane_.setOneTouchExpandable(true);
      int rightDividerLocation = prefs_.getInt(RIGHT_DIVIDER_LOCATION, 200);
      rightSplitPane_.setDividerLocation(rightDividerLocation);
      splitPane_ = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplitPane_);
      splitPane_.setOneTouchExpandable(true);
      int dividerLocation = prefs_.getInt(DIVIDER_LOCATION, 200);
      splitPane_.setDividerLocation(dividerLocation);
      //rightSplitPane.setMinimumSize(minimumSize);
      splitPane_.setMinimumSize(new Dimension(180, 130));
      rightSplitPane_.setResizeWeight(1.0);
      splitPane_.setResizeWeight(0.0);

      getContentPane().add(splitPane_);
      
      // Load the shortcut table based on saved preferences
      getScriptsFromPrefs();
   }

   protected void stopScript() {
      interp_.stopRequest();
   }

   protected class MyDocumentListener
                    implements DocumentListener {
       public void insertUpdate(DocumentEvent e) {
          scriptPaneSaved_ = false;
       }
       public void removeUpdate(DocumentEvent e) {
          scriptPaneSaved_ = false;
       }
       public void changedUpdate(DocumentEvent e) {
          scriptPaneSaved_ = false;
       }
   }
 

   /**
    * Prompt and save file if contents were modified
    * @param row - row number of script in question
    * @return - true if the file was saved
    */
   public boolean promptToSave(int row) {
      if (scriptPaneSaved_)
         return true;
      String message;
      if (scriptFile_ != null)
         message = "Save changes to " + scriptFile_.getName() + "?";
      else
         message = "Save script?";
      int result = JOptionPane.showConfirmDialog(this,
            message,
            APP_NAME, JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE);
      switch (result) {
         case JOptionPane.YES_OPTION:
            saveScript(row);
         case JOptionPane.NO_OPTION:
            // avoid prompting again:
            scriptPaneSaved_ = true;
      }                                                                      

      return true;                                                           
   } 

   /**
    * Lets the user select a script file to add to the shortcut table
    */
   private void addScript() {
      if (scriptFile_ != null && !model_.HasScriptAlready(scriptFile_)) {
         addScriptToModel(scriptFile_);
      }
      else if (scriptFile_ == null && ! scriptPaneSaved_) {
         if (!promptToSave(-1))
            return;
         addScriptToModel(scriptFile_);
      }
      else
      {
         // check for changes and offer to save if needed
         if (!promptToSave(-1))
            return;

         File curFile = FileDialogs.openFile(this, "Select a Beanshell script", BSH_FILE);

         if (curFile != null) {
               prefs_.put(SCRIPT_FILE, curFile.getAbsolutePath());
               // only creates a new file when a file with this name does not exist
               addScriptToModel(curFile);
         }
      }
   }
   
   private void addScriptToModel(File curFile) {
      model_.AddScript(curFile);
      model_.fireTableDataChanged();
      int[] cellAddress = new int[2];
      model_.GetCell(curFile, cellAddress);
      scriptTable_.changeSelection(cellAddress[0], cellAddress[1], false, false);
   }

   /**
    * Removes the selected script from the shortcut table
    */
   private void removeScript()
   {
      if (!promptToSave(-1))
         return;

      model_.RemoveScript(scriptTable_.getSelectedRow(), scriptTable_.getSelectedColumn());
      model_.fireTableDataChanged();
      scriptPane_.setText("");
      scriptPaneSaved_ = true;
      this.setTitle("");
      scriptFile_ = null;
   }

   /**
    * Saves the script in the editor Pane
    * @param row - row of script in editor pane (or <0 for current)
    */
   private void saveScript(int row)
   {
      if (scriptFile_ == null) {
         saveScriptAs();
         return;
      }
      if (scriptFile_ != null && (scriptTable_.getSelectedRow() > -1) ) {
         if (row < 0)
            row = scriptTable_.getSelectedRow();
         boolean modified = (scriptFile_.lastModified() != model_.getLastMod(row, 0));
         if (modified) {
            int result = JOptionPane.showConfirmDialog(this,
                  "Script was changed on disk.  Continue saving anyways?",
                  APP_NAME, JOptionPane.YES_NO_OPTION,
                  JOptionPane.INFORMATION_MESSAGE);
            switch (result) {
               case JOptionPane.YES_OPTION:
                  break;
               case JOptionPane.NO_OPTION:
                  return;
            }
         }
      }
      try {
         FileWriter fw = new FileWriter(scriptFile_);
         fw.write(scriptPane_.getText());
         fw.close();
         scriptPaneSaved_ = true;
         int[] cellAddress = new int[2];
         model_.GetCell(scriptFile_, cellAddress);
         model_.setLastMod(cellAddress[0], 0, scriptFile_.lastModified());
         gui_.message("Saved file: " + scriptFile_.getName());
      } catch (IOException ioe){
         ReportingUtils.showError(ioe);
      } catch (MMScriptException mex) {
         // nothing to do
      }
   }

   /**
    * Saves script in the editor Pane.  Always prompts for a new name
    */
   private void saveScriptAs () 
   {
      //String suffixes [] = {"bsh"};
      File saveFile = FileDialogs.save(this, "Save beanshell script", BSH_FILE);
      if (saveFile != null) {
         try {
            // Add .bsh extension if file did not have an extension itself
            String fileName = saveFile.getName();
            if (fileName.length() < 5 || (fileName.charAt(fileName.length()-4)!= '.' && fileName.charAt(fileName.length()-5) != '.') )
               fileName+= ".bsh";
            saveFile = new File(saveFile.getParentFile(), fileName);

            FileWriter fw = new FileWriter(saveFile);
            fw.write(scriptPane_.getText());
            fw.close();
            scriptFile_ = saveFile;
            prefs_.put(SCRIPT_FILE, saveFile.getAbsolutePath());
            scriptPaneSaved_ = true;
            this.setTitle(saveFile.getName());
         } catch (IOException ioe){
            ReportingUtils.showError(ioe);
         }
      }
   }

   /*
    * Runs the content of the editor Pane in the REPL context.
    */
   @SuppressWarnings("unused")
   private void injectPane() {
      interp_.setInterpreter(beanshellREPLint_);
      runPane();
      interp_.resetInterpreter();
   }
   
   /*
    * Runs the content of the editor Pane
    */
   private void runPane()
   {
      File curFile = model_.getScript(scriptTable_.getSelectedRow(), scriptTable_.getSelectedColumn());
      // check if file on disk was modified.  
      if (curFile != null) {
         boolean modified = (curFile.lastModified() != model_.getLastMod(scriptTable_.getSelectedRow(), 0));
         if (modified) {
            int result = JOptionPane.showConfirmDialog(this,
                  "Script was changed on disk.  Re-load from disk?",
                  APP_NAME, JOptionPane.YES_NO_CANCEL_OPTION,
                  JOptionPane.INFORMATION_MESSAGE);
            switch (result) {
               case JOptionPane.YES_OPTION:
                  try {
                     FileReader in = new FileReader(curFile);
                     scriptPane_.read(in);
                     in.close();
                     scriptFile_ = curFile;
                     scriptPaneSaved_ = true;
                     model_.setLastMod(scriptTable_.getSelectedRow(), 0, curFile.lastModified());
                  } catch (Exception e) {
                     handleException (e);
                  }
                  break;
               case JOptionPane.NO_OPTION:
                  break;
               case JOptionPane.CANCEL_OPTION:                                        
                  return;
            }
         }
      }
      try {
    
         interp_.evaluateAsync(scriptPane_.getText());
         //interp_.evaluate(scriptPane_.getText());
      } catch (MMScriptException e) {
         ReportingUtils.logError(e);
         messageException(e.getMessage(), -1);
      }
   }


   /*
   * Runs the content of the editor Pane
   */
   public static void runFile(File curFile)
   {
      // check if file on disk was modified.
      if (curFile != null) {
         try {
            interp_.evaluateAsync(getContents(curFile));
            //interp_.evaluate(scriptPane_.getText());
         } catch (MMScriptException e) {
            ReportingUtils.logError(e);
            //messageException(e.getMessage(), -1);
         }
      }
   }

   public static   String getContents(File aFile) {
      StringBuilder contents = new StringBuilder();

      try {
         //use buffering, reading one line at a time
         //FileReader always assumes default encoding is OK!
         BufferedReader input =  new BufferedReader(new FileReader(aFile));
         try {
           String line = null; //not declared within while loop
           /*
           * readLine is a bit quirky :
           * it returns the content of a line MINUS the newline.
           * it returns null only for the END of the stream.
           * it returns an empty String if two newlines appear in a row.
           */
           while (( line = input.readLine()) != null){
             contents.append(line);
             contents.append(System.getProperty("line.separator"));
           }
        }
        finally {
            input.close();
        }
    }
    catch (IOException ex){
      ex.printStackTrace();
    }

    return contents.toString();
  }


   /*
    * Empties the editor Pane and deselects the shortcuts, in effect creating a 'blank' editor pane
    */
   private void newPane()
   {
      // check for changes and offer to save if needed
      if (!promptToSave(-1))
         return;

      int row = scriptTable_.getSelectedRow();
      int column =  scriptTable_.getSelectedColumn();
      scriptTable_.changeSelection(row, column, true, false);
      scriptPane_.setText("");
      scriptPaneSaved_ = true;
      scriptFile_ = null;
      this.setTitle("");
      scriptPane_.requestFocusInWindow();
   }

   /*
    * Opens a script in the editor Pane
    */
   private void openScriptInPane() {
      // check for changes and offer to save if needed
      if (!promptToSave(-1))
         return;

      File curFile = FileDialogs.openFile(this, "Choose Beanshell script", BSH_FILE);

      if (curFile != null) {
         try {
            prefs_.put(SCRIPT_FILE, curFile.getAbsolutePath());
            int row = scriptTable_.getSelectedRow();
            int column =  scriptTable_.getSelectedColumn();
            scriptTable_.changeSelection(row, column, true, false);
            FileReader in = new FileReader(curFile);
            scriptPane_.read(in);
            in.close();
            scriptFile_ = curFile;
            scriptPaneSaved_ = true;
            this.setTitle(curFile.getName());
         } catch (Exception e) {
            handleException (e);
         } finally {

         }
      }
   }


   public void insertScriptingObject(String varName, Object obj) {
      try {
         interp_.insertGlobalObject(varName, obj);
         beanshellREPLint_.set(varName,obj);
      } catch (Exception e) {
         handleException(e);
      }
   }  

   public void setParentGUI(ScriptInterface parent) {
      parentGUI_ = parent;      
      insertScriptingObject("gui", parent);
   } 

   private void runImmediatePane() 
   {
      try {
         immediatePaneHistory_.addElement(immediatePane_.getText());
         immediatePaneHistoryIndex_ = immediatePaneHistory_.size();
         interp_.evaluateAsync(immediatePane_.getText());
         immediatePane_.setText("");
      } catch (MMScriptException e) {
         messageException(e.getMessage(), -1);
      }
   }

   private void doImmediatePaneHistoryUp()
   {
      if (immediatePaneHistoryIndex_ > 0)
         immediatePaneHistoryIndex_--;
      if (immediatePaneHistoryIndex_ >= 0 && immediatePaneHistoryIndex_ < immediatePaneHistory_.size())
         immediatePane_.setText((String) immediatePaneHistory_.elementAt(immediatePaneHistoryIndex_));
   }

   private void doImmediatePaneHistoryDown()
   {
      if (immediatePaneHistoryIndex_ < immediatePaneHistory_.size())
         immediatePaneHistoryIndex_++;
      if (immediatePaneHistoryIndex_ < immediatePaneHistory_.size())
         immediatePane_.setText((String) immediatePaneHistory_.elementAt(immediatePaneHistoryIndex_));
      else
         immediatePane_.setText("");
   }

   public  void closePanel() {
      if (!promptToSave(-1))
         return;
      savePosition();
      saveScriptsToPrefs();
      dispose();
   }

   /**
    * Displays exception message in a generic dialog box
    * @param e
    */
   public void handleException (Exception e) {
      ReportingUtils.showError(e);
   }

   /**
    * Displays text string in message window
    * @param text
    */
   public void message (String text) {
      messagePane_.setCharacterAttributes(sc_.getStyle(blackStyleName_), false);
      messagePane_.replaceSelection(text + "\n");
      cons_.print("\n"+text,java.awt.Color.black);
      showPrompt();
   }

   public void Message (String text) {
      message(text);
   }

   /**
    * Displays text string in message window in color red
    * @param text
    * @param lineNumber_ 
    */
   public void messageException (String text, int lineNumber) {
      // move cursor to the error line number
      if (lineNumber >= 0) {
         scriptPane_.scrollTo(lineNumber - 1, 0);
         scriptPane_.select(scriptPane_.getLineStartOffset(lineNumber - 1), scriptPane_.getLineEndOffset(lineNumber - 1));
      }
      messagePane_.setCharacterAttributes(sc_.getStyle(redStyleName_), false);
      messagePane_.replaceSelection(text + "\n");
      cons_.print("\n"+text,java.awt.Color.red);
      showPrompt();

   }

   private void showPrompt() {
     String promptStr;
      try {
        promptStr = (String) beanshellREPLint_.eval("getBshPrompt();");
      } catch (EvalError e) {
          ReportingUtils.logError(e);
        promptStr = "bsh % ";
      } 
     cons_.print("\n"+promptStr,java.awt.Color.darkGray);  
   }
   
   /**
    * Clears the content of the message window
    */
   public void clearOutput() {
      boolean originalAccessibility = true;
      try {
         beanshellREPLint_.eval("bsh.console.text");
      } catch (EvalError e) {
         originalAccessibility = false;
         try {
            beanshellREPLint_.eval("setAccessibility(true);");
         } catch (EvalError e1) {
               ReportingUtils.showError(e1);
         }
      }
      try {
         beanshellREPLint_.eval("bsh.console.text.setText(\"\");"
               + "setAccessibility("+originalAccessibility+");");
      } catch (EvalError e) {
           ReportingUtils.showError(e);
      }
   }

   public void ClearOutput() {
      clearOutput();
   }
   
   public void clear() {
      clearOutput();
   }

   public void updateGUI () {
      parentGUI_.refreshGUI();
   }

   public void getScriptsFromPrefs ()
   { 
      // restore previously listed scripts from prefs
      int j = 0;
      String script;
      boolean isFile = false;
      do {
         script = prefs_.get(SCRIPT_FILE + j, null);
         if ( (script != null) && (!script.equals("") ) )
         {
            File file = new File(script);
            if (file.isFile()) {
               model_.AddScript(file);
               isFile = true;
            }
         }
         j++;
      }
      while ( (script != null) && (!script.equals("") )  && isFile);
   }


   public static ArrayList<File> getScriptList() {
      return model_.getFileArray();
   }

   public void saveScriptsToPrefs ()
   { 
      File file;
      ArrayList<File> scriptFileArray = model_.getFileArray();
      for (int i = 0; i < scriptFileArray.size(); i ++) 
      {
         file = scriptFileArray.get(i);
         if (file != null) {
            prefs_.put(SCRIPT_FILE + i, file.getAbsolutePath() );
         }
      }

      // Add one empty script, so as not to read in stale variables
      prefs_.put(SCRIPT_FILE + scriptFileArray.size(), "");
   }

   private String getExtension(File f) {
      String ext = null;
      String s = f.getName();
      int i = s.lastIndexOf('.');

      if (i > 0 &&  i < s.length() - 1) {
         ext = s.substring(i+1).toLowerCase();
      }
      return ext;
   }

   /**
    * MouseListener implementation
    * @param MouseEvent e
    */
   public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() >= 2)
         runPane();
   }  
            
   public void mousePressed(MouseEvent e) { 
   }
                                                                             
   public void mouseReleased(MouseEvent e) {                                 
   }                                                                         
                                                                             
   public void mouseEntered(MouseEvent e) {                                  
   }                                                                         
                                                                             
   public void mouseExited(MouseEvent e) {                                   
   } 

   class immediatePaneListener implements ActionListener {
      public void actionPerformed(ActionEvent evt) {
         runImmediatePane();
      }
   }

   /**
    * Displays a message coming from a separate thread.
    */
   private class ExecuteDisplayMessage implements Runnable {

      String msg_;
      boolean error_ = false;
      int lineNumber_ = -1;
      
      public ExecuteDisplayMessage(String txt, boolean error, int lineNumber) {
         msg_ = txt;
         error_ = error;
         lineNumber_ = lineNumber;
      }
      public ExecuteDisplayMessage(String txt, boolean error) {
         msg_ = txt;
         error_ = error;
      }
      
      public ExecuteDisplayMessage(String txt) {
         msg_ = txt;
      }
      
      public void run() {
         if (error_)
            messageException(msg_, lineNumber_);
            
         else
            message(msg_);
      }
   }
   
   public void displayMessage(String message) {
      SwingUtilities.invokeLater(new ExecuteDisplayMessage(message));
      ReportingUtils.logMessage(message);
   }

   public void displayError(String text) {
      SwingUtilities.invokeLater(new ExecuteDisplayMessage(text, true));
      ReportingUtils.logError(text);
   }
   
   public void displayError(String text, int lineNumber) {
      SwingUtilities.invokeLater(new ExecuteDisplayMessage(text, true, lineNumber));
      ReportingUtils.logError(text);
   }

   public boolean stopRequestPending() {
      return interp_.stopRequestPending();
   }

   public void sleep(long ms) throws MMScriptException {
      if (ms > 0)
         interp_.sleep(ms);
   }

}
