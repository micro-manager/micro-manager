package org.micromanager.asidispim.table;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;

import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.AcquisitionPanel;
import org.micromanager.asidispim.Data.AcquisitionSettings;
import org.micromanager.asidispim.Data.Icons;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.MyDialogUtils;
import org.micromanager.asidispim.Utils.MyFileUtils;
import org.micromanager.utils.MMFrame;
import org.micromanager.utils.ReportingUtils;

import com.google.gson.JsonSyntaxException;

import net.miginfocom.swing.MigLayout;

// TODO: display acq start time, currently elapsed time, and estimated time
// TODO: disable controls that could mess up an acquisition

/**
 * This frame contains controls for the Acquistiion Table.
 */
@SuppressWarnings("serial")
public class AcquisitionTableFrame extends JFrame {

    private final ScriptInterface gui_;

    private JButton btnLoadPlaylist_;
    private JButton btnSavePlaylist_;
    private JButton btnEditPositionList_;
    private JButton btnAddPositionList_;
    private JButton btnRemovePositionList_;
    private JButton btnRenamePositionList_;
    private JButton btnAddAcq_;
    private JButton btnRemoveAcq_;
    private JButton btnSaveAcq_;
    private JButton btnOpenGridFrame_;
    
    // acquisition status
    private JTextArea txtAcqStatusLog_;
    private JScrollPane txtScrollBar_;
    private JLabel lblCurrentAcqName_;
    private JLabel lblAcqProgress_;
    
    // playlist metadata save location
    private JLabel lblSaveDirectory_;
    private JTextField txtSaveDirectory_;
    private JButton btnBrowse_;
    private String saveDirectory_;
    
    private JToggleButton btnRunAcquisitions_;
    private AtomicBoolean isRunning_;
    
    private MMFrame gridFrame_;
    private AcquisitionTable acqTable_;
    private AcquisitionTablePositionList lstPositions_;
    
    private final Prefs prefs_;
    private final AcquisitionPanel acqPanel_;
    private final JFileChooser fileBrowser_;
    private final JFileChooser directoryBrowser_;
    
    public AcquisitionTableFrame(final ScriptInterface gui, final Prefs prefs, final AcquisitionPanel acqPanel) {
        setTitle("Acquisition Playlist");
        setIconImage(Icons.MICROSCOPE.getImage());
        setLocation(200, 200);
        setSize(800, 700);
        setMinimumSize(new Dimension(800, 700));
        setLayout(new MigLayout("", "", ""));
        
        // TODO: improve ui and remove this
        setResizable(false);
        
        gui_ = gui;
        acqPanel_ = acqPanel;
        prefs_ = prefs;
        
        // empty string used to prompt user to set directory
        saveDirectory_ = "";
        
        // used to cancel run acquisitions
        isRunning_ = new AtomicBoolean(false);
        
        // used to save and load settings
        fileBrowser_ = new JFileChooser();
        fileBrowser_.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileBrowser_.setFileFilter(new FileNameExtensionFilter("JSON Files (.txt, .text, .json)", "txt", "text", "json"));
        
        directoryBrowser_ = new JFileChooser();
        directoryBrowser_.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        createUserInterface();
        createEventHandlers();
        initWindowListener();
    }
    
    /**
     * Create the user interface elements.
     */
    private void createUserInterface() {
        // section titles
        final Font font = new Font(Font.MONOSPACED, Font.BOLD, 18);
        final JLabel lblAcqTable = new JLabel("Acquisition Table");
        final JLabel lblPositionLists = new JLabel("Position Lists");
        final JLabel lblAcqStatus = new JLabel("Acquisition Status");
        lblAcqTable.setFont(font);
        lblPositionLists.setFont(font);
        lblAcqStatus.setFont(font);
        
        // subpanel for the JList and related elements
        final JPanel listPanel = new JPanel();
        listPanel.setLayout(new MigLayout("", "", ""));
    
        // subpanel to report acquisition status
        final JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new MigLayout("", "", ""));
  
        final JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new MigLayout("", "[]10[]", ""));
        
        // the acquisition table and position list selector
        acqTable_ = new AcquisitionTable(gui_, acqPanel_);
        lstPositions_ = new AcquisitionTablePositionList(acqTable_);
        lstPositions_.setMinimumSize(new Dimension(200, 200));

        // give acquisition table a reference to the position list
        acqTable_.setPositionListControls(lstPositions_);
        
        // buttons to add and remove acquistions
        btnAddAcq_ = new JButton("Add");
        btnRemoveAcq_ = new JButton("Remove");
        btnSaveAcq_ = new JButton("Save");
        
        // buttons to operate on PositionLists
        btnAddPositionList_ = new JButton("Add");
        btnRemovePositionList_ = new JButton("Remove");
        btnRenamePositionList_ = new JButton("Rename");
        btnEditPositionList_ = new JButton("Edit Position List...");
        btnOpenGridFrame_ = new JButton("XYZ grid...");
        
        // buttons to load and save data
        btnLoadPlaylist_ = new JButton("Load Playlist");
        btnSavePlaylist_ = new JButton("Save Playlist");
        
        // expand the acquisition table to the screen resolution when the window is maximized
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        acqTable_.setPreferredSize(new Dimension(screenSize.width, 300));

        // report acquisition status
        lblCurrentAcqName_ = new JLabel("Current Acquisition: " + acqTable_.getCurrentAcqName());
        lblAcqProgress_ = new JLabel("Acquisition Status: Not Running");
        
        // display acquisition errors
        txtAcqStatusLog_ = new JTextArea();
        txtAcqStatusLog_.setEditable(false);
//        txtErrorLog.setLineWrap(true);
//        txtErrorLog.setWrapStyleWord(true);
        txtScrollBar_ = new JScrollPane(txtAcqStatusLog_);
        txtScrollBar_.setPreferredSize(new Dimension(600, 200));
        
        // set the component to display error messages
        MyDialogUtils.setErrorLog(txtAcqStatusLog_);
       
        // scroll to the bottom of the error log when appending text
        final DefaultCaret caret = (DefaultCaret)txtAcqStatusLog_.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        
        btnRunAcquisitions_ = new JToggleButton("Run Acquisition Playlist", Icons.ARROW_RIGHT, false);
        
        // where to save logs
        txtSaveDirectory_ = new JTextField();
        txtSaveDirectory_.setPreferredSize(new Dimension(200, 20));
        txtSaveDirectory_.setEditable(false);
        
        // load from prefs
        final String saveDirectoryRoot = prefs_.getString("Acquisition Playlist", "Playlist Save Directory", "");
        txtSaveDirectory_.setText(saveDirectoryRoot);
        saveDirectory_ = saveDirectoryRoot;
        
        lblSaveDirectory_ = new JLabel("Playlist Save Directory:");
        lblSaveDirectory_.setToolTipText("The save directory for playlist settings and the acquisition status log.");
        btnBrowse_ = new JButton("Browse");
        
        // position list subpanel
        listPanel.add(lblPositionLists, "span 2, wrap");
        listPanel.add(lstPositions_, "span 2, wrap");
        listPanel.add(btnAddPositionList_, "");
        listPanel.add(btnRemovePositionList_, "split 2");
        listPanel.add(btnRenamePositionList_, "wrap");
        listPanel.add(btnEditPositionList_, "split 2, span 2");
        listPanel.add(btnOpenGridFrame_, "");
        
        // acquisition status subpanel
        statusPanel.add(lblAcqStatus, "wrap");
        statusPanel.add(txtScrollBar_, "wrap");
        statusPanel.add(lblCurrentAcqName_, "wrap");
        statusPanel.add(lblAcqProgress_, "wrap");
        statusPanel.add(btnRunAcquisitions_, "");
        
        // save & load subpanel
        bottomPanel.add(lblSaveDirectory_, "");
        bottomPanel.add(txtSaveDirectory_, "");
        bottomPanel.add(btnBrowse_, "");
        bottomPanel.add(btnSavePlaylist_, "gapleft 150");
        bottomPanel.add(btnLoadPlaylist_, "");
        
        // add ui elements to frame
        add(lblAcqTable, "split 2");
        add(btnSaveAcq_, "gapleft 530, wrap");
        add(acqTable_, "span 2, wrap");
        add(btnAddAcq_, "split 3");
        add(btnRemoveAcq_, "wrap");
        add(listPanel, "split 2");
        add(statusPanel, "wrap");
        add(bottomPanel, "");
    }
    
	/**
	 * Liaten to window events and unselect acquisitions 
	 * and position lists if the window closes.
	 */
	private void initWindowListener() {
		addWindowListener(new WindowAdapter() {
	        @Override
	        public void windowClosing(WindowEvent event) {
	        	lstPositions_.clearSelectionAndReset();
	        	acqTable_.clearSelection();
	        }
		});
	}
    /**
     * Connect methods to the user interface.
     */
    private void createEventHandlers() {
        // add an acquisition to the table
        btnAddAcq_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addAcquisiton();
            }
        });
        
        // remove the selected acquisition from the table
        btnRemoveAcq_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acqTable_.removeSelectedAcquisition();
            }
        });
        
        // save current position list and acquisition settings
        btnSaveAcq_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            	saveSettings();
            }
        });
        
        // add a new position list to the JList and HashMap
        btnAddPositionList_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addPositionList();
            }
        });
        
        // remove a position list from the JList and HashMap
        btnRemovePositionList_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedPositionList();
            }
        });
        
        // rename the selected position list
        btnRenamePositionList_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                renameSelectedPositionList();
            }
        });
        
        // save acquisition settings to a directory
        btnSavePlaylist_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveAcquisitionTable();
            }
        });
        
        // open the acquisition settings from a directory
        btnLoadPlaylist_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadAcquisitionTable();
            }
        });
        
        // run all acquisitions sequentially
        btnRunAcquisitions_.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				final boolean selected = btnRunAcquisitions_.isSelected();
				if (selected) {
					btnRunAcquisitions_.setText("Stop Acquisition Playlist");
					btnRunAcquisitions_.setIcon(Icons.CANCEL);
					runSequentialAcquisitions();
				} else {
					btnRunAcquisitions_.setText("Run Acquisition Playlist");
					btnRunAcquisitions_.setIcon(Icons.ARROW_RIGHT);
					isRunning_.set(false); // stop acquisition
				}
			}
        });
        
        // open the position list editor window
        btnEditPositionList_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gui_.showXYPositionList();
            }
        });
        
        // open the XYZ grid window
        btnOpenGridFrame_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gridFrame_.setVisible(true);
            }
        });
        
        // browse to the playlist save directory
        btnBrowse_.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setSaveDirectory();
			}
        });
    }
    
    /**
     * Set the XYZ grid frame so we can open it from the playlist.
     * 
     * @param frame the XYZ grid frame
     */
    public void setXYZGridFrame(final MMFrame frame) {
        gridFrame_ = frame;
    }
    
    /**
     * Open a file browser dialog to set the save directory for playlist metadata.
     */
    public void setSaveDirectory() {
		if (directoryBrowser_.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			final String filePath = directoryBrowser_.getSelectedFile().toString();
			prefs_.putString("Acquisition Playlist", "Playlist Save Directory", filePath);
			txtSaveDirectory_.setText(filePath);
			saveDirectory_ = filePath;
			//System.out.println(saveDirectory_);
		}
    }
    
    /**
     * Saves the acquisition table, position lists, and their metadata.
     */
    public void saveAcquisitionTable() {
        // open a file browser
        fileBrowser_.setDialogTitle("Save acquisition settings to the directory...");
        if (fileBrowser_.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            final String filePath = fileBrowser_.getSelectedFile().toString();
            // check if the file exists and prompt the user to confirm to save
            final File file = new File(filePath);
            boolean result = true;
            if (file.exists()) {
            	result = MyDialogUtils.getConfirmDialogResult(
            			"The file already exists, would you like to overwrite it?", JOptionPane.YES_NO_OPTION);
            }
        	if (result) {
        		saveSettings(); // update position list and acquisition settings before saving
                final String json = acqTable_.getTableData().toJson(true);
                MyFileUtils.writeStringToFile(filePath, json);
        	}
        }
    }
    
    /**
     * Loads the acquisition table and position list and their metadata.
     * 
     * @throws JsonSyntaxException if the selected file is not valid json
     */
    public void loadAcquisitionTable() {
        // open a file browser
        fileBrowser_.setDialogTitle("Load acquisition settings from the directory...");
        if (fileBrowser_.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            // load data from json
            final String filePath = fileBrowser_.getSelectedFile().toString();
            final String json = MyFileUtils.readFileToString(filePath);
            //System.out.println(json);
            
            // parse the json file into an object
            AcquisitionTableData data = null;
            try {
            	data = AcquisitionTableData.fromJson(json);
                // clear data before loading settings from json
                acqTable_.clearData();
                acqTable_.setTableData(data);
                // update position list items
                lstPositions_.addItems(data.getPositionListNames());
            } catch (JsonSyntaxException e) {
            	ReportingUtils.showError("JsonSyntaxException: unable to parse file: \n" + e.getMessage());
            }
        }
    }
    
    /**
     * Gets the current acquisition settings from the AcquisitionPanel and adds them to the table.
     */
    public void addAcquisiton() {
        // get user input
        final String name = MyDialogUtils.showTextEntryDialog(this, 
                "Add acquisition", "Enter the name of the acquisition:"
        );
        // canceled dialog box => early exit
        if (name == null) {
            return;
        }
        // error checking => early exit
        if (acqTable_.getTableData().acqNameExists(name)) {
            MyDialogUtils.showErrorMessage(this, "Name Error", "Name already exists, enter a unique name.");
            return;
        } else if (name.length() == 0) {
            MyDialogUtils.showErrorMessage(this, "Name Error", "Name cannot be an empty string.");
            return;
        } else if (!AcquisitionTable.isNameValid(name)) {
            MyDialogUtils.showErrorMessage(this, "Name Error", 
                    "Name can only contain characters, digits, and underscores.");
            return;
        } else if (name.equals(AcquisitionTable.DEFAULT_ACQ_NAME)) {
        	MyDialogUtils.showErrorMessage(this, "Name Error", "Name cannot be \"" 
        			+ AcquisitionTable.DEFAULT_ACQ_NAME + "\".");
        	return;
        } else {
            acqTable_.addAcquisitionSettings(name, acqPanel_.getCurrentAcquisitionSettings());
        }
    }
    
    public void addPositionList() {
        // get user input
        final String name = MyDialogUtils.showTextEntryDialog(this, 
                "Add position list", "Enter the name of the position list:"
        );
        // canceled dialog box => early exit
        if (name == null) {
            return;
        }
        // error checking => early exit
        if (lstPositions_.contains(name)) {
            MyDialogUtils.showErrorMessage(this, "Name Error", "Name already exists, enter a unique name.");
            return;
        } else if (name.length() == 0) {
            MyDialogUtils.showErrorMessage(this, "Name Error", "Name cannot be an empty string.");
            return;
        } else if (!AcquisitionTable.isNameValid(name)) {
            MyDialogUtils.showErrorMessage(this, "Name Error", 
                    "Name can only contain characters, digits, and underscores.");
            return;
        } else {
            lstPositions_.addItem(name);
            lstPositions_.setSelectedValue(name, true);
            lstPositions_.repaint();
            acqTable_.addPositionList(name, lstPositions_.getPositionListNames());
        }
    }
    
    private void removeSelectedPositionList() {
        final Object selected = lstPositions_.getSelectedValue();
        if (selected != null) {
            final String name = selected.toString();
            if (name.equals(AcquisitionTablePositionList.NO_POSITION_LIST)) {
                MyDialogUtils.showErrorMessage(this, "Remove Error", "Unable to remove \"" 
                		+ AcquisitionTablePositionList.NO_POSITION_LIST + "\".");
                return;
            }
            lstPositions_.removeItem(name); // remove name from JList
            acqTable_.removePositionList(name);
            System.out.println("remove: " + name);
        }
    }
    
    private void renameSelectedPositionList() {
        if (lstPositions_.getNumItems() == 0) {
            MyDialogUtils.showErrorMessage(this, "Rename Error", "No position list to rename.");
            return;
        }
        // get user input
        final String oldName = lstPositions_.getSelectedValue().toString();
        if (oldName.equals(AcquisitionTablePositionList.NO_POSITION_LIST)) {
            MyDialogUtils.showErrorMessage(this, "Rename Error", "Unable to rename \"None\".");
            return;
        }
        final String newName = MyDialogUtils.showTextEntryDialog(this, 
                "Rename position list", "Enter the new name of the position list (" + oldName + "):"
        );
        // canceled dialog box => early exit
        if (newName == null) {
            return;
        }
        // newName already exists => early exit
        if (lstPositions_.contains(newName)) {
            MyDialogUtils.showErrorMessage(this, 
                    "Rename Error", "Name already exists, please choose a unique name."
            );
            return;
        }
        // rename the underlying HashMap key
        acqTable_.renamePositionList(oldName, newName);
        // update the JList items
        lstPositions_.renameItem(oldName, newName);
    }
    
    private void setupAcquisitionAndRun(final AcquisitionMetadata data) {
        // change acquisition settings
        final AcquisitionSettings acqSettings = 
                acqTable_.getTableData().getAcquisitionSettings(data.getAcquisitionName());
        acqPanel_.setAcquisitionSettings(acqSettings);
        
        // change position list
        final String positionListName = data.getPositionListName();
        if (!positionListName.equals(AcquisitionTablePositionList.NO_POSITION_LIST)) {
            final PositionList positionList = acqTable_.getTableData().getPositionList(positionListName);
            acqTable_.setMMPositionList(positionList);
        } else {
            acqTable_.clearMMPositionList(); // empty position list
        }
        
        // start the acquisition with these settings
        acqPanel_.runAcquisition();
    }
    
    /**
     * Run all acquisitions in the table sequentially.<P>
     * 
     * Always sets acquisition failures to quiet and save 
     * while acquiring to true.<P>
     * 
     * Acquisition windows are always closed to free memory.
     */
    public void runSequentialAcquisitions() {
    	// check for errors outside of the SwingWorker to update components on the EDT
    	
        txtAcqStatusLog_.setText(""); // reset error log
        
        // send errors to the acquisition status log
        MyDialogUtils.SEND_ERROR_TO_COMPONENT = true;
        
        // always set acquisition failures to quiet => dialogs prevent acqs from running
        // get the current value so we can restore it at the end of acquisitions
        final boolean hideErrors = isAcquisitionFailureQuiet();
        if (!hideErrors) {
            setAcquisitionFailureQuiet(true);
        }
        
        // check if there are any acquisitions to run
        final int numAcqs = acqTable_.getTableData().getNumAcquisitions();
        if (numAcqs == 0) {
        	MyDialogUtils.showError("No acquisitions to run.", true);
        	btnRunAcquisitions_.setSelected(false);
        	return; // early exit => do not start thread
        }
        
        // ensure that the save directory has been set
        if (saveDirectory_.equals("")) {
        	MyDialogUtils.showError("Please select the playlist save directory.", true);
        	btnRunAcquisitions_.setSelected(false);
        	return; // early exit => do not start thread
        }
        
        // save AcquisitionSettings and PositionList to update any changes to the settings
        saveSettings();
        
        // always save images during an acquisition
        final boolean saveWhileAcq = acqPanel_.getSavingSaveWhileAcquiring();
        acqPanel_.setSavingSaveWhileAcquiring(true);
        
        // clear current selections
        lstPositions_.clearSelectionAndReset();
        acqTable_.clearSelection();
        
        // error checking complete and fields that are needed in the SwingWorker are marked final
    	final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
        	
            @Override
            protected Void doInBackground() throws Exception {
                // used to cancel acquisition
                isRunning_.set(true);

                // links acquisitions names, acquisition settings, and position lists
                final List<AcquisitionMetadata> metadata = 
                        acqTable_.getTableData().getMetadataList();
        		
                // run each aquisition in sequence
                int acqIndex = 0;
                boolean allAcqsDone = false;
                while (!allAcqsDone) {
                	// cancel acquisition if requested
				    if (!isRunning_.get()) {
				    	acqPanel_.stopAcquisition();
				    	allAcqsDone = true;
				    	break;
				    }
				    // check to see if we can start the next acquisition
                	if (!acqPanel_.isAcquisitionRequested()) {
                		// close the acquisition window to free memory
                		if (acqIndex > 0) {
                			gui_.closeAcquisitionWindow(acqPanel_.getAcqName());
                		}
                		// start the next acquisition or exit if all acqs done
                		if (acqIndex < metadata.size()) {
	                		setupAcquisitionAndRun(metadata.get(acqIndex));
	                		// update swing components on the EDT
	                		final int currentAcqNum = acqIndex + 1;
	                		final String acqName = metadata.get(acqIndex).getAcquisitionName();
	                		SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
				                    lblCurrentAcqName_.setText("Current Acquisition: " + acqName);
				                    lblAcqProgress_.setText("Acquisition Status: " + currentAcqNum + "/" + numAcqs);
							    	txtAcqStatusLog_.append("Run Acquisition: " + acqName + "\n");
								}
	                		});
                		} else {
                			// we tried running all acquisitions
                			allAcqsDone = true;
                			break;
                		}
                		acqIndex++;
                	}
                	Thread.sleep(1000);
                }

                // save playlist metadata json
                final String filePath = MyFileUtils.createUniquePath(saveDirectory_, "playlist_metadata", "txt");
                final String json = acqTable_.getTableData().toJson(true);
                MyFileUtils.writeStringToFile(filePath, json);
                
                return null;
            }
            
            @Override
            protected void done() {
                // save acquisition status log
                final String filePath = MyFileUtils.createUniquePath(saveDirectory_, "acquisition_log", "txt");
                MyFileUtils.writeStringToFile(filePath, txtAcqStatusLog_.getText());
                
                // restore original settings
                setAcquisitionFailureQuiet(hideErrors);
                acqPanel_.setSavingSaveWhileAcquiring(saveWhileAcq);
                MyDialogUtils.SEND_ERROR_TO_COMPONENT = false;
                
                // report to user
                btnRunAcquisitions_.setSelected(false); // sets isRunning to false
                lblCurrentAcqName_.setText("Current Acquisition: Finished");
                txtAcqStatusLog_.append("Acquisitions complete\n");
            }
            
        };
        worker.execute();
    }
    
    /**
     * Save the current acquisition settings if an acquisition is selected.<P>
     * Save the current position list if a position list is selected.
     */
    private void saveSettings() {
        // save AcquisitionSettings and PositionList to update any settings changes
        if (acqTable_.getSelectedRow() != -1) {
        	acqTable_.getTableData().updateAcquisitionSettings(
        			acqTable_.getCurrentAcqName(), acqPanel_.getCurrentAcquisitionSettings());
        }
        if (lstPositions_.getSelectedValue() != null) {
            final String selected = lstPositions_.getSelectedValue().toString();
            if (!selected.equals(AcquisitionTablePositionList.NO_POSITION_LIST)) {
            	acqTable_.getTableData().addPositionList(selected, acqTable_.getMMPositionList());
            }
        }
    }
    
    /**
     * Returns true if the acquisition failures are set to quiet.
     * 
     * @return true if acquisitions failures are quiet
     */
    private boolean isAcquisitionFailureQuiet() {
        return prefs_.getBoolean(MyStrings.PanelNames.SETTINGS.toString(), 
                Properties.Keys.PLUGIN_ACQUIRE_FAIL_QUIETLY, true);
    }
    
    /**
     * Sets the acquisition failures quiet setting to the preferences.
     * 
     * @param state true to set acquisition failures to quiet
     */
    private void setAcquisitionFailureQuiet(final boolean state) {
        prefs_.putBoolean(MyStrings.PanelNames.SETTINGS.toString(), 
                Properties.Keys.PLUGIN_ACQUIRE_FAIL_QUIETLY, state);
    }
    
    /**
     * Returns a reference to the acquisition table.
     * 
     * @return a reference to the acquisition table
     */
    public AcquisitionTable getAcquisitionTable() {
        return acqTable_;
    }
    
}
