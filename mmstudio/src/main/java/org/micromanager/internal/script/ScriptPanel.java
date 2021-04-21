///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------

// AUTHOR:       Nico Stuurman, nico@cmp.ucsf.edu, January 19, 2008

// COPYRIGHT:    University of California, San Francisco, 2008

// LICENSE:      This file is distributed under the BSD license.
// License text is included with the source distribution.

// This file is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

// IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.script;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.util.JConsole;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;
import org.micromanager.ScriptController;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.*;
import org.micromanager.internal.utils.FileDialogs.FileType;
import org.micromanager.propertymap.MutablePropertyMapView;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public final class ScriptPanel extends JFrame implements MouseListener, ScriptController {
  private static final long serialVersionUID = 1L;
  private static final int HISTORYSIZE = 100;
  private static final String STARTUP_SCRIPT =
      "path to the Beanshell script to run when the program starts up";
  private JTable scriptTable_;
  private static ScriptTableModel model_;
  private final RSyntaxTextArea scriptArea_;
  private final RTextScrollPane sp;
  private boolean scriptPaneSaved_;
  private File scriptFile_;
  private JTextField immediatePane_;
  private JSplitPane rightSplitPane_;
  private JSplitPane splitPane_;
  private JButton runButton_;
  private JButton stopButton_;
  private List<String> immediatePaneHistory_ = new ArrayList<>(HISTORYSIZE);
  private int immediatePaneHistoryIndex_ = 0;
  private static ScriptingEngine interp_;
  private JTextPane messagePane_;
  private StyleContext sc_;
  private Interpreter beanshellREPLint_;
  private JConsole cons_;

  public static final FileType BSH_FILE =
      new FileType(
          "BSH_FILE",
          "Beanshell files",
          System.getProperty("user.home") + "/MyScript.bsh",
          true,
          "bsh");

  private static final String SCRIPT_FILE = "script_file_";
  private static final String RIGHT_DIVIDER_LOCATION = "right_divider_location";
  private static final String DIVIDER_LOCATION = "divider_location";
  private static final String EXT_POS = "bsh";
  private static final String EXT_ACQ = "xml";
  private static final String APP_NAME = "MMScriptPanel";
  private static final String BLACK_STYLE_NAME = "blackStyle";
  private static final String RED_STYLE_NAME = "Red";
  private final Studio studio_;
  private final MutablePropertyMapView settings_;

  /*
   * Table model that manages the shortcut script table
   */
  private class ScriptTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private static final int COLUMN_COUNT = 1;
    private ArrayList<File> scriptFileArray_;
    private ArrayList<Long> lastModArray_;

    public ScriptTableModel() {
      scriptFileArray_ = new ArrayList<>();
      lastModArray_ = new ArrayList<>();
    }

    public Boolean HasScriptAlready(File f) {
      Boolean preExisting = false;
      for (File scriptFile : scriptFileArray_) {
        if (scriptFile.getAbsolutePath().equals(f.getAbsolutePath())) preExisting = true;
      }
      return preExisting;
    }

    public void AddScript(File f) {
      if (false == HasScriptAlready(f)) {
        scriptFileArray_.add(f);
        lastModArray_.add(f.lastModified());
      }
    }

    public void GetCell(File f, int[] cellAddress) {
      int index = scriptFileArray_.indexOf(f);
      if (index >= 0) {
        cellAddress[0] = index / COLUMN_COUNT;
        cellAddress[1] = index % COLUMN_COUNT;
      }
    }

    public void RemoveAllScripts() {
      scriptFileArray_.clear();
      lastModArray_.clear();
      fireTableDataChanged();
    }

    public void RemoveScript(int rowNumber, int columnNumber) {
      if ((rowNumber >= 0) && (isScriptAvailable(rowNumber, columnNumber))) {
        scriptFileArray_.remove((rowNumber * COLUMN_COUNT) + columnNumber);
        lastModArray_.remove((rowNumber * COLUMN_COUNT) + columnNumber);
      }
    }

    public File getScript(int rowNumber, int columnNumber) {
      if ((rowNumber >= 0) && (columnNumber >= 0) && (isScriptAvailable(rowNumber, columnNumber)))
        return scriptFileArray_.get((rowNumber * COLUMN_COUNT) + columnNumber);
      return null;
    }

    public Long getLastMod(int rowNumber, int columnNumber) {
      if ((rowNumber >= 0) && (columnNumber >= 0) && (isScriptAvailable(rowNumber, columnNumber)))
        return lastModArray_.get((rowNumber * COLUMN_COUNT) + columnNumber);
      return null;
    }

    public void setLastMod(int rowNumber, int columnNumber, Long lastMod) {
      if ((rowNumber >= 0) && (columnNumber >= 0) && (isScriptAvailable(rowNumber, columnNumber)))
        lastModArray_.set((rowNumber * COLUMN_COUNT) + columnNumber, lastMod);
    }

    public boolean isScriptAvailable(int rowNumber, int columnNumber) {
      return (rowNumber >= 0)
          && (columnNumber >= 0)
          && ((rowNumber * COLUMN_COUNT) + columnNumber) < scriptFileArray_.size();
    }

    public ArrayList<File> getFileArray() {
      return scriptFileArray_;
    }

    @Override
    public int getRowCount() {
      if (scriptFileArray_ != null)
        return (int) Math.ceil(((double) scriptFileArray_.size() / (double) COLUMN_COUNT));
      return 0;
    }

    @Override
    public int getColumnCount() {
      return COLUMN_COUNT;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return "Script-Shortcuts";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (rowIndex >= 0 && (isScriptAvailable(rowIndex, columnIndex))) {
        return scriptFileArray_.get((rowIndex * COLUMN_COUNT) + columnIndex).getName();
      }
      return null;
    }
  }

  public final class SelectionListener implements ListSelectionListener {
    JTable table_;
    int lastRowSelected_ = -1;
    int lastColumnSelected_ = -1;
    boolean multipleStarted_ = false;

    // It is necessary to keep the table since it is not possible
    // to determine the table from the event's source
    SelectionListener(JTable table) {
      this.table_ = table;
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
      // we might get two events when both column and row are changed.  Repsond only to the second
      // one
      if (e.getValueIsAdjusting() && !multipleStarted_) {
        multipleStarted_ = true;
        return;
      }
      multipleStarted_ = false;
      int row = table_.getSelectedRow();
      int column = table_.getSelectedColumn();
      if ((row >= 0) && (model_.isScriptAvailable(row, column))) {
        if (row != lastRowSelected_ || column != lastColumnSelected_) {
          // check for changes and offer to save if needed
          if (!promptToSave(lastRowSelected_)) {
            table_.changeSelection(lastRowSelected_, lastColumnSelected_, false, false);
            return;
          }
          File file = model_.getScript(row, column);
          if (EXT_POS.equals(getExtension(file))) {
            try {
              readFileToTextArea(file, scriptArea_);
              scriptFile_ = file;
              scriptPaneSaved_ = true;
              setTitle(file.getName());
              model_.setLastMod(table_.getSelectedRow(), 0, file.lastModified());
            } catch (IOException | MMScriptException ee) {
              ReportingUtils.logError(ee);
            }
          } else if (EXT_ACQ.equals(getExtension(file))) {
            scriptArea_.setText(
                "mm.loadAcquisition(\"" + file.getAbsolutePath() + "\");\ngui.startAcquisition();");
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

    beanshellREPLint_ = new Interpreter(cons_);

    new Thread(beanshellREPLint_, "BeanShell interpreter").start();
  }

  // Add methods and variables to the interpreter
  private void initializeInterpreter() {
    File tmpFile = null;
    try {
      java.io.InputStream input =
          getClass().getResourceAsStream("/org/micromanager/scriptpanel_startup.bsh");
      if (input != null) {
        tmpFile = File.createTempFile("mm_scriptpanel_startup", ".bsh");
        try (java.io.OutputStream output = new java.io.FileOutputStream(tmpFile)) {
          int read;
          byte[] bytes = new byte[4096];
          while ((read = input.read(bytes)) != -1) {
            output.write(bytes, 0, read);
          }
        }
        tmpFile.deleteOnExit();
      } else {
        ReportingUtils.logError("Failed to find Script Panel Beanshell startup script");
      }
    } catch (IOException e) {
      ReportingUtils.showError("Failed to read Script Panel BeanShell startup script", this);
    }

    if (tmpFile != null) {
      try {
        beanshellREPLint_.source(tmpFile.getAbsolutePath());
      } catch (FileNotFoundException e) {
        ReportingUtils.showError(e, this);
      } catch (IOException | EvalError e) {
        ReportingUtils.showError(e, this);
      }
    }

    // This command allows variables to be inspected in the command-line
    // (e.g., typing "x;" causes the value of x to be returned):
    beanshellREPLint_.setShowResults(true);

    insertScriptingObject("mm", studio_);
    insertScriptingObject("mmc", studio_.core());
  }

  public JConsole getREPLCons() {
    return cons_;
  }

  private void readFileToTextArea(File file, RSyntaxTextArea rsa)
      throws FileNotFoundException, IOException, MMScriptException {
    try (FileReader in = new FileReader(file)) {
      rsa.setRows(1);
      rsa.read(in, null);
      rsa.setCaretPosition(0);
    }
  }

  /**
   * Create the dialog
   *
   * @param studio - MM script-interface implementation
   */
  @SuppressWarnings("LeakingThisInConstructor")
  public ScriptPanel(MMStudio studio) {
    super("script panel");
    studio_ = studio;
    settings_ = studio_.profile().getSettings(ScriptPanel.class);
    final JFrame scriptPanelFrame = this;

    // Beanshell REPL Console
    createBeanshellREPL();

    // Needed when Cancel button is pressed upon save file warning
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent arg0) {
            if (!promptToSave(-1)) return;
            settings_.putInteger(RIGHT_DIVIDER_LOCATION, rightSplitPane_.getDividerLocation());
            settings_.putInteger(DIVIDER_LOCATION, splitPane_.getDividerLocation());
            saveScriptsToPrefs();
            setVisible(false);
          }
        });

    setVisible(false);

    interp_ = new BeanshellEngine(this);
    interp_.setInterpreter(beanshellREPLint_);

    setTitle("Script Panel");
    setIconImage(
        Toolkit.getDefaultToolkit()
            .getImage(MMStudio.class.getResource("/org/micromanager/icons/microscope.gif")));

    int buttonHeight = 15;
    Dimension buttonSize = new Dimension(80, buttonHeight);
    int gap = 5; // determines gap between buttons

    super.setIconImage(
        Toolkit.getDefaultToolkit()
            .getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));
    super.setBounds(100, 100, 550, 495);
    WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);

    final JPanel leftPanel = new JPanel();
    SpringLayout spLeft = new SpringLayout();
    leftPanel.setLayout(spLeft);

    final JPanel topRightPanel = new JPanel();
    SpringLayout spTopRight = new SpringLayout();
    topRightPanel.setLayout(spTopRight);

    final JPanel bottomRightPanel = new JPanel();
    bottomRightPanel.setLayout(new BoxLayout(bottomRightPanel, BoxLayout.Y_AXIS));
    bottomRightPanel.setBackground(Color.white);

    final JButton addButton = new JButton();
    addButton.setFont(new Font("", Font.PLAIN, 10));
    addButton.addActionListener(
        (ActionEvent arg0) -> {
          addScript();
        });
    addButton.setText("Add");
    addButton.setToolTipText("Add shortcut to beanshell script in file system");
    addButton.setPreferredSize(buttonSize);
    spLeft.putConstraint(SpringLayout.NORTH, addButton, gap, SpringLayout.NORTH, leftPanel);
    spLeft.putConstraint(SpringLayout.WEST, addButton, gap, SpringLayout.WEST, leftPanel);
    leftPanel.add(addButton);

    final JButton removeButton = new JButton();
    removeButton.setMargin(new Insets(0, 0, 0, 0));
    removeButton.setFont(new Font("", Font.PLAIN, 10));
    removeButton.addActionListener(
        (ActionEvent arg0) -> {
          removeScript();
        });
    removeButton.setText("Remove");
    removeButton.setToolTipText("Remove currently selected shortcut");
    removeButton.setPreferredSize(buttonSize);
    spLeft.putConstraint(SpringLayout.NORTH, removeButton, gap, SpringLayout.NORTH, leftPanel);
    spLeft.putConstraint(SpringLayout.WEST, removeButton, gap, SpringLayout.EAST, addButton);
    leftPanel.add(removeButton);

    final JButton hotkeyButton = new JButton();
    hotkeyButton.setMargin(new Insets(0, 0, 0, 0));
    hotkeyButton.setFont(new Font("", Font.PLAIN, 10));
    hotkeyButton.addActionListener(
        (ActionEvent arg0) -> {
          HotKeysDialog hk = new HotKeysDialog();
        });
    hotkeyButton.setText("ShortCuts");
    hotkeyButton.setToolTipText(
        TooltipTextMaker.addHTMLBreaksForTooltip(
            "Opens "
                + "shortcuts manager window.  Allows the creation "
                + "of keyboard shortcuts to automatically run scripts"));
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

    scriptArea_ = new RSyntaxTextArea(1, 20);
    scriptArea_.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
    scriptArea_.setCodeFoldingEnabled(true);
    scriptArea_.setAutoIndentEnabled(true);

    scriptArea_.getDocument().putProperty(PlainDocument.tabSizeAttribute, 3);
    scriptArea_.setBackground(Color.WHITE);
    scriptArea_.getDocument().addDocumentListener(new MyDocumentListener());
    scriptArea_.setMinimumSize(new Dimension(300, 300));
    scriptArea_.setMaximumSize(new Dimension(800, 800));
    scriptArea_.setPreferredSize(new Dimension(800, 300));
    scriptPaneSaved_ = true;
    scriptArea_.setFocusTraversalKeysEnabled(false);

    sp = new RTextScrollPane(scriptArea_);
    sp.setFocusTraversalKeysEnabled(false);
    sp.setLineNumbersEnabled(true);

    spTopRight.putConstraint(SpringLayout.EAST, sp, 0, SpringLayout.EAST, topRightPanel);
    spTopRight.putConstraint(
        SpringLayout.SOUTH, sp, -(buttonHeight + 2 * gap), SpringLayout.SOUTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, sp, 0, SpringLayout.WEST, topRightPanel);
    spTopRight.putConstraint(
        SpringLayout.NORTH, sp, buttonHeight + 2 * gap, SpringLayout.NORTH, topRightPanel);
    topRightPanel.add(sp);

    bottomRightPanel.add(cons_);

    // Immediate Pane (executes single lines of script)
    immediatePane_ = new JTextField();
    immediatePane_.setFont(new Font("Courier New", Font.PLAIN, 12));
    immediatePane_.setBackground(Color.WHITE);

    // 'Consume' the enter key if it was pressed in this pane
    immediatePane_.addActionListener(new immediatePaneListener());
    // Implement History with up and down keys
    immediatePane_.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_KP_UP || e.getKeyCode() == KeyEvent.VK_UP) {
              doImmediatePaneHistoryUp();
            } else if (e.getKeyCode() == KeyEvent.VK_KP_DOWN
                || e.getKeyCode() == KeyEvent.VK_DOWN) {
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
    Style blackStyle = messagePane_.getLogicalStyle();
    blackStyle = sc_.addStyle(BLACK_STYLE_NAME, blackStyle);
    StyleConstants.setForeground(blackStyle, Color.black);
    Style redStyle = sc_.addStyle(RED_STYLE_NAME, null);
    StyleConstants.setForeground(redStyle, Color.red);

    // disable user input to the messagePane
    messagePane_.setKeymap(null);

    // ----- Pane with script buttons -------//

    scriptTable_ = new DaytimeNighttime.Table();
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
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
    scrollPane.setViewportView(scriptTable_);
    // catch double clicks
    scriptTable_.addMouseListener(this);

    // -------- top row of buttons -------- //

    runButton_ = new JButton();
    topRightPanel.add(runButton_);
    runButton_.setFont(new Font("", Font.PLAIN, 10));
    runButton_.addActionListener(
        (ActionEvent arg0) -> {
          runPane();
        });
    runButton_.setText("Run");
    runButton_.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.NORTH, runButton_, gap, SpringLayout.NORTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, runButton_, gap, SpringLayout.WEST, topRightPanel);

    stopButton_ = new JButton();
    topRightPanel.add(stopButton_);
    stopButton_.setFont(new Font("", Font.PLAIN, 10));
    stopButton_.addActionListener(
        (ActionEvent arg0) -> {
          stopScript("Interrupt".equals(stopButton_.getText()));
        });
    stopButton_.setText("Interrupt");
    stopButton_.setEnabled(false);
    stopButton_.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.NORTH, stopButton_, gap, SpringLayout.NORTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, stopButton_, gap, SpringLayout.EAST, runButton_);

    final JButton newButton = new JButton();
    topRightPanel.add(newButton);
    newButton.setFont(new Font("", Font.PLAIN, 10));
    newButton.addActionListener(
        (ActionEvent arg0) -> {
          newPane();
        });
    newButton.setText("New");
    newButton.setPreferredSize(buttonSize);
    spTopRight.putConstraint(SpringLayout.NORTH, newButton, gap, SpringLayout.NORTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, newButton, gap, SpringLayout.EAST, stopButton_);

    final JButton openButton = new JButton();
    topRightPanel.add(openButton);
    openButton.setFont(new Font("", Font.PLAIN, 10));
    openButton.addActionListener(
        (ActionEvent arg0) -> {
          openScriptInPane();
        });
    openButton.setText("Open");
    openButton.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.NORTH, openButton, gap, SpringLayout.NORTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, openButton, gap, SpringLayout.EAST, newButton);

    final JButton saveButton = new JButton();
    topRightPanel.add(saveButton);
    saveButton.setFont(new Font("", Font.PLAIN, 10));
    saveButton.addActionListener(
        (ActionEvent arg0) -> {
          saveScript(-1);
        });
    saveButton.setText("Save");
    saveButton.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.NORTH, saveButton, gap, SpringLayout.NORTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, saveButton, gap, SpringLayout.EAST, openButton);

    final JButton saveAsButton = new JButton();
    saveAsButton.setMargin(new Insets(0, 0, 0, 0));
    topRightPanel.add(saveAsButton);
    saveAsButton.setFont(new Font("", Font.PLAIN, 10));
    saveAsButton.addActionListener(
        (ActionEvent arg0) -> {
          saveScriptAs();
        });
    saveAsButton.setText("Save As");
    saveAsButton.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.NORTH, saveAsButton, gap, SpringLayout.NORTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, saveAsButton, gap, SpringLayout.EAST, saveButton);

    final JButton helpButton = new JButton();
    helpButton.setMargin(new Insets(0, 0, 0, 0));
    topRightPanel.add(helpButton);
    helpButton.setFont(new Font("", Font.PLAIN, 10));
    helpButton.addActionListener(
        (ActionEvent e) -> {
          try {
            ij.plugin.BrowserLauncher.openURL(
                "https://micro-manager.org/wiki/Version_2.0_Users_Guide#Script_Panel");
          } catch (IOException e1) {
            ReportingUtils.showError(e1, scriptPanelFrame);
          }
        });
    helpButton.setText("Help");
    helpButton.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.NORTH, helpButton, gap, SpringLayout.NORTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, helpButton, gap, SpringLayout.EAST, saveAsButton);

    JLabel fLabel = new JLabel("Find:");
    topRightPanel.add(fLabel);
    fLabel.setFont(new Font("", Font.PLAIN, 10));
    spTopRight.putConstraint(SpringLayout.SOUTH, fLabel, -gap, SpringLayout.SOUTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, fLabel, gap, SpringLayout.WEST, topRightPanel);

    // ---------- Find area --------- //

    // Find text field
    final JTextField findTextField = new JTextField(20);
    topRightPanel.add(findTextField);
    findTextField.setFont(new Font("", Font.PLAIN, 10));
    spTopRight.putConstraint(
        SpringLayout.SOUTH, findTextField, 0, SpringLayout.SOUTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, findTextField, gap, SpringLayout.EAST, fLabel);

    final SearchContext context = new SearchContext();

    // find next Button
    final JButton findButton = new JButton();
    topRightPanel.add(findButton);
    findButton.setFont(new Font("", Font.PLAIN, 10));
    findButton.addActionListener(
        (ActionEvent arg0) -> {
          find(context, findTextField.getText(), false);
        });
    findButton.setText("Find Next");
    findButton.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.SOUTH, findButton, -gap, SpringLayout.SOUTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, findButton, gap, SpringLayout.EAST, findTextField);

    // find previous Button
    final JButton findRevButton = new JButton();
    topRightPanel.add(findRevButton);
    findRevButton.setFont(new Font("", Font.PLAIN, 10));
    findRevButton.addActionListener(
        (ActionEvent arg0) -> {
          find(context, findTextField.getText(), true);
        });
    findRevButton.setText("Find Previous");
    findRevButton.setPreferredSize(buttonSize);
    spTopRight.putConstraint(
        SpringLayout.SOUTH, findRevButton, -gap, SpringLayout.SOUTH, topRightPanel);
    spTopRight.putConstraint(SpringLayout.WEST, findRevButton, gap, SpringLayout.EAST, findButton);

    // Set up basic structure
    leftPanel.setMinimumSize(new Dimension(180, 130));
    rightSplitPane_ = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topRightPanel, bottomRightPanel);
    rightSplitPane_.setOneTouchExpandable(true);
    int rightDividerLocation = settings_.getInteger(RIGHT_DIVIDER_LOCATION, 200);
    rightSplitPane_.setDividerLocation(rightDividerLocation);
    splitPane_ = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplitPane_);
    splitPane_.setOneTouchExpandable(true);
    int dividerLocation = settings_.getInteger(DIVIDER_LOCATION, 200);
    splitPane_.setDividerLocation(dividerLocation);
    splitPane_.setMinimumSize(new Dimension(180, 130));
    rightSplitPane_.setResizeWeight(1.0);
    splitPane_.setResizeWeight(0.0);

    getContentPane().add(splitPane_);

    // Load the shortcut table based on saved preferences
    getScriptsFromPrefs();

    resetInterpreter();
  }

  protected void stopScript(boolean shouldInterrupt) {
    interp_.stopRequest(shouldInterrupt);
    stopButton_.setText("Terminate");
  }

  protected class MyDocumentListener implements DocumentListener {
    @Override
    public void insertUpdate(DocumentEvent e) {
      scriptPaneSaved_ = false;
      try {
        // Strange, but it seems to be our responsibility to keep the
        // number of lines in the scriptArea_ current
        scriptArea_.setRows(getNumLines(e.getDocument().getText(0, e.getDocument().getLength())));
        sp.revalidate();
      } catch (BadLocationException ble) {
        // TODO
      }
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
      scriptPaneSaved_ = false;
      try {
        scriptArea_.setRows(getNumLines(e.getDocument().getText(0, e.getDocument().getLength())));
        sp.revalidate();
      } catch (BadLocationException ble) {
        // TODO
      }
    }

    /**
     * This expensive method counts the number of lines in the script, so we can determine the
     * number of rows to allocate to scriptArea_. TODO: find a better method.
     */
    private int getNumLines(String text) {
      return text.length() - text.replace("\n", "").length() + 1;
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
      scriptPaneSaved_ = false;
    }
  }

  /**
   * Executes search in script currently shown in scriptArea_
   *
   * @param context - SearchContext instance, passed into function so that we only need a single
   *     instance
   * @param text - Search string
   * @param reverse - Search backward when true
   */
  private void find(SearchContext context, String text, boolean reverse) {
    if (text.length() == 0) {
      return;
    }
    context.setSearchFor(text);
    context.setMatchCase(false);
    context.setRegularExpression(false);
    context.setSearchForward(!reverse);
    context.setWholeWord(false);

    SearchResult found = SearchEngine.find(scriptArea_, context);
    if (!found.wasFound()) {
      studio_.logs().showMessage("\"" + text + "\" was not found", this);
    }
  }

  /**
   * Prompt and save file if contents were modified
   *
   * @param row - row number of script in question
   * @return - true if the file was saved
   */
  public boolean promptToSave(int row) {
    if (scriptPaneSaved_) return true;
    String message;
    if (scriptFile_ != null) message = "Save changes to " + scriptFile_.getName() + "?";
    else message = "Save script?";
    int result =
        JOptionPane.showConfirmDialog(
            this, message, APP_NAME, JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
    switch (result) {
      case JOptionPane.NO_OPTION:
        // avoid prompting again:
        scriptPaneSaved_ = true;
        break;
      default:
        saveScript(row);
        break;
    }

    return true;
  }

  /** Lets the user select a script file to add to the shortcut table */
  private void addScript() {
    if (scriptFile_ != null && !model_.HasScriptAlready(scriptFile_)) {
      addScriptToModel(scriptFile_);
    } else if (scriptFile_ == null && !scriptPaneSaved_) {
      if (!promptToSave(-1)) return;
      addScriptToModel(scriptFile_);
    } else {
      // check for changes and offer to save if needed
      if (!promptToSave(-1)) return;

      File curFile = FileDialogs.openFile(this, "Select a Beanshell script", BSH_FILE);

      if (curFile != null) {
        studio_
            .profile()
            .getSettings(ScriptPanel.class)
            .putString(SCRIPT_FILE, curFile.getAbsolutePath());
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

  /** Removes the selected script from the shortcut table */
  private void removeScript() {
    if (!promptToSave(-1)) return;

    model_.RemoveScript(scriptTable_.getSelectedRow(), scriptTable_.getSelectedColumn());
    model_.fireTableDataChanged();
    scriptArea_.setText("");
    scriptPaneSaved_ = true;
    this.setTitle("");
    scriptFile_ = null;
  }

  /**
   * Saves the script in the editor Pane
   *
   * @param row - row of script in editor pane (or <0 for current)
   */
  private void saveScript(int row) {
    if (scriptFile_ == null) {
      saveScriptAs();
      return;
    }
    if (scriptFile_ != null && (scriptTable_.getSelectedRow() > -1)) {
      if (row < 0) row = scriptTable_.getSelectedRow();
      boolean modified = (scriptFile_.lastModified() != model_.getLastMod(row, 0));
      if (modified) {
        int result =
            JOptionPane.showConfirmDialog(
                this,
                "Script was changed on disk.  Continue saving anyways?",
                APP_NAME,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        switch (result) {
          case JOptionPane.YES_OPTION:
            break;
          default:
            return;
        }
      }
    }
    try {
      try (FileWriter fw = new FileWriter(scriptFile_)) {
        fw.write(scriptArea_.getText());
      }
      scriptPaneSaved_ = true;
      int[] cellAddress = new int[2];
      model_.GetCell(scriptFile_, cellAddress);
      model_.setLastMod(cellAddress[0], 0, scriptFile_.lastModified());
      showMessage("Saved file: " + scriptFile_.getName());
    } catch (IOException ioe) {
      ReportingUtils.showError(ioe, this);
    }
  }

  /** Saves script in the editor Pane. Always prompts for a new name */
  private void saveScriptAs() {
    File saveFile = FileDialogs.save(this, "Save beanshell script", BSH_FILE);
    if (saveFile != null) {
      try {
        // Add .bsh extension if file did not have an extension itself
        String fileName = saveFile.getName();
        if (fileName.length() < 5
            || (fileName.charAt(fileName.length() - 4) != '.'
                && fileName.charAt(fileName.length() - 5) != '.')) fileName += ".bsh";
        saveFile = new File(saveFile.getParentFile(), fileName);

        try (FileWriter fw = new FileWriter(saveFile)) {
          fw.write(scriptArea_.getText());
        }
        scriptFile_ = saveFile;
        settings_.putString(SCRIPT_FILE, saveFile.getAbsolutePath());
        scriptPaneSaved_ = true;
        this.setTitle(saveFile.getName());
      } catch (IOException ioe) {
        ReportingUtils.showError(ioe, this);
      }
    }
  }

  /** Runs the content of the editor Pane in the REPL context. */
  @SuppressWarnings("unused")
  private void injectPane() {
    interp_.setInterpreter(beanshellREPLint_);
    runPane();
    interp_.resetInterpreter();
  }

  /** Runs the content of the editor Pane */
  private void runPane() {
    File curFile =
        model_.getScript(scriptTable_.getSelectedRow(), scriptTable_.getSelectedColumn());
    // check if file on disk was modified.
    if (curFile != null) {
      boolean modified =
          (curFile.lastModified() != model_.getLastMod(scriptTable_.getSelectedRow(), 0));
      if (modified) {
        int result =
            JOptionPane.showConfirmDialog(
                this,
                "Script was changed on disk.  Re-load from disk?",
                APP_NAME,
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        switch (result) {
          case JOptionPane.YES_OPTION:
            try {
              readFileToTextArea(curFile, scriptArea_);
              scriptFile_ = curFile;
              scriptPaneSaved_ = true;
              model_.setLastMod(scriptTable_.getSelectedRow(), 0, curFile.lastModified());
            } catch (IOException | MMScriptException e) {
              handleException(e);
            }
            break;
          case JOptionPane.NO_OPTION:
            break;
          default:
            return;
        }
      }
    }
    try {
      runButton_.setEnabled(false);
      stopButton_.setText("Interrupt");
      stopButton_.setEnabled(true);

      interp_.evaluateAsync(scriptArea_.getText());

      // Spawn a thread that waits for the execution thread to exit and then
      // updates the buttons as appropriate.
      Thread sentinel =
          new Thread(
              () -> {
                try {
                  interp_.joinEvalThread();
                } catch (InterruptedException e) {
                } // Assume thread is done.
                runButton_.setEnabled(true);
                stopButton_.setEnabled(false);
              });
      sentinel.start();
    } catch (MMScriptException e) {
      ReportingUtils.logError(e);
      messageException(e.getMessage(), -1);
    }
  }

  /**
   * Runs the content of the provided file
   *
   * @param curFile - script file to be run
   */
  @Override
  public void runFile(File curFile) {
    // check if file on disk was modified.
    if (curFile != null) {
      try {
        interp_.evaluateAsync(getContents(curFile));
      } catch (MMScriptException e) {
        ReportingUtils.logError(e);
      }
    }
  }

  public static String getContents(File aFile) {
    StringBuilder contents = new StringBuilder();

    try {
      // use buffering, reading one line at a time
      // FileReader always assumes default encoding is OK!
      try (BufferedReader input = new BufferedReader(new FileReader(aFile))) {
        /*
         * readLine is a bit quirky :
         * it returns the content of a line MINUS the newline.
         * it returns null only for the END of the stream.
         * it returns an empty String if two newlines appear in a row.
         */
        String line;
        while ((line = input.readLine()) != null) {
          contents.append(line);
          contents.append(System.getProperty("line.separator"));
        }
      }
    } catch (IOException ex) {
      ReportingUtils.logError(ex);
    }

    return contents.toString();
  }

  /**
   * Empties the editor Pane and deselects the shortcuts, in effect creating a 'blank' editor pane
   */
  private void newPane() {
    // check for changes and offer to save if needed
    if (!promptToSave(-1)) return;

    int row = scriptTable_.getSelectedRow();
    int column = scriptTable_.getSelectedColumn();
    scriptTable_.changeSelection(row, column, true, false);
    scriptArea_.setText("");
    scriptPaneSaved_ = true;
    scriptFile_ = null;
    this.setTitle("");
    scriptArea_.requestFocusInWindow();
  }

  /** Opens a script in the editor Pane */
  private void openScriptInPane() {
    // check for changes and offer to save if needed
    if (!promptToSave(-1)) return;

    File curFile = FileDialogs.openFile(this, "Choose Beanshell script", BSH_FILE);

    if (curFile != null) {
      try {
        settings_.putString(SCRIPT_FILE, curFile.getAbsolutePath());
        int row = scriptTable_.getSelectedRow();
        int column = scriptTable_.getSelectedColumn();
        scriptTable_.changeSelection(row, column, true, false);
        readFileToTextArea(curFile, scriptArea_);
        scriptFile_ = curFile;
        scriptPaneSaved_ = true;
        this.setTitle(curFile.getName());
      } catch (IOException | MMScriptException e) {
        handleException(e);
      } finally {

      }
    }
  }

  public void insertScriptingObject(String varName, Object obj) {
    try {
      interp_.insertGlobalObject(varName, obj);
      beanshellREPLint_.set(varName, obj);
    } catch (EvalError | MMScriptException e) {
      handleException(e);
    }
  }

  private void runImmediatePane() {
    try {
      immediatePaneHistory_.add(immediatePane_.getText());
      immediatePaneHistoryIndex_ = immediatePaneHistory_.size();
      interp_.evaluateAsync(immediatePane_.getText());
      immediatePane_.setText("");
    } catch (MMScriptException e) {
      messageException(e.getMessage(), -1);
    }
  }

  private void doImmediatePaneHistoryUp() {
    if (immediatePaneHistoryIndex_ > 0) immediatePaneHistoryIndex_--;
    if (immediatePaneHistoryIndex_ >= 0
        && immediatePaneHistoryIndex_ < immediatePaneHistory_.size())
      immediatePane_.setText(immediatePaneHistory_.get(immediatePaneHistoryIndex_));
  }

  private void doImmediatePaneHistoryDown() {
    if (immediatePaneHistoryIndex_ < immediatePaneHistory_.size()) immediatePaneHistoryIndex_++;
    if (immediatePaneHistoryIndex_ < immediatePaneHistory_.size())
      immediatePane_.setText(immediatePaneHistory_.get(immediatePaneHistoryIndex_));
    else immediatePane_.setText("");
  }

  public void closePanel() {
    if (!promptToSave(-1)) return;
    saveScriptsToPrefs();
    dispose();
  }

  /**
   * Displays exception message in a generic dialog box
   *
   * @param e
   */
  public void handleException(Exception e) {
    ReportingUtils.showError(e, this);
  }

  /**
   * Displays text string in message window in color red
   *
   * @param text - text to be displayed
   * @param lineNumber - line to be highlighted in red
   */
  public void messageException(String text, int lineNumber) {
    // move cursor to the error line number
    if (lineNumber >= 0) {
      scriptArea_.setActiveLineRange(lineNumber - 1, lineNumber);
      try {
        scriptArea_.select(
            scriptArea_.getLineStartOffset(lineNumber - 1),
            scriptArea_.getLineEndOffset(lineNumber - 1));
      } catch (BadLocationException ex) {
        ReportingUtils.logError(ex, "Error in Scriptpanel member function messageException");
      }
    }
    messagePane_.setCharacterAttributes(sc_.getStyle(RED_STYLE_NAME), false);
    messagePane_.replaceSelection(text + "\n");
    cons_.print("\n" + text, java.awt.Color.red);
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
    cons_.print("\n" + promptStr, studio_.app().skin().getEnabledTextColor());
  }

  /** Clears the content of the message window */
  public void clearOutput() {
    boolean originalAccessibility = true;
    try {
      beanshellREPLint_.eval("bsh.console.text");
    } catch (EvalError e) {
      originalAccessibility = false;
      try {
        beanshellREPLint_.eval("setAccessibility(true);");
      } catch (EvalError e1) {
        ReportingUtils.showError(e1, this);
      }
    }
    try {
      beanshellREPLint_.eval(
          "bsh.console.text.setText(\"\");" + "setAccessibility(" + originalAccessibility + ");");
    } catch (EvalError e) {
      ReportingUtils.showError(e, this);
    }
  }

  public void getScriptsFromPrefs() {
    // restore previously listed scripts from profile
    int j = 0;
    String script;
    boolean isFile = false;
    model_.RemoveAllScripts();
    do {
      script = settings_.getString(SCRIPT_FILE + j, null);
      if ((script != null) && (!script.equals(""))) {
        File file = new File(script);
        if (file.isFile()) {
          model_.AddScript(file);
          isFile = true;
        }
      }
      j++;
    } while ((script != null) && (!script.equals("")) && isFile);
  }

  public static ArrayList<File> getScriptList() {
    return model_.getFileArray();
  }

  public void saveScriptsToPrefs() {
    // first clear existing entries
    int i = 0;
    while (settings_.containsString(SCRIPT_FILE + i)) {
      settings_.remove(SCRIPT_FILE + i);
      i++;
    }
    File file;
    ArrayList<File> scriptFileArray = model_.getFileArray();
    for (i = 0; i < scriptFileArray.size(); i++) {
      file = scriptFileArray.get(i);
      if (file != null) {
        settings_.putString(SCRIPT_FILE + i, file.getAbsolutePath());
      }
    }

    // Add one empty script, so as not to read in stale variables
    settings_.putString(SCRIPT_FILE + scriptFileArray.size(), "");
  }

  private void finishUp() {
    if (!promptToSave(-1)) {
      return;
    }
    settings_.putInteger(RIGHT_DIVIDER_LOCATION, rightSplitPane_.getDividerLocation());
    settings_.putInteger(DIVIDER_LOCATION, splitPane_.getDividerLocation());
    saveScriptsToPrefs();
    setVisible(false);
  }

  @Override
  public void dispose() {
    finishUp();
    super.dispose();
  }

  private String getExtension(File f) {
    String ext = null;
    String s = f.getName();
    int i = s.lastIndexOf('.');

    if (i > 0 && i < s.length() - 1) {
      ext = s.substring(i + 1).toLowerCase();
    }
    return ext;
  }

  /**
   * MouseListener implementation
   *
   * @param e MouseEvent to listen to
   */
  @Override
  public void mouseClicked(MouseEvent e) {
    if (e.getClickCount() >= 2) runPane();
  }

  @Override
  public void mousePressed(MouseEvent e) {}

  @Override
  public void mouseReleased(MouseEvent e) {}

  @Override
  public void mouseEntered(MouseEvent e) {}

  @Override
  public void mouseExited(MouseEvent e) {}

  class immediatePaneListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent evt) {
      runImmediatePane();
    }
  }

  /** Displays a message coming from a separate thread. */
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

    @Override
    public void run() {
      if (error_) messageException(msg_, lineNumber_);
      else message(msg_);
    }
  }

  public void displayMessage(String message) {
    SwingUtilities.invokeLater(new ExecuteDisplayMessage(message));
    studio_.logs().logMessage(message);
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

  public static String getStartupScript(Studio studio) {
    return studio
        .profile()
        .getSettings(ScriptPanel.class)
        .getString(STARTUP_SCRIPT, "MMStartup.bsh");
  }

  public static void setStartupScript(Studio studio, String path) {
    studio.profile().getSettings(ScriptPanel.class).putString(STARTUP_SCRIPT, path);
  }

  private void showMessage(String text) {
    messagePane_.setCharacterAttributes(sc_.getStyle(BLACK_STYLE_NAME), false);
    messagePane_.replaceSelection(text + "\n");
    cons_.print("\n" + text, studio_.app().skin().getEnabledTextColor());
    showPrompt();
  }

  @Override
  public void message(final String text) throws ScriptController.ScriptStoppedException {
    if (stopRequestPending()) {
      throw new ScriptController.ScriptStoppedException("Script interrupted by the user!");
    }

    SwingUtilities.invokeLater(
        () -> {
          showMessage(text);
        });
  }

  @Override
  public void clearMessageWindow() throws ScriptController.ScriptStoppedException {
    if (stopRequestPending()) {
      throw new ScriptController.ScriptStoppedException("Script interrupted by the user!");
    }
    clearOutput();
  }

  @Override
  public void resetInterpreter() throws ScriptController.ScriptStoppedException {
    if (stopRequestPending()) {
      throw new ScriptController.ScriptStoppedException("Script interrupted by the user!");
    }
    try {
      beanshellREPLint_.eval("clear();");
    } catch (EvalError e) {
      message("Reset of BeanShell interpreter failed");
    }
    try {
      // Apparently clear() also erases bsh.console, which we need
      beanshellREPLint_.setConsole(cons_);
      // this call appears to fail on linux so catch the error that was reported:
    } catch (bsh.InterpreterError bi) {
      ReportingUtils.logError(
          "Called to Beanshell setConsole failed. Probably inocuous." + bi.getMessage());
    }

    initializeInterpreter();
  }
}
