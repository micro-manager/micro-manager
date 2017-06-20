package org.micromanager.dialogs;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.TooltipTextMaker;

public class CustomTimeIntervalsPanel extends JPanel {

    private final AcquisitionEngine acqEng_;
    private JTable timeIntervalTable_;
    private TimeIntervalTableModel intervalTableModel_;
    private JPanel buttonsPanel_;
    private JScrollPane scrollPane_;
    private JPanel infoPanel_;
    private LinearTimeDialog linearTimeDialog_;
    private LogTimeDialog logTimeDialog_;
    private JCheckBox useIntervalsCheckBox_;
    private final JTabbedPane window_;

    @SuppressWarnings("LeakingThisInConstructor")
    public CustomTimeIntervalsPanel(AcquisitionEngine acqEng, JTabbedPane window, ScriptInterface gui) {
        super();
        window_ = window;
        acqEng_ = acqEng;
        createTable();
        createButtons();
        createInfoPanel();
        configureLayout();
        if (gui != null) {
        	  setBackground(gui.getBackgroundColor());
        	  gui.addMMBackgroundListener(this);
        }

    }

    private void createInfoPanel() {
        infoPanel_ = new JPanel(new BorderLayout());
        JLabel info = new JLabel("<html>This tab allows you to set custom time intervals between successive frames.  "
                + "The above table shows the list of time intervals currently supplied to Micromanager, as well as their indices "
                + "and the expected elapsed time at the start of collection of each frame.  Each time interval represents the "
                + "minimum delay from the start of the previous frame to the start of the current one.  The interval corresponding"
                + " to frame 0 is the delay before the start of acquisition, and should be set to 0 ms unless such a delay is "
                + "desired.  Note that the actual intervals between frames and the actual elapsed times may exceed the values "
                + "listed here, depending on the speed with which the hardware can acquire images.  Intervals can be typed in "
                + "manually, or created with the \"Create Logarithmic Intervals\" and \"Create Constant Intervals\" buttons. </html>"
                );
        info.setFont(new Font("Arial", Font.PLAIN, 10));
        infoPanel_.add(info); 
    }
    
    private void createTable() {
        intervalTableModel_ = new TimeIntervalTableModel();
        timeIntervalTable_ = new JTable(intervalTableModel_);
        timeIntervalTable_.setDefaultRenderer(Object.class, new IntervalTableCellRenderer());
        scrollPane_ = new JScrollPane(timeIntervalTable_);
    }

    public void closeLinearDialog() {
        linearTimeDialog_.setVisible(false);
    }

    public void closeLogDialog() {
        logTimeDialog_.setVisible(false);
    }

    private void createButtons() {       
         buttonsPanel_ = new JPanel();
         BoxLayout buttonsPanelLayout = new BoxLayout(buttonsPanel_, BoxLayout.Y_AXIS);
         buttonsPanel_.setLayout(buttonsPanelLayout);
  
         //remove all
        JButton removeAllButton = new JButton("Remove All");
        removeAllButton.setFont(new Font("Arial", Font.PLAIN, 10));
        buttonsPanel_.add(removeAllButton);
        removeAllButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                intervalTableModel_.removeAll();
            } });
        removeAllButton.setMaximumSize(new Dimension(165,25));
      
        //remove
        JButton removeButton = new JButton("Remove");
        removeButton.setFont(new Font("Arial", Font.PLAIN, 10));
        buttonsPanel_.add(removeButton);
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                int[] selectedRows = timeIntervalTable_.getSelectedRows();
                if (selectedRows.length == 0) {
                    JOptionPane.showMessageDialog(buttonsPanel_, "Must select row(s) to be removed");
                } else if (selectedRows.length == 1) {
                    intervalTableModel_.removeRow(selectedRows[0]);
                } else {
                    Arrays.sort(selectedRows);
                    for (int k = selectedRows.length - 1; k >= 0; k--) {
                        intervalTableModel_.removeRow(selectedRows[k]);
                    }}}});
        removeButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
                "Remove currently selected row(s)"));
        removeButton.setMaximumSize(new Dimension(165,25));
        
        //add
        JButton addButton = new JButton("Add one frame");
        addButton.setFont(new Font("Arial", Font.PLAIN, 10));
        buttonsPanel_.add(addButton);
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {               
                int selectedRow = timeIntervalTable_.getSelectedRow();
                if (selectedRow == -1) {
                    intervalTableModel_.addRow(0);
                } else {
                    intervalTableModel_.insertRow(0, selectedRow);
                    timeIntervalTable_.addRowSelectionInterval(selectedRow + 1, selectedRow + 1);
                } 
            }});
        addButton.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(
                "Add new time point (Inserts above currently selected row(s) or at bottom of list if no row selected)"));
        addButton.setMaximumSize(new Dimension(165,25));

        //linear
        JButton createLinearTimePointsButton = new JButton("Create Constant Intervals");
        createLinearTimePointsButton.setFont(new Font("Arial", Font.PLAIN, 10));
        buttonsPanel_.add(createLinearTimePointsButton);
        createLinearTimePointsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (linearTimeDialog_ == null) 
                    linearTimeDialog_ = new LinearTimeDialog();     
                linearTimeDialog_.setVisible(true);
            }});
        createLinearTimePointsButton.setMaximumSize(new Dimension(165,25));
        createLinearTimePointsButton.setToolTipText("Create series of equally spaced frames");

        //log
        JButton createLogTimePointsButton = new JButton("Create Logarithmic Intervals");
        createLogTimePointsButton.setFont(new Font("Arial", Font.PLAIN, 10));
        buttonsPanel_.add(createLogTimePointsButton);
        createLogTimePointsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (logTimeDialog_ == null) 
                    logTimeDialog_ = new LogTimeDialog();         
                logTimeDialog_.setVisible(true);
            }});
        createLogTimePointsButton.setMaximumSize(new Dimension(165,25));
        createLogTimePointsButton.setToolTipText("Create series of equally logarithmically spaced frames");
        
        buttonsPanel_.add(new JLabel("      ")); //spacer
        
        useIntervalsCheckBox_ = new JCheckBox("Use custom intervals");
        useIntervalsCheckBox_.setEnabled(acqEng_.getCustomTimeIntervals() != null);
        useIntervalsCheckBox_.setSelected(acqEng_.customTimeIntervalsEnabled());
        buttonsPanel_.add(useIntervalsCheckBox_);
        useIntervalsCheckBox_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acqEng_.enableCustomTimeIntervals(useIntervalsCheckBox_.isSelected());
                setTabTitle();
            }});
        
    }

    private void setTabTitle() {
        if (acqEng_.customTimeIntervalsEnabled())
            window_.setTitleAt(0, "Custom time intervals (Enabled)");
        else
             window_.setTitleAt(0, "Custom time intervals (Disabled)");
    }

    private void configureLayout() {
         JPanel topPanel = new JPanel(new BorderLayout());
         topPanel.add(scrollPane_,BorderLayout.CENTER);
         topPanel.add(buttonsPanel_,BorderLayout.LINE_END);
         
         BoxLayout layout = new BoxLayout(this,BoxLayout.Y_AXIS);
         this.setLayout(layout);
         this.add(topPanel);
         this.add(infoPanel_);
        
    }
 
     public void syncCheckBoxFromAcqEng() {
         useIntervalsCheckBox_.setEnabled(acqEng_.getCustomTimeIntervals() != null);
         useIntervalsCheckBox_.setSelected(acqEng_.customTimeIntervalsEnabled());
         setTabTitle();
     }  
     
     public void syncIntervalsFromAcqEng() {
         intervalTableModel_.syncIntervalsFromAcqEng();
     }

    private class LogTimeDialog extends MMDialog {


        private JComboBox creationTypeCombo_;
        private JFormattedTextField aValue_;
        private JFormattedTextField rValue_;
        private JFormattedTextField tValue_;
        private JComboBox aCombo_;
        private JComboBox tCombo_;
        private JSpinner nValue_;
        private JCheckBox aCheck_, rCheck_, nCheck_, tCheck_;
        
       private double r_, t_, a_;
       private int n_;

        public LogTimeDialog() {
            super();
            this.setModal(true);
            this.setSize(new Dimension(520, 300));
            this.setResizable(false);
            this.setTitle("Create logarithmically spaced time points");
            this.setLocationRelativeTo(window_);
            
            this.getContentPane().setLayout(new BoxLayout(this.getContentPane(),BoxLayout.Y_AXIS));
            createCheckBoxes();
            createCreationComboandButtons();
            
        }
        
        private double convertToMS(double num, JComboBox unitCombo) {
             double result = num;
             int units = unitCombo.getSelectedIndex();
             if (units == 1)
                 result *= 1000;
             else if (units == 2)
                 result *= 60000;
             return result;
        }
        
        private void calcFourthParameter() {
        try {
                rValue_.commitEdit();
                nValue_.commitEdit();
                aValue_.commitEdit();
                tValue_.commitEdit();
            } catch (ParseException ex) {}
            
        if (!aCheck_.isSelected()) {
            r_ = ((Number) rValue_.getValue()).doubleValue();
            t_ = convertToMS(((Number) tValue_.getValue()).doubleValue(),tCombo_);
            n_ = (Integer) nValue_.getValue();
            a_ = t_ / Math.pow(r_, n_-2);
        } else if (!rCheck_.isSelected()) {
            a_ = convertToMS(((Number) aValue_.getValue()).doubleValue(),aCombo_);
            t_ = convertToMS(((Number) tValue_.getValue()).doubleValue(),tCombo_);
            n_ = (Integer) nValue_.getValue();
            r_ = Math.pow(t_/a_, 1.0/(n_-2));
        } else if (!tCheck_.isSelected()) {
            a_ = convertToMS(((Number) aValue_.getValue()).doubleValue(),aCombo_);
            n_ = (Integer) nValue_.getValue();
            r_ = ((Number) rValue_.getValue()).doubleValue();
            t_ = a_*Math.pow(r_,n_-2);
        } else  {
            a_ = convertToMS(((Number) aValue_.getValue()).doubleValue(),aCombo_);
            r_ = ((Number) rValue_.getValue()).doubleValue();
            t_ = convertToMS(((Number) tValue_.getValue()).doubleValue(),tCombo_);
            n_ = (int) Math.max(3,Math.log(t_/a_)/Math.log(r_) + 2);
        }
        }
        
        private int numberBoxesChecked() {
            int countSelected = 0;
            if (aCheck_.isSelected()) 
                countSelected++;
            if (rCheck_.isSelected()) 
                countSelected++;
            if (tCheck_.isSelected()) 
                countSelected++;
            if (nCheck_.isSelected()) 
                countSelected++;
            return countSelected;
        }

        private void updateParameterValues() {
            if (numberBoxesChecked() != 3) {
                return;
            }
                        
            calcFourthParameter();
            if (!aCheck_.isSelected()) {
                double dispVal = a_;
                if (aCombo_.getSelectedIndex() == 1) {
                    dispVal /= 1000.0;
                } else if (aCombo_.getSelectedIndex() == 2) {
                    dispVal /= 60000.0;
                }
                aValue_.setValue(dispVal);
            } else if (!rCheck_.isSelected()) {
                rValue_.setValue(r_);
            } else if (!tCheck_.isSelected()) {
                double dispVal = t_;
                if (tCombo_.getSelectedIndex() == 1) {
                    dispVal /= 1000.0;
                } else if (aCombo_.getSelectedIndex() == 2) {
                    dispVal /= 60000.0;
                }
                tValue_.setValue(dispVal);
            } else if (!nCheck_.isSelected()) {
                nValue_.setValue(n_);
            }
        }

        private void createCheckBoxes() {
            JPanel checkBoxPanel = new JPanel();
            checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));
            this.add(checkBoxPanel);
            JPanel labelPanel = new JPanel();
            this.add(labelPanel);
            labelPanel.add(new JLabel("Specify 3 of the following 4 parameters"));
            final JPanel row1 = new JPanel(), row2 = new JPanel(), row3 = new JPanel(), row4 = new JPanel();
            this.add(row1);
            this.add(row2);
            this.add(row3);
            this.add(row4);
           
            aCheck_ = new JCheckBox("Length of interval between first two frames",true);
            rCheck_ = new JCheckBox("Ratio of elapsed time of consecutive points",true);
            tCheck_ = new JCheckBox("Total time length",true);
            nCheck_ = new JCheckBox("Number of Frames",false);
            
            row1.add(aCheck_);
            row2.add(rCheck_);
            row3.add(tCheck_);
            row4.add(nCheck_);
            
            ActionListener boxesListener = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int checkCount = 0;
                    if( aCheck_.isSelected() )
                        checkCount++;
                    if( rCheck_.isSelected() )
                        checkCount++;
                    if( tCheck_.isSelected() )
                        checkCount++;
                    if( nCheck_.isSelected() )
                        checkCount++;
                    if (checkCount == 3) {
                        if (!aCheck_.isSelected())
                            for( Component c : row1.getComponents())
                                c.setEnabled(false);
                        else if(!rCheck_.isSelected())
                             for( Component c : row2.getComponents())
                                c.setEnabled(false);
                        else if (!tCheck_.isSelected())
                            for( Component c : row3.getComponents())
                                c.setEnabled(false);
                        else if (!nCheck_.isSelected())
                            for( Component c : row4.getComponents())
                                c.setEnabled(false);
                    }
                    else if (checkCount == 2) {                        
                        for( Component c : row1.getComponents())
                                c.setEnabled(true);
                        for (Component c : row2.getComponents()) 
                                c.setEnabled(true);
                        for (Component c : row3.getComponents()) 
                                c.setEnabled(true);
                        for (Component c : row4.getComponents()) 
                                c.setEnabled(true);

                        }
                 }};
             
            aCheck_.addActionListener(boxesListener);
            rCheck_.addActionListener(boxesListener);
            nCheck_.addActionListener(boxesListener);
            tCheck_.addActionListener(boxesListener);
            
            aValue_ = new JFormattedTextField(NumberFormat.getNumberInstance());
            aValue_.setFont(new Font("Arial", Font.PLAIN, 14));
            aValue_.setValue(1.0);
            aValue_.setPreferredSize(new Dimension(80, 22));
            aCombo_ = new JComboBox();
            aCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));
            aCombo_.setFont(new Font("Arial", Font.PLAIN, 14));
            row1.add(aValue_);
            row1.add(aCombo_);
            rValue_ = new JFormattedTextField(NumberFormat.getNumberInstance());
            rValue_.setFont(new Font("Arial", Font.PLAIN, 14));
            rValue_.setValue(2.0);           
            rValue_.setPreferredSize(new Dimension(80, 22));        
            row2.add(rValue_);
            tValue_ = new JFormattedTextField(NumberFormat.getNumberInstance());
            tValue_.setFont(new Font("Arial", Font.PLAIN, 14));
            tValue_.setValue(1000.0);
            tValue_.setPreferredSize(new Dimension(80, 22));
            tCombo_ = new JComboBox();
            tCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));
            tCombo_.setFont(new Font("Arial", Font.PLAIN, 14));
            row3.add(tValue_);
            row3.add(tCombo_);
            
            SpinnerModel sModel = new SpinnerNumberModel(new Integer(3), new Integer(3), null, new Integer(1));
            nValue_ = new JSpinner(sModel);
            nValue_.setPreferredSize(new Dimension(80, 22));
            ((JSpinner.DefaultEditor) nValue_.getEditor()).getTextField().setFont(new Font("Arial", Font.PLAIN, 14));
            row1.add(nValue_);
            row4.add(nValue_);
            
            
           
            ActionListener dynamicUpdater = new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                        updateParameterValues();
                }};
            
            aCombo_.addActionListener(dynamicUpdater);
            tCombo_.addActionListener(dynamicUpdater);
            aValue_.addCaretListener(new CaretListener() {
             @Override
             public void caretUpdate(CaretEvent e) {
                  if (aValue_.isEnabled())
                    updateParameterValues();
             }});
            tValue_.addCaretListener(new CaretListener() {
             @Override
             public void caretUpdate(CaretEvent e) {
                  if (tValue_.isEnabled())
                    updateParameterValues();
             }});
            rValue_.addCaretListener(new CaretListener() {
             @Override
             public void caretUpdate(CaretEvent e) {
                  if (rValue_.isEnabled())
                    updateParameterValues();
             }});
            nValue_.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {                  
                    if(nValue_.isEnabled()) 
                        updateParameterValues();
                } });
            ((JSpinner.DefaultEditor) nValue_.getEditor()).getTextField().addCaretListener(new CaretListener() {
             @Override
             public void caretUpdate(CaretEvent e) {
                  if (nValue_.isEnabled())
                    if (((Integer) nValue_.getValue()) >= 3 )
                        updateParameterValues();
             }});
            
            
            
            
            for(Component co : row4.getComponents())
                co.setEnabled(false);
            updateParameterValues();
        }
        
        private boolean checkParametersValid() {
        if (!aCheck_.isSelected()) {
           if (r_ <= 1) {
               JOptionPane.showMessageDialog(this, "Ratio parameter must be > 1");
               return false;
           }
        } else if (!rCheck_.isSelected()) {
           if (t_ <= a_) {
               JOptionPane.showMessageDialog(this, "Total time must be greater than interval between first two frames");
               return false;
           }
        } else if (!tCheck_.isSelected()) {
           if (r_ <= 1) {
               JOptionPane.showMessageDialog(this, "Ratio parameter must be > 1");
               return false;
           }
        } else if (!nCheck_.isSelected()) {
           if (t_ <= a_) {
               JOptionPane.showMessageDialog(this, "Total time must be greater than interval between first two frames");
               return false;
           }
           if (r_ <= 1) {
               JOptionPane.showMessageDialog(this, "Ratio parameter must be > 1");
               return false;
           }
        } 
        return true;
        }
        
        private void createButtonAction() { 
        if (numberBoxesChecked() != 3) {
            JOptionPane.showMessageDialog(this, "Select 3 parameters to specify");
            return;
        }
       
       calcFourthParameter();
       if (!checkParametersValid())
           return;
       
       ArrayList<Double> newIntervals = new ArrayList<Double>();
        newIntervals.add(0.0);   //first interval is 0 
        newIntervals.add(a_); // 2nd interval = a
        for (int i = 2; i < n_; i++) {
            newIntervals.add( a_*(Math.pow(r_,i-1) - Math.pow(r_, i-2) ));
        }                                
            int creationType = creationTypeCombo_.getSelectedIndex();
            if (creationType == 0) {
                intervalTableModel_.replaceList(newIntervals);
            } else if (creationType == 1) {
                intervalTableModel_.addListToStart(newIntervals);
            } else if (creationType == 2) {
                intervalTableModel_.addListToEnd(newIntervals);
            } else if (timeIntervalTable_.getSelectedRow() == -1) {
                intervalTableModel_.addListToEnd(newIntervals);
            } else {
                intervalTableModel_.addList(timeIntervalTable_.getSelectedRow(), newIntervals);
            }
            closeLogDialog();
                    
        }
      

        private void createCreationComboandButtons() { 
            //creation Type Row
            JPanel creationTypeRow = new JPanel();
            this.add(creationTypeRow);
            creationTypeCombo_ = new JComboBox();
            creationTypeCombo_.setModel(new DefaultComboBoxModel(new String[]{
                        "Replace current time point list", "Add to start of current list",
                        "Add to end of current list", "Insert at currently selected position"}));
            creationTypeCombo_.setFont(new Font("Arial", Font.PLAIN, 14));
            creationTypeRow.add(creationTypeCombo_);

            //Buttons Row
            JPanel buttonsRow = new JPanel();
            this.add(buttonsRow);

            JButton cancelButton = new JButton("Cancel");
            cancelButton.setFont(new Font("Arial", Font.PLAIN, 14));
            buttonsRow.add(cancelButton);
            cancelButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    closeLogDialog();
                }});

            JLabel spacer = new JLabel("        ");
            buttonsRow.add(spacer);

            JButton createButton = new JButton("Create");
            createButton.setFont(new Font("Arial", Font.PLAIN, 14));
            buttonsRow.add(createButton);
            createButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent arg0) {
                    createButtonAction();
                }});

        }
    }

    private class LinearTimeDialog extends MMDialog {

        private JSpinner numFrames_;
        private JFormattedTextField interval_;
        private JComboBox timeUnitCombo_;
        private JComboBox creationTypeCombo_;

        public LinearTimeDialog() {
            super();
             this.setModal(true);
            this.setSize(new Dimension(350, 200));
            this.setResizable(false);
            initComponents();
            this.setTitle("Create equal interval time points");
            this.setLocationRelativeTo(window_);
        }

        private void initComponents() {
            final LinearTimeDialog selfPointer = this;
            GridLayout layout = new GridLayout(4, 1);
            this.getContentPane().setLayout(layout);

            //Row 1
            JPanel row1 = new JPanel();
            this.add(row1);

            final JLabel numberLabel = new JLabel();
            numberLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            numberLabel.setText("Number of time points to create");
            row1.add(numberLabel);

            SpinnerModel sModel = new SpinnerNumberModel(new Integer(2), new Integer(2), null, new Integer(1));
            numFrames_ = new JSpinner(sModel);
            numFrames_.setPreferredSize(new Dimension(80, 22));
            ((JSpinner.DefaultEditor) numFrames_.getEditor()).getTextField().setFont(new Font("Arial", Font.PLAIN, 14));
            row1.add(numFrames_);

            //Row 2
            JPanel row2 = new JPanel();
            this.add(row2);

            final JLabel intervalLabel = new JLabel();
            intervalLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            intervalLabel.setText("Time interval between points");
            intervalLabel.setToolTipText("Interval between successive time points.  Setting an interval"
                    + "of 0 will cause micromanager to acquire 'burts' of images as fast as possible");
            row2.add(intervalLabel);

            interval_ = new JFormattedTextField(NumberFormat.getNumberInstance());
            interval_.setFont(new Font("Arial", Font.PLAIN, 14));
            interval_.setValue(1.0);
            interval_.setPreferredSize(new Dimension(80, 22));
            row2.add(interval_);

            timeUnitCombo_ = new JComboBox();
            timeUnitCombo_.setModel(new DefaultComboBoxModel(new String[]{"ms", "s", "min"}));
            timeUnitCombo_.setFont(new Font("Arial", Font.PLAIN, 14));
            row2.add(timeUnitCombo_);

            //Row 3
            JPanel row3 = new JPanel();
            this.add(row3);
            creationTypeCombo_ = new JComboBox();
            creationTypeCombo_.setModel(new DefaultComboBoxModel(new String[]{
                        "Replace current time point list", "Add to start of current list",
                        "Add to end of current list", "Insert at currently selected position"}));
            creationTypeCombo_.setFont(new Font("Arial", Font.PLAIN, 14));
            row3.add(creationTypeCombo_);

            //Row 4
            JPanel row4 = new JPanel();
            this.add(row4);

            JButton cancelButton = new JButton("Cancel");
            cancelButton.setFont(new Font("Arial", Font.PLAIN, 14));
            row4.add(cancelButton);
            cancelButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    closeLinearDialog();
                }
            });

            JLabel spacer = new JLabel("        ");
            row4.add(spacer);

            JButton createButton = new JButton("Create");
            createButton.setFont(new Font("Arial", Font.PLAIN, 14));
            row4.add(createButton);
            createButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent arg0) {
                    int numFrames = (Integer) numFrames_.getValue();
                    double interval = ((Number) interval_.getValue()).doubleValue();
                    int units = timeUnitCombo_.getSelectedIndex();
                    int creationType = creationTypeCombo_.getSelectedIndex();
                    if (numFrames >= 1 && interval >= 0) {
                        if (units == 1) // seconds
                        {
                            interval *= 1000;
                        } else if (units == 2) // min
                        {
                            interval *= 60000;
                        }
                        ArrayList<Double> newIntervals = new ArrayList<Double>();
                        newIntervals.add(0.0);
                        for (int k = 1; k < numFrames; k++) {
                            newIntervals.add(interval);
                        }
                        if (creationType == 0) {
                            intervalTableModel_.replaceList(newIntervals);
                        } else if (creationType == 1) {
                            intervalTableModel_.addListToStart(newIntervals);
                        } else if (creationType == 2) {
                            intervalTableModel_.addListToEnd(newIntervals);
                        } else {
                            int selected = timeIntervalTable_.getSelectedRow();
                            if (selected == -1) {
                                intervalTableModel_.addListToEnd(newIntervals);
                            } else {
                                intervalTableModel_.addList(selected, newIntervals);
                            }
                        }
                        closeLinearDialog();
                        
                    } else {
                        JOptionPane.showMessageDialog(selfPointer, "Invalid number of frames or time interval");
                    }
                }
            });



        }
    }

    private class IntervalTableCellRenderer extends DefaultTableCellRenderer {

        private final DecimalFormat formatter = new DecimalFormat("0.##");

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {

           // https://stackoverflow.com/a/3055930
           if (value == null) {
              return null;
           }

            Component cell = super.getTableCellRendererComponent(table, formatter.format((Number) value), isSelected, hasFocus, row, column);
            if (column == 0 || column == 2) {
                if (isSelected) {
                    cell.setBackground(Color.LIGHT_GRAY);
                    cell.setForeground(Color.BLACK);
                } else {
                    cell.setBackground(Color.DARK_GRAY);
                    cell.setForeground(Color.WHITE);
                }
            } else if (isSelected) {
                cell.setBackground(Color.BLUE);
                cell.setForeground(Color.WHITE);
            } else {
                cell.setBackground(Color.WHITE);
                cell.setForeground(Color.BLACK);
            }

            return cell;
        }
    }

    private class TimeIntervalTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        public final String[] COLUMN_NAMES = new String[]{"Frame Index", "Time interval (ms)", "Elapsed time(ms)"};
        private final ArrayList<Double> timeIntervals_;

        public TimeIntervalTableModel() {
            super();
            timeIntervals_ = new ArrayList<Double>();
            syncIntervalsFromAcqEng();
            this.addTableModelListener(new TableModelListener() {
                @Override
                public void tableChanged(TableModelEvent e) {
                    syncCheckBoxFromAcqEng();
                }
            });
        }
        
        private void sendIntervalsToAcqEng() {
            if (timeIntervals_ == null || timeIntervals_.isEmpty()) {
                acqEng_.setCustomTimeIntervals(null);
            } else {
                double[] intervalsArray = new double[timeIntervals_.size()];
                for (int i = 0; i < timeIntervals_.size(); i++) {
                    intervalsArray[i] = timeIntervals_.get(i);
                }
                acqEng_.setCustomTimeIntervals(intervalsArray);
            }
           fireTableDataChanged();
        }
        
        public final void syncIntervalsFromAcqEng() {
            timeIntervals_.clear();
            double[] existingCustomIntervals = acqEng_.getCustomTimeIntervals();
            if (existingCustomIntervals != null) {
                for (double d : existingCustomIntervals) {
                    timeIntervals_.add(d);
                }
            }
            fireTableDataChanged();
        }

        public ArrayList<Double> getIntervals() {
            return timeIntervals_;
        }

        public void replaceList(ArrayList<Double> intervals) {
            timeIntervals_.clear();
            timeIntervals_.addAll(intervals);
            sendIntervalsToAcqEng();
        }

        public void addListToStart(ArrayList<Double> intervals) {
            addList(0, intervals);
        }

        public void addListToEnd(ArrayList<Double> intervals) {
            addList(timeIntervals_.size(), intervals);
        }

        public void addList(int index, ArrayList<Double> intervals) {
            timeIntervals_.addAll(index, intervals);
            sendIntervalsToAcqEng();
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public int getRowCount() {
            return timeIntervals_.size();
        }

        public void removeAll() {
            timeIntervals_.clear();
            sendIntervalsToAcqEng();
        }

        public void removeRow(int index) {
            timeIntervals_.remove(index);
           sendIntervalsToAcqEng();
        }

        public void addRow(double interval) {
            timeIntervals_.add(interval);
            sendIntervalsToAcqEng();
        }

        public void insertRow(double interval, int row) {
            timeIntervals_.add(row, interval);
            sendIntervalsToAcqEng();

        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return rowIndex;
            } else if (columnIndex == 1) {
                return timeIntervals_.get(rowIndex);
            } else if (columnIndex == 2) {
                return (calcElapsedTime(rowIndex));
            }
            return null;
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int colIndex) {
            if (isCellEditable(rowIndex, colIndex)) {
                String str = (String) value;
                if (str.equals("")) {
                    timeIntervals_.set(rowIndex, 0.0);
                } else {
                    Double number = Double.parseDouble(str);
                    timeIntervals_.set(rowIndex, number);
                }
            }
            sendIntervalsToAcqEng();
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            if (col == 1 && row < timeIntervals_.size()) {
                return true;
            }
            return false;

        }

        private double calcElapsedTime(int row) {
            double elapsed = 0;
            for (int i = 0; i <= row; i++) {
                elapsed += timeIntervals_.get(i);
            }

            return elapsed;


        }
    }
}
