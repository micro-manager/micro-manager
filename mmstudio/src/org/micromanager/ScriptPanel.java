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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;
import java.util.prefs.Preferences;

import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JFileChooser;
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
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.PlainDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

import mmcorej.CMMCore;

import org.jeditsyntax.JEditTextArea;
import org.jeditsyntax.JavaTokenMarker;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.ScriptingEngine;
import org.micromanager.api.ScriptingGUI;
import org.micromanager.script.BeanshellEngine;
import org.micromanager.script.ScriptPanelMessageWindow;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.MMScriptException;

import com.swtdesigner.SwingResourceManager;


public class ScriptPanel extends MMFrame implements MouseListener, ScriptingGUI {
   private static final long serialVersionUID = 1L;
   private JTable scriptTable_;
   private ScriptTableModel model_;
   private final JEditTextArea scriptPane_;
   private boolean scriptPaneSaved_;
   private File scriptFile_;
   private JTextField immediatePane_;
   private JSplitPane rightSplitPane_;
   private JSplitPane splitPane_;
   private Vector<String> immediatePaneHistory_ = new Vector<String>(50);
   private int immediatePaneHistoryIndex_ = 0;
   private Preferences prefs_;
   private ScriptingEngine interp_;
   private ScriptInterface parentGUI_;
   private JTextPane messagePane_;
   private StyleContext sc_;
   private ScriptPanelMessageWindow messageWindow_;

   private static final String SCRIPT_DIRECTORY = "script_directory";
   private static final String SCRIPT_FILE = "script_file_";
   private static final String RIGHT_DIVIDER_LOCATION = "right_divider_location";
   private static final String DIVIDER_LOCATION = "divider_location";
   private static final String EXT_POS = "bsh";
   private static final String EXT_ACQ = "xml";
   private static final String APP_NAME = "MMScriptPanel";
   private static final String blackStyleName_ = "blackStyle";
   private static final String redStyleName_ = "Red";

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

      public void setData (ArrayList<File> scriptFileArray) {
         scriptFileArray_ = scriptFileArray;
         lastModArray_ = new ArrayList<Long>();
         Iterator<File> it = scriptFileArray.iterator();
         while (it.hasNext()) {
            File f = (File) it.next();
            lastModArray_.add(f.lastModified());
         }
      }

      public void AddScript(File f) {
         scriptFileArray_.add(f);
         lastModArray_.add(f.lastModified());
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
                  if (!promptToSave()) {
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
                        System.out.println(ee.getMessage());
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


   /**
    * File filter class for Open/Save file choosers 
    */
   private class ScriptFileFilter extends FileFilter {
      final private String DESCRIPTION;

      public ScriptFileFilter() {
         super();
         DESCRIPTION = new String("MM beanshell files (*.bsh)");
      }

      public boolean accept(File f){
         if (f.isDirectory())
            return true;

         if (EXT_POS.equals(getExtension(f)))
            return true;

         if (EXT_ACQ.equals(getExtension(f)))
            return true;

         return false;
      }

      public String getDescription(){
         return DESCRIPTION;
      }

   }

   /**
    * Create the dialog
    */
   public ScriptPanel(CMMCore core, MMOptions options) {
      super();

      // Needed when Cancel button is pressed upon save file warning
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
            if (!promptToSave()) 
               return;
            prefs_.putInt(RIGHT_DIVIDER_LOCATION, rightSplitPane_.getDividerLocation());
            prefs_.putInt(DIVIDER_LOCATION, splitPane_.getDividerLocation());
            saveScriptsToPrefs();
            savePosition();
            dispose();
         }
      });

      interp_ = new BeanshellEngine(this);
      
      new GUIColors();
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
      addButton.setPreferredSize(buttonSize);
      spLeft.putConstraint(SpringLayout.NORTH, addButton, gap, SpringLayout.NORTH, leftPanel);
      spLeft.putConstraint(SpringLayout.WEST, addButton, gap, SpringLayout.WEST, leftPanel);
      leftPanel.add(addButton);

      final JButton removeButton = new JButton();
      removeButton.setFont(new Font("", Font.PLAIN, 10));
      removeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            removeScript();
         }
      });
      removeButton.setText("Remove");
      removeButton.setPreferredSize(buttonSize);
      spLeft.putConstraint(SpringLayout.NORTH, removeButton, gap, SpringLayout.NORTH, leftPanel);
      spLeft.putConstraint(SpringLayout.WEST, removeButton, gap, SpringLayout.EAST, addButton);
      leftPanel.add(removeButton);
      
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
      scriptPane_.setFont(new Font("Courier New", Font.PLAIN, 12));
      scriptPane_.getDocument().putProperty(PlainDocument.tabSizeAttribute, 3);
      scriptPane_.setBackground(Color.WHITE);
      scriptPane_.getDocument().addDocumentListener(new MyDocumentListener());
      scriptPane_.setMinimumSize(new Dimension(300, 300));
      scriptPane_.setMaximumSize(new Dimension(800, 800));
      scriptPane_.setPreferredSize(new Dimension(800, 300));
      scriptPaneSaved_ = true;
      scriptPane_.setFocusTraversalKeysEnabled(false);
      //InputMap imEditor = scriptPane_.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      //imEditor.put (KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "none");

      spTopRight.putConstraint(SpringLayout.EAST, scriptPane_, 0, SpringLayout.EAST, topRightPanel);
      spTopRight.putConstraint(SpringLayout.SOUTH, scriptPane_, 0, SpringLayout.SOUTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, scriptPane_, 0, SpringLayout.WEST, topRightPanel);
      spTopRight.putConstraint(SpringLayout.NORTH, scriptPane_, buttonHeight + 2 * gap, SpringLayout.NORTH, topRightPanel);
      topRightPanel.add(scriptPane_);

      // Immediate Pane (executes single lines of script)
      immediatePane_ = new JTextField();
      immediatePane_.setFont(new Font("Courier New", Font.PLAIN, 12));
      immediatePane_.setBackground(Color.WHITE);

      // 'Consume' the enter key if it was pressed in this pane
      immediatePane_.addActionListener(new immediatePaneListener());
      // Implement History with up and down keys
      immediatePane_.addKeyListener(new KeyAdapter() {
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
      bottomRightPanel.add(immediatePane_);

      // Message (output) pane
      messagePane_ = new JTextPane();
      messagePane_.setFont(new Font("Courier New", Font.PLAIN, 12));
      messagePane_.setBackground(Color.WHITE);
      final JScrollPane messageScrollPane = new JScrollPane(messagePane_);
      messageScrollPane.setMinimumSize(new Dimension(100, 30));
      messageScrollPane.setMaximumSize(new Dimension(2000, 2000));
      bottomRightPanel.add(messageScrollPane);

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
            saveScript();
         }
      });
      saveButton.setText("Save");
      saveButton.setPreferredSize(buttonSize);
      spTopRight.putConstraint(SpringLayout.NORTH, saveButton, gap, SpringLayout.NORTH, topRightPanel);
      spTopRight.putConstraint(SpringLayout.WEST, saveButton, gap, SpringLayout.EAST, openButton);
      
      final JButton saveAsButton = new JButton();
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
    * @return - true if the file was saved
    */
   public boolean promptToSave() {
      if (scriptPaneSaved_)
         return true;
      String message;
      if (scriptFile_ != null)
         message = "Save changes to " + scriptFile_.getName() + "?";
      else
         message = "Save script?";
      int result = JOptionPane.showConfirmDialog(this,
            message,
            APP_NAME, JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.INFORMATION_MESSAGE);
      switch (result) {
         case JOptionPane.YES_OPTION:
            saveScript();
            return true;
         case JOptionPane.NO_OPTION:
            // avoid prompting again:
            scriptPaneSaved_ = true;
            return true;
         case JOptionPane.CANCEL_OPTION:                                        
            return false;                                                       
      }                                                                      

      return true;                                                           
   } 

   /**
    * Lets the user select a script file to add to the shortcut table
    */
   private void addScript() {
      // check for changes and offer to save if needed
      if (!promptToSave()) 
         return;

      File curFile;

      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new ScriptFileFilter());

      String scriptListDir = prefs_.get(SCRIPT_FILE, null);
      if (scriptListDir != null) {
         fc.setSelectedFile(new File(scriptListDir));
      }

      // int retval = fc.showOpenDialog(this);
      int retval = fc.showDialog(this, "New/Open");
      if (retval == JFileChooser.APPROVE_OPTION) {
         curFile = fc.getSelectedFile();
         try {
            scriptListDir = curFile.getParent();
            prefs_.put(SCRIPT_DIRECTORY, scriptListDir);
            prefs_.put(SCRIPT_FILE, curFile.getAbsolutePath());
            // only creates a new file when a file with this name does not exist
            curFile.createNewFile();
         } catch (Exception e) {
            handleException (e);
         } finally {
            model_.AddScript(curFile);
            model_.fireTableDataChanged();
            int[] cellAddress = new int[2];
            model_.GetCell(curFile, cellAddress);
            scriptTable_.changeSelection(cellAddress[0], cellAddress[1], false, false);
         }
      }
   }

   /**
    * Removes the selected script from the shortcut table
    */
   private void removeScript()
   {
      if (!promptToSave()) 
         return;

      model_.RemoveScript(scriptTable_.getSelectedRow(), scriptTable_.getSelectedColumn());
      model_.fireTableDataChanged();
      scriptPane_.setText("");
      scriptPaneSaved_ = true;
      this.setTitle("");
   }

   /**
    * Saves the script in the editor Pane
    */
   private void saveScript()
   {
      if (scriptFile_ == null) {
         saveScriptAs();
         return;
      }
      if (scriptFile_ != null && (scriptTable_.getSelectedRow() > -1) ) {
         boolean modified = (scriptFile_.lastModified() != model_.getLastMod(scriptTable_.getSelectedRow(), 0));
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
         JOptionPane.showMessageDialog(this, "File saved");
      } catch (IOException ioe){
         message("IO exception" + ioe.getMessage());
      }
   }

   /**
    * Saves script in the editor Pane.  Always prompts for a new name
    */
   private void saveScriptAs () 
   {
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new ScriptFileFilter());

      if (scriptFile_ != null) {
         fc.setSelectedFile(new File(scriptFile_.getAbsolutePath()));
      } else {
         String scriptListFile = prefs_.get(SCRIPT_FILE, null);
         if (scriptListFile != null) {
            fc.setSelectedFile(new File(scriptListFile));
         }
      }

      int retval = fc.showSaveDialog(this);
      if (retval == JFileChooser.APPROVE_OPTION) {
         File saveFile = fc.getSelectedFile();
         try {
            // Add .bsh extension of file did not have an extension itself
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
            message("IO exception" + ioe.getMessage());
         }
      }
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
      } catch (MMScriptException e) {
         messageException(e.getMessage(), -1);
      }
   }

   /*
    * Empties the editor Pane and deselects the shortcuts, in effect creating a 'blank' editor pane
    */
   private void newPane()
   {
      // check for changes and offer to save if needed
      if (!promptToSave()) 
         return;

      int row = scriptTable_.getSelectedRow();
      int column =  scriptTable_.getSelectedColumn();
      scriptTable_.changeSelection(row, column, true, false);
      scriptPane_.setText("");
      scriptPaneSaved_ = true;
      scriptFile_ = null;
      this.setTitle("");
   }

   /*
    * Opens a script in the editor Pane
    */
   private void openScriptInPane() {
      // check for changes and offer to save if needed
      if (!promptToSave()) 
         return;

      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new ScriptFileFilter());

      String scriptListFile = prefs_.get(SCRIPT_FILE, null);
      if (scriptListFile != null) {
         fc.setSelectedFile(new File(scriptListFile));
      }

      // int retval = fc.showOpenDialog(this);
      int retval = fc.showOpenDialog(this);
      File curFile;
      if (retval == JFileChooser.APPROVE_OPTION) {
         curFile = fc.getSelectedFile();
         try {
            scriptListFile = curFile.getParent();
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

   public void closePanel() {
      if (!promptToSave()) 
         return;
      if (messageWindow_ != null)
         messageWindow_.closeWindow();
      savePosition();
      saveScriptsToPrefs();
      dispose();
   }

   /**
    * Displays exception message in a generic dialog box
    * @param e
    */
   public void handleException (Exception e) {
      String errText = "Exeption occured: " + e.getMessage();
      JOptionPane.showMessageDialog(this, errText);
   }

   /**
    * Displays text string in message window
    * @param text
    */
   public void message (String text) {
      messagePane_.setCharacterAttributes(sc_.getStyle(blackStyleName_), false);
      messagePane_.replaceSelection(text + "\n");
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
   }

   /**
    * Clears the content of the message window
    */
   public void clearOutput() {
      messagePane_.setText("");
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
         if ( (script != null) && (script != "") ) 
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
   }

   public void displayError(String text) {
      SwingUtilities.invokeLater(new ExecuteDisplayMessage(text, true));        
   }
   
   public void displayError(String text, int lineNumber) {
      SwingUtilities.invokeLater(new ExecuteDisplayMessage(text, true, lineNumber));        
   }

   public boolean stopRequestPending() {
      return interp_.stopRequestPending();
   }

   public void sleep(long ms) throws MMScriptException {
      interp_.sleep(ms);
   }

}
