///////////////////////////////////////////////////////////////////////////////
//FILE:          MMScriptView.java
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

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader; // jizhen 4.17.2007
import java.io.EOFException; // jizhen 4.17.2007
import java.io.IOException; // jizhen 4.17.2007
import java.util.GregorianCalendar;

//import javax.swing.JEditorPane; // remove jizhen 4.17.2007
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SpringLayout;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.micromanager.utils.ScriptFileFilter;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.ParseException;
import bsh.TargetError;
import bsh.util.JConsole;//jizhen 4.17.2007

import com.Ostermiller.Syntax.*;//jizhen 4.17.2007

/**
 * Encapsulated text editor and scripting engine
 */
public class MMScriptView extends JTabbedPane {
   private static final long serialVersionUID = -4762444284878352488L;
   
   private static final String NEW_FILE_NAME = "Unitled.bsh";
   private static final String APP_NAME = "MMScriptView";
   private Interpreter interp_;
   private final JEditorPaneWithPopup scriptPane_;
   private final JTextPane outputPane_;
   private final JScrollPane outputView_;
   private File scriptFile_;
   private String scriptDir_;
   private boolean scriptChanged_ = false;
   private JPopupMenu popupMenu_; 
//   private final JTextArea historyPane_; // jizhen 4.17.2007
   private JTextField commandLine_;
   private SpringLayout springLayout_;
   private HighlightedDocument document_;//jizhen 4.17.2007
      
   /**
    * Utility class with sole purpose of providing popup menu action
    * to JEditorPane
    */
   private class JEditorPaneWithPopup extends JTextPane { //JEditorPane { //jizhen 4.17.2007
      public void processMouseEvent( MouseEvent event )
      {
         if( event.isPopupTrigger() )
         {
            popupMenu_.show( event.getComponent(),
                           event.getX(), event.getY() );
         }

         super.processMouseEvent( event );
      }
      public JEditorPaneWithPopup() {
         super();
      }
      public JEditorPaneWithPopup(HighlightedDocument doc) {
          super(doc);
      }       
   }
   
   /**
    * Utility class to detect when the editor content is modified
    */
   private class UpdateListener implements DocumentListener {
      public void insertUpdate(DocumentEvent e) {
         scriptChanged_ = true;
      }
      public void removeUpdate(DocumentEvent e) {
         scriptChanged_ = true;
      }
      public void changedUpdate(DocumentEvent e) {
         scriptChanged_ = true;
      }
   }
   
   
   /**
    * create all GUI elements
    */
   public MMScriptView(){
      
      super();
      
      //jizhen 4.17.2007
      document_ = new HighlightedDocument();
      document_.setHighlightStyle(HighlightedDocument.JAVA_STYLE);  
      scriptPane_ = new JEditorPaneWithPopup(document_);
//      scriptPane_ = new JEditorPaneWithPopup();
      // eof jizhen
      scriptPane_.setFont(new Font("Courier New", Font.PLAIN, 14));
      scriptPane_.setBackground(Color.WHITE);
      JScrollPane psScript = new JScrollPane(scriptPane_);
      
      addTab("Script", null, psScript, null);
      popupMenu_ = getContextMenu();
      psScript.add(popupMenu_);
      enableEvents( AWTEvent.MOUSE_EVENT_MASK );
     
      outputPane_ = new JTextPane();
      outputPane_.setBackground(Color.LIGHT_GRAY);
      outputPane_.setEditable(false);
      
      outputView_ = new JScrollPane(outputPane_);
      outputView_.setFont(new Font("Courier New", Font.PLAIN, 14));
      addTab("Output", null, outputView_, null);
      
      // initalize the interpreter
//      interp_ = new Interpreter(); // jizhen 4.17.2007
      CreateNewScript();

      final JPanel panel = new JPanel();
      springLayout_ = new SpringLayout();
      panel.setLayout(springLayout_);
      addTab("Immediate", null, panel, null);
      
      // jizhen 4.17.2007
      JConsole console = new JConsole();
      panel.add(console);
      springLayout_.putConstraint(SpringLayout.SOUTH, console, -5, SpringLayout.SOUTH, panel);
      springLayout_.putConstraint(SpringLayout.EAST, console, -5, SpringLayout.EAST, panel);
      springLayout_.putConstraint(SpringLayout.NORTH, console, 5, SpringLayout.NORTH, panel);
      springLayout_.putConstraint(SpringLayout.WEST, console, 5, SpringLayout.WEST, panel);      
      
	  interp_ = new Interpreter(console);   
	  new Thread( interp_ ).start();
      
//	  commandLine_ = new JTextField();
//      commandLine_.addKeyListener(new KeyAdapter() {
//         public void keyPressed(KeyEvent e) {
//            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
//               runCommandLine();
//            }
//         }
//      });
//      commandLine_.setBorder(new LineBorder(Color.black, 1, false));
//      panel.add(commandLine_);
//      springLayout_.putConstraint(SpringLayout.SOUTH, commandLine_, -5, SpringLayout.SOUTH, panel);
//      springLayout_.putConstraint(SpringLayout.EAST, commandLine_, -5, SpringLayout.EAST, panel);
//      springLayout_.putConstraint(SpringLayout.NORTH, commandLine_, -35, SpringLayout.SOUTH, panel);
//      springLayout_.putConstraint(SpringLayout.WEST, commandLine_, 5, SpringLayout.WEST, panel);
//
//      historyPane_ = new JTextArea();
//      historyPane_.setBorder(new LineBorder(Color.black, 1, false));
//      historyPane_.setEditable(false);
//      panel.add(historyPane_);
//      springLayout_.putConstraint(SpringLayout.SOUTH, historyPane_, -5, SpringLayout.NORTH, commandLine_);
//      springLayout_.putConstraint(SpringLayout.EAST, historyPane_, 0, SpringLayout.EAST, commandLine_);
//      springLayout_.putConstraint(SpringLayout.NORTH, historyPane_, 5, SpringLayout.NORTH, panel);
//      springLayout_.putConstraint(SpringLayout.WEST, historyPane_, 0, SpringLayout.WEST, commandLine_);
	  
	  //eof jizhen
   }
   
   
   /**
    * Add global objects to the scripting environment
    * @param varName - variable name in the script space
    * @param obj
    */
   public void injectScriptingObject(String varName, Object obj){
      try {
         interp_.set(varName, obj);
      } catch (Exception e) {
         handleException(e);
      }
   }
   
   /**
    * Prompt and save file if contents were modified
    * @return - true if the file was saved
    */
   public boolean promptToSave() {
      if (!scriptChanged_)
         return true;
      int result = JOptionPane.showConfirmDialog(this,
            "Save changes to "+ scriptFile_.getName()+ "?",
            APP_NAME, JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.INFORMATION_MESSAGE);
      switch (result) {
      case JOptionPane.YES_OPTION:
         if (!saveScriptFile(true))
            return false;
         return true;
      case JOptionPane.NO_OPTION:
         return true;
      case JOptionPane.CANCEL_OPTION:
         return false;
      }
      return true;
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
    * Displays text string in the output pane
    * @param text
    */
   public void message(String text){
      String outText = outputPane_.getText();
      outText += text + "\n";
      outputPane_.setText(outText);
      setSelectedComponent(outputView_);
   }
   
   /**
    * Clear contents of the output pane
    */
   public void clearOutput(){
      outputPane_.setText("");
   }
   
   /**
    * Evaluates the contents of the script pane
    */
   public void runScript(){
      try {
         String script = scriptPane_.getText();
         GregorianCalendar today = new GregorianCalendar();
         message("Started " + today.getTime());
         interp_.eval(script);
         message("Ended " + today.getTime() + "\n");
      } catch (TargetError e) {
         // The script threw an exception
         Throwable t = e.getTarget();
         message("Script error: " + t.getMessage() + "\n");
      } catch (ParseException e) {
         // Parsing error
         message("Parsing error: " + e.getMessage() + "\n");
      } catch (EvalError e) {
         // General Error evaluating script
         message("Evaluation error: " + e.getMessage() + "\n");
      }   
   }
   
   private void runCommandLine(){
      try {
         interp_.eval(commandLine_.getText());
//         historyPane_.append(commandLine_.getText() + "\n"); // jizhen 4.17.2007
         commandLine_.setText("");
      } catch (TargetError e) {
         // The script threw an exception
         Throwable t = e.getTarget();
         message("Script error: " + t.getMessage() + "\n");
      } catch (ParseException e) {
         // Parsing error
         message("Parsing error: " + e.getMessage() + "\n");
      } catch (EvalError e) {
         // General Error evaluating script
         message("Evaluation error: " + e.getMessage() + "\n");
      }   
   }
   
   /**
    * Opens a text file in the workspace
    */
   public void openScriptFile(){
      
      if (!promptToSave())
         return;
      
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new ScriptFileFilter());
      if (scriptDir_ != null)
         fc.setCurrentDirectory(new File(scriptDir_));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         scriptFile_ = fc.getSelectedFile();
         try {
        	 
        	 //jizhen
        	 
//            FileReader in = new FileReader(scriptFile_);
//            scriptPane_.read(in, null); // jizhen 4.17.2007 can not read into styled doc: it use default styled doc
//        	  in.close();
            
            BufferedReader in = new BufferedReader(new FileReader(scriptFile_));
            String content="";
            String line="";
            try {
            	while (line!=null){
            		line = in.readLine();
            		if (line!=null) content+=line+"\n";
            	}
            } catch (EOFException e){            	
            }catch (IOException e){            	
            }            
            in.close();
            scriptPane_.setText(content);
            
            // jizhen 4.17.2007
            
            scriptDir_ = scriptFile_.getParent();
         } catch (Exception e) {
            handleException(e);
         }
         scriptChanged_ = false;
         scriptPane_.getDocument().addDocumentListener(new UpdateListener());
      }
   }
   
   /**
    * Saves the workspace contents to a file
    * @param saveAs - true to display SaveAs dialog 
    */
   public boolean saveScriptFile(boolean saveAs){
      JFileChooser fc = new JFileChooser();
      boolean saveFile = true;
      
      do {
         if (saveAs || scriptFile_ == null){
            if (scriptFile_ == null)
               scriptFile_ = new File(NEW_FILE_NAME);
            fc.setSelectedFile(scriptFile_);
            int retVal = fc.showSaveDialog(this);
            if (retVal == JFileChooser.APPROVE_OPTION) {
               scriptFile_ = fc.getSelectedFile();
               
               // check if file already exists
               if( scriptFile_.exists() ) { 
                  int sel = JOptionPane.showConfirmDialog( this,
                        "Overwrite " + scriptFile_.getName(),
                        "File Save",
                        JOptionPane.YES_NO_OPTION);
                  
                  if(sel == JOptionPane.YES_OPTION)
                     saveFile = true;
                  else
                     saveFile = false;
               }
            } else {
               return false; 
            }
         }
      } while (saveFile == false);
      
      
      try {
         BufferedWriter bw = new BufferedWriter(new FileWriter(scriptFile_));
         String script = scriptPane_.getText();
         bw.write(script);
         bw.close();
         scriptDir_ = scriptFile_.getParent();
         scriptChanged_ = false;
      } catch (Exception e) {
         handleException(e);
      }
      return true;
   }
   
   /**
    * Clears the workspace and creates the new script
    */
   public void CreateNewScript(){
      if(!promptToSave())
         return;
      
      scriptPane_.setText("");
      scriptFile_ = new File(NEW_FILE_NAME);
      scriptChanged_ = false;
      scriptPane_.getDocument().addDocumentListener(new UpdateListener());
   }


   public void copy() {
      scriptPane_.copy();
   }

   /* (non-Javadoc)
    * @see javax.swing.JEditorPane#getText()
    */
   public String getText() {
      return scriptPane_.getText();
   }

   /* (non-Javadoc)
    * @see javax.swing.JEditorPane#cut()
    */
   public void cut() {
      scriptPane_.cut();
   }

   /* (non-Javadoc)
    * @see javax.swing.JEditorPane#paste()
    */
   public void paste() {
      scriptPane_.paste();
   }

   /* (non-Javadoc)
    * @see javax.swing.JEditorPane#selectAll()
    */
   public void selectAll() {
      scriptPane_.selectAll();
   }
 
   /**
    * @return - current file name
    */
   public String getFileName(){
      return scriptFile_.getName();
   }

   /**
    * Builds content sensitive meny for the editor
    * @return - popup menu
    */
   private JPopupMenu getContextMenu(){
     
      // Create a popup menu
      JPopupMenu popupMenu = new JPopupMenu( "Menu" );
      
      final JMenuItem cutMenuItem = new JMenuItem();
      cutMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_MASK));
      cutMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            cut();
         }
      });
      cutMenuItem.setText("Cut");
      popupMenu.add(cutMenuItem);
      
      final JMenuItem copyMenuItem = new JMenuItem();
      copyMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK));
      copyMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            copy();
         }
      });
      copyMenuItem.setText("Copy");
      popupMenu.add(copyMenuItem);
      
      final JMenuItem pasteMenuItem = new JMenuItem();
      pasteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_MASK));
      pasteMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            paste();
         }
      });
      pasteMenuItem.setText("Paste");
      popupMenu.add(pasteMenuItem);
      
      popupMenu.addSeparator();
      
      final JMenuItem selectAllMenuItem = new JMenuItem();
      selectAllMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_MASK));
      selectAllMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            selectAll();
         }
      });
      selectAllMenuItem.setText("Select All");
      popupMenu.add(selectAllMenuItem);
      
      return popupMenu;
   }


   public String getScriptDir() {
      return scriptDir_;
   }


   public void setScriptDir(String dir) {
      scriptDir_ = dir;
   }
}

