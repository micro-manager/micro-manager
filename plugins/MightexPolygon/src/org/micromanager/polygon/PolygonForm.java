///////////////////////////////////////////////////////////////////////////////
// FILE:          PolygonForm.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MightexPolygon plugin
//-----------------------------------------------------------------------------
// DESCRIPTION:   Mightex Polygon400 plugin.
//                
// AUTHOR:        Wayne Liao, mightexsystem.com, 05/15/2015
//
// COPYRIGHT:     Mightex Systems, 2015
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.polygon;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.WindowConstants;
import org.micromanager.api.ScriptInterface;
import mmcorej.CMMCore;

/**
 *
 * @author Wayne
 */
public class PolygonForm extends javax.swing.JFrame implements UIFlag {
    private final static int PolygonDevicePanelIndex = 0;
    private final static int PatternDesignPanelIndex = 1;
    private final static int SessionControlPanelIndex = 2;
    
    // UIFlag for PatternDesignPanel
    public final static long UIFlag_New = 0x01;
    public final static long UIFlag_Open = 0x02;
    public final static long UIFlag_Save = 0x04;
    public final static long UIFlag_SaveAs = 0x08;
    public final static long UIFlag_UnDo = 0x10;
    public final static long UIFlag_ReDo = 0x20;
    public final static long UIFlag_Copy = 0x40;
    public final static long UIFlag_Paste = 0x80;
    public final static long UIFlag_Line = 0x100;
    public final static long UIFlag_Rectangle = 0x200;
    public final static long UIFlag_Ellipse = 0x400;
    public final static long UIFlag_Polyline = 0x800;
    public final static long UIFlag_Polygon = 0x1000;
    public final static long UIFlag_Image = 0x2000;
    public final static long UIFlag_Upload = 0x4000;
    public final static long UIFlag_Select = 0x8000;
    public final static long UIFlag_SelectKeyPoint = 0x10000;
    public final static long UIFlag_Delete = 0x20000;
    public final static long UIFlag_Add2Seq = 0x40000;
    public final static long UIFlag_UpdateSample = 0x80000;
    public final static long UIFlag_ClearSample = 0x100000;
    public final static long UIFlag_ClearUpload = 0x200000;
    
    // UIFlag for SessionControlPanel
    public final static long UIFlag_MoveTo = 0x200000;
    public final static long UIFlag_DeleteSelected = 0x400000;
    public final static long UIFlag_DeleteAll = 0x800000;
    public final static long UIFlag_PatternPeriod = 0x1000000;
    public final static long UIFlag_LoopCount = 0x2000000;
    public final static long UIFlag_PositiveEdgeTrigger = 0x4000000;
    public final static long UIFlag_NegativeEdgeTrigger = 0x8000000;
    public final static long UIFlag_Start = 0x10000000;
    public final static long UIFlag_Stop = 0x20000000;
    
    private final ScriptInterface app_;
    private final CMMCore core_;
    private Camera camera_;
    private final SLM slm_;
    
    private CalibrationThread calThd_;
    
    private final Mapping mapping_ = new Mapping();
    private final Rectangle cameraEWA_ = new Rectangle();
    private final Rectangle slmEWA_ = new Rectangle();
    
    private final ActionListener timerAction_;
    private final javax.swing.Timer tmr_;
    private int thdState = 0;
    
    private final ArrayList seq_ = new ArrayList();

    private SessionThread sessionThd_;

    private int TimerCount = 0;

    private boolean ProjectingPattern_ = false;

    private int LastPanelIndex_ = 0;
    
    public PolygonForm(ScriptInterface app) {
        app_ = app;
        core_ = app_.getMMCore();
        camera_ = new Camera( app_ );
        slm_ = new SLM(core_);
        calThd_ = null;
        
        try{
            initComponents();
        }
        catch( Exception ex ){
            Utility.LogMsg("PolygonForm.PolygonForm: " + ex.toString() );
            Utility.LogException(ex);
        }

        // setup patternDesignPanel
        patternDesignPanel.setMapping(camera_, slm_, mapping_, cameraEWA_);
        patternDesignPanel.setPattern(new Pattern());
        patternDesignPanel.setSequence(seq_);
        
        patternDesignPanel.setPenWidth((Integer)PenWidthSpinner.getValue());
        patternDesignPanel.setTransparency((Integer)TransparencySpinner.getValue());
        ColorComboBox.setSelectedIndex(1);
        patternDesignPanel.setColor((Integer)ColorComboBox.getSelectedIndex()==0?Color.BLACK:Color.WHITE);
        
        SpotSizeFactorSpinner.setValue( 1 );
        ImageWaitTimeSpinner.setValue( 1 );
        
        timerAction_ = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                TimerCount++;
                int n = MainPanel.getSelectedIndex();
                switch( n ){
                    case PolygonDevicePanelIndex:
                        try{
                            if( calThd_ == null){
                                btnCalibrate.setText("Calibrate");
                            }
                            else if(calThd_.isAlive()){
                                btnCalibrate.setText("Stop Calibration");
                                lblMsg.setText(thdState + " Active: " + calThd_.getCalState() + ", " + calThd_.getCalIdx() + " ( " + calThd_.peakPoint.x + ", " + calThd_.peakPoint.y + " ) Value =" + calThd_.peakValue + ", Var = " + calThd_.var );
                            }
                            else{
                                btnCalibrate.setText("Calibrate");
                                lblMsg.setText(thdState + " Inactive: " + calThd_.getCalState() + ", " + calThd_.getCalIdx());
                            }
                            BufferedImage bi = camera_.getBufferedImage();
                            Graphics g = ImagePanel.getGraphics();
                            g.drawImage( bi, 0, 0, ImagePanel.getWidth(), ImagePanel.getHeight(), null );

                            sequenceDesignPanel.updateButtons();
                        }
                        catch(Exception e){

                        }
                        break;
                    case PatternDesignPanelIndex:
                        Thread.yield();
                        patternDesignPanel.repaint();
                        break;
                    case SessionControlPanelIndex:
                        if( TimerCount % 20 == 0 )
                            sequenceDesignPanel.updateButtons();
                        break;
                }
            }
        };
        tmr_ = new javax.swing.Timer(50, timerAction_);
        tmr_.start();

        sequenceDesignPanel.setParent(this);
/* 2015/05/04 Switch to AT
        sequenceDesignPanel.setMapping(slm_, mapping_, slmEWA_);
*/        
        sequenceDesignPanel.setMapping(slm_, mapping_, cameraEWA_);
        
        sequenceDesignPanel.setSequence(seq_);
        sequenceDesignPanel.setScrollBar(seqScrollBar);
        sequenceDesignPanel.setUINotify(this);
        updateTitle();

        addWindowListener( new WindowAdapter(){
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if( ProjectingPattern_ ){
                    if( JOptionPane.showConfirmDialog(null, 
                        "Closing the plugin will cause all patterns to terminate. Are you sure you want to close the plugin ?", "Mightex Polygon Plugin Closing", 
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE) == JOptionPane.NO_OPTION){
                        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                        return;
                    }
                }
                slm_.stopSequence();
                ProjectingPattern_ = false;
            }
        });
        
        slm_.ShowBlank();
//        System.out.println( ToolTipManager.sharedInstance().getInitialDelay());
        ToolTipManager.sharedInstance().setInitialDelay(500);
//        System.out.println( ToolTipManager.sharedInstance().getInitialDelay());
    }

    /**
     * update window title
     */
    void updateTitle()
    {
        String fn = patternDesignPanel.getFileName();
        this.setTitle(fn.equals("")?"unknown":fn);
    }
    
    public void editPattern( Pattern ptn )
    {
        patternDesignPanel.setPattern(ptn);
        MainPanel.setSelectedIndex(PatternDesignPanelIndex);
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        MasterSlaveButtonGroup = new javax.swing.ButtonGroup();
        SlaveTriggerButtonGroup = new javax.swing.ButtonGroup();
        MainPanel = new javax.swing.JTabbedPane();
        DevicePanel = new javax.swing.JPanel();
        btnCalibrate = new javax.swing.JButton();
        btnShowGrid = new javax.swing.JButton();
        lblMsg = new javax.swing.JLabel();
        ImagePanel = new javax.swing.JPanel();
        btnLightOff = new javax.swing.JButton();
        btnLightOn = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        SpotSizeFactorSpinner = new javax.swing.JSpinner();
        jLabel4 = new javax.swing.JLabel();
        ImageWaitTimeSpinner = new javax.swing.JSpinner();
        PatternDesignPanel = new javax.swing.JPanel();
        SaveButton = new javax.swing.JButton();
        Action saveAction = new AbstractAction(){
            @Override
            public void actionPerformed(ActionEvent e){
                patternDesignPanel.Save();
                updateTitle();
            }
        };
        SaveButton.setAction(saveAction);
        SaveButton.getInputMap(2).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.CTRL_MASK), "SavePatternFile");
        SaveButton.getActionMap().put("SavePatternFile", saveAction);
        OpenButton = new javax.swing.JButton();
        Action openAction = new AbstractAction(){
            @Override
            public void actionPerformed(ActionEvent e){
                patternDesignPanel.Open();
                updateTitle();
            }
        };
        OpenButton.setAction(openAction);
        OpenButton.getInputMap(2).put(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK), "OpenPatternFile");
        OpenButton.getActionMap().put("OpenPatternFile", openAction);

        UndoButton = new javax.swing.JButton();
        Action undoAction = new AbstractAction(){
            @Override
            public void actionPerformed(ActionEvent e){
                patternDesignPanel.UnDo();
            }
        };
        UndoButton.setAction(undoAction);
        UndoButton.getInputMap(2).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ActionEvent.CTRL_MASK), "UnDo");
        UndoButton.getActionMap().put("UnDo", undoAction);
        RedoButton = new javax.swing.JButton();
        Action redoAction = new AbstractAction(){
            @Override
            public void actionPerformed(ActionEvent e){
                patternDesignPanel.ReDo();
            }
        };
        RedoButton.setAction(redoAction);
        RedoButton.getInputMap(2).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, ActionEvent.CTRL_MASK), "ReDo");
        RedoButton.getActionMap().put("ReDo", redoAction);
        CopyButton = new javax.swing.JButton();
        Action copyAction = new AbstractAction(){
            @Override
            public void actionPerformed(ActionEvent e){
                patternDesignPanel.Copy();
            }
        };
        CopyButton.setAction(copyAction);
        CopyButton.getInputMap(2).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK), "CopyVGE");
        CopyButton.getActionMap().put("CopyVGE", copyAction);

        PasteButton = new javax.swing.JButton();
        Action pasteAction = new AbstractAction(){
            @Override
            public void actionPerformed(ActionEvent e){
                patternDesignPanel.Paste();
            }
        };
        PasteButton.getInputMap(2).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK), "PasteVGE");
        PasteButton.getActionMap().put("PasteVGE", pasteAction);
        LineButton = new javax.swing.JButton();
        PolylineButton = new javax.swing.JButton();
        RectangleButton = new javax.swing.JButton();
        EllipseButton = new javax.swing.JButton();
        UploadButton = new javax.swing.JButton();
        SelectButton = new javax.swing.JButton();
        SelectKeyPointButton = new javax.swing.JButton();
        AddToSeqButton = new javax.swing.JButton();
        DeleteButton = new javax.swing.JButton();
        ImageButton = new javax.swing.JButton();
        TransparencyLabel = new javax.swing.JLabel();
        TransparencySpinner = new javax.swing.JSpinner();
        PenWidthLabel = new javax.swing.JLabel();
        PenWidthSpinner = new javax.swing.JSpinner();
        patternDesignPanel = new org.micromanager.polygon.PatternDesignPanel();
        jLabel1 = new javax.swing.JLabel();
        ColorComboBox = new javax.swing.JComboBox();
        NewButton = new javax.swing.JButton();
        Action newAction = new AbstractAction(){
            @Override
            public void actionPerformed(ActionEvent e){
                patternDesignPanel.New();
                updateTitle();
            }
        };
        NewButton.setAction(newAction);
        NewButton.getInputMap(2).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK), "NewPattern");
        NewButton.getActionMap().put("NewPattern", newAction);
        SaveAsButton = new javax.swing.JButton();
        PolygonButton = new javax.swing.JButton();
        ClearImageButton = new javax.swing.JButton();
        ShowImageButton = new javax.swing.JToggleButton();
        ClearUploadButton = new javax.swing.JButton();
        SessionControlPanel = new javax.swing.JPanel();
        PatternDeleteButton = new javax.swing.JToggleButton();
        sequenceDesignPanel = new org.micromanager.polygon.SequenceDesignPanel();
        seqScrollBar = new javax.swing.JScrollBar();
        PatternCountLable = new javax.swing.JLabel();
        PatternClearButton = new javax.swing.JButton();
        MasterModeRadioButton = new javax.swing.JRadioButton();
        SlaveModeRadioButton = new javax.swing.JRadioButton();
        PatternPeriodLabel = new javax.swing.JLabel();
        LoopCountLabel = new javax.swing.JLabel();
        PatternPeriodSpinner = new javax.swing.JSpinner();
        LoopCountSpinner = new javax.swing.JSpinner();
        PositiveEdgeTriggerRadioButton = new javax.swing.JRadioButton();
        NegativeEdgeTriggerRadioButton = new javax.swing.JRadioButton();
        StartButton = new javax.swing.JButton();
        StopButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        MoveToButton = new javax.swing.JToggleButton();
        MoveToSpinner = new javax.swing.JSpinner();
        LoadPatternsButton = new javax.swing.JToggleButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });

        MainPanel.setDoubleBuffered(true);
        MainPanel.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                MainPanelStateChanged(evt);
            }
        });

        btnCalibrate.setText("Calibrate");
        btnCalibrate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCalibrateActionPerformed(evt);
            }
        });

        btnShowGrid.setText("Show Grid");
        btnShowGrid.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowGridActionPerformed(evt);
            }
        });

        lblMsg.setText("Ready.");

        javax.swing.GroupLayout ImagePanelLayout = new javax.swing.GroupLayout(ImagePanel);
        ImagePanel.setLayout(ImagePanelLayout);
        ImagePanelLayout.setHorizontalGroup(
            ImagePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 631, Short.MAX_VALUE)
        );
        ImagePanelLayout.setVerticalGroup(
            ImagePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 461, Short.MAX_VALUE)
        );

        btnLightOff.setText("All Off");
        btnLightOff.setName(""); // NOI18N
        btnLightOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLightOffActionPerformed(evt);
            }
        });

        btnLightOn.setText("All On");
        btnLightOn.setName(""); // NOI18N
        btnLightOn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLightOnActionPerformed(evt);
            }
        });

        jLabel3.setText("Spot Size Factor:");

        SpotSizeFactorSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, 10, 1));

        jLabel4.setText("Image Wait Time:");

        ImageWaitTimeSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, 10, 1));

        javax.swing.GroupLayout DevicePanelLayout = new javax.swing.GroupLayout(DevicePanel);
        DevicePanel.setLayout(DevicePanelLayout);
        DevicePanelLayout.setHorizontalGroup(
            DevicePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DevicePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(DevicePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblMsg, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(DevicePanelLayout.createSequentialGroup()
                        .addGroup(DevicePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(SpotSizeFactorSpinner, javax.swing.GroupLayout.DEFAULT_SIZE, 92, Short.MAX_VALUE)
                            .addComponent(btnShowGrid, javax.swing.GroupLayout.DEFAULT_SIZE, 92, Short.MAX_VALUE)
                            .addComponent(btnCalibrate, javax.swing.GroupLayout.DEFAULT_SIZE, 92, Short.MAX_VALUE)
                            .addComponent(btnLightOn, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnLightOff, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(ImageWaitTimeSpinner))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ImagePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        DevicePanelLayout.setVerticalGroup(
            DevicePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(DevicePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(DevicePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ImagePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(DevicePanelLayout.createSequentialGroup()
                        .addComponent(btnLightOff)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnLightOn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnShowGrid)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCalibrate)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(SpotSizeFactorSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ImageWaitTimeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblMsg)
                .addContainerGap())
        );

        MainPanel.addTab("Polygon Device", DevicePanel);

        SaveButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Save.png"))); // NOI18N
        SaveButton.setToolTipText("Save");

        OpenButton.setBackground(new java.awt.Color(255, 255, 255));
        OpenButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Open.png"))); // NOI18N
        OpenButton.setToolTipText("Open");

        UndoButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Undo.png"))); // NOI18N
        UndoButton.setToolTipText("Undo");

        RedoButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Redo.png"))); // NOI18N
        RedoButton.setToolTipText("Redo");

        CopyButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Copy.png"))); // NOI18N
        CopyButton.setToolTipText("Copy");

        PasteButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Paste.png"))); // NOI18N
        PasteButton.setToolTipText("Paste");
        PasteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PasteButtonActionPerformed(evt);
            }
        });

        LineButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Line.png"))); // NOI18N
        LineButton.setToolTipText("Line");
        LineButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LineButtonActionPerformed(evt);
            }
        });

        PolylineButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Polyline.png"))); // NOI18N
        PolylineButton.setToolTipText("Polyline");
        PolylineButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PolylineButtonActionPerformed(evt);
            }
        });

        RectangleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Rectangle.png"))); // NOI18N
        RectangleButton.setToolTipText("Rectangle");
        RectangleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RectangleButtonActionPerformed(evt);
            }
        });

        EllipseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Ellipse.png"))); // NOI18N
        EllipseButton.setToolTipText("Ellipse");
        EllipseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                EllipseButtonActionPerformed(evt);
            }
        });

        UploadButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Upload.png"))); // NOI18N
        UploadButton.setToolTipText("Upload to Polygon");
        UploadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                UploadButtonActionPerformed(evt);
            }
        });

        SelectButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Select.png"))); // NOI18N
        SelectButton.setToolTipText("Select");
        SelectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SelectButtonActionPerformed(evt);
            }
        });

        SelectKeyPointButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/SelectKeyPoint.png"))); // NOI18N
        SelectKeyPointButton.setToolTipText("Select Key Point");
        SelectKeyPointButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SelectKeyPointButtonActionPerformed(evt);
            }
        });

        AddToSeqButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Add2Seq.png"))); // NOI18N
        AddToSeqButton.setToolTipText("Add to Session");
        AddToSeqButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                AddToSeqButtonActionPerformed(evt);
            }
        });

        DeleteButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Recycle.png"))); // NOI18N
        DeleteButton.setToolTipText("Delete Selected Item");
        DeleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DeleteButtonActionPerformed(evt);
            }
        });

        ImageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Image.png"))); // NOI18N
        ImageButton.setToolTipText("Import Image");
        ImageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ImageButtonActionPerformed(evt);
            }
        });

        TransparencyLabel.setText("Transparency");

        TransparencySpinner.setModel(new javax.swing.SpinnerNumberModel(50, 0, 100, 10));
        TransparencySpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                TransparencySpinnerStateChanged(evt);
            }
        });

        PenWidthLabel.setText("Pen Width");

        PenWidthSpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, 100, 1));
        PenWidthSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                PenWidthSpinnerStateChanged(evt);
            }
        });

        patternDesignPanel.setBackground(new java.awt.Color(204, 204, 204));

        javax.swing.GroupLayout patternDesignPanelLayout = new javax.swing.GroupLayout(patternDesignPanel);
        patternDesignPanel.setLayout(patternDesignPanelLayout);
        patternDesignPanelLayout.setHorizontalGroup(
            patternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 661, Short.MAX_VALUE)
        );
        patternDesignPanelLayout.setVerticalGroup(
            patternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        jLabel1.setText("Color");

        ColorComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Black", "White" }));
        ColorComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ColorComboBoxActionPerformed(evt);
            }
        });

        NewButton.setBackground(new java.awt.Color(255, 255, 255));
        NewButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/New.png"))); // NOI18N
        NewButton.setToolTipText("New");

        SaveAsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/SaveAs.png"))); // NOI18N
        SaveAsButton.setToolTipText("Save As");
        SaveAsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveAsButtonActionPerformed(evt);
            }
        });

        PolygonButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Polygon.png"))); // NOI18N
        PolygonButton.setToolTipText("Polygon");
        PolygonButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PolygonButtonActionPerformed(evt);
            }
        });

        ClearImageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/ClearImage.png"))); // NOI18N
        ClearImageButton.setToolTipText("Clear Image");
        ClearImageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ClearImageButtonActionPerformed(evt);
            }
        });

        ShowImageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Camera.png"))); // NOI18N
        ShowImageButton.setSelected(true);
        ShowImageButton.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                ShowImageButtonStateChanged(evt);
            }
        });

        ClearUploadButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/ClearUpload2.png"))); // NOI18N
        ClearUploadButton.setToolTipText("Clear Uploaded Pattern");
        ClearUploadButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ClearUploadButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout PatternDesignPanelLayout = new javax.swing.GroupLayout(PatternDesignPanel);
        PatternDesignPanel.setLayout(PatternDesignPanelLayout);
        PatternDesignPanelLayout.setHorizontalGroup(
            PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(RectangleButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(EllipseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(LineButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(PolylineButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(CopyButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(PasteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(UndoButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(RedoButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, PatternDesignPanelLayout.createSequentialGroup()
                            .addComponent(UploadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(ClearUploadButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                            .addComponent(SelectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(SelectKeyPointButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                            .addComponent(PolygonButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(DeleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                            .addComponent(NewButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(OpenButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(SaveButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(SaveAsButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(TransparencyLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(TransparencySpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(PenWidthLabel)
                    .addComponent(PenWidthSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1)
                    .addComponent(ColorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 58, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(ImageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ClearImageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(ShowImageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(AddToSeqButton, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(14, 14, 14)
                .addComponent(patternDesignPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        PatternDesignPanelLayout.setVerticalGroup(
            PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addComponent(patternDesignPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(OpenButton)
                            .addComponent(NewButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(SaveButton)
                            .addComponent(SaveAsButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(UndoButton)
                            .addComponent(RedoButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(CopyButton)
                            .addComponent(PasteButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(LineButton)
                            .addComponent(PolylineButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(RectangleButton)
                            .addComponent(EllipseButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(PolygonButton)
                            .addComponent(DeleteButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ImageButton)
                            .addComponent(ClearImageButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(SelectButton)
                            .addComponent(SelectKeyPointButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(UploadButton)
                            .addComponent(ClearUploadButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(PatternDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(PatternDesignPanelLayout.createSequentialGroup()
                                .addComponent(ShowImageButton)
                                .addGap(8, 8, 8)
                                .addComponent(TransparencyLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(TransparencySpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(PenWidthLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(PenWidthSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ColorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(AddToSeqButton))
                        .addGap(0, 17, Short.MAX_VALUE))))
        );

        MainPanel.addTab("Pattern Design", PatternDesignPanel);

        PatternDeleteButton.setText("Delete");
        PatternDeleteButton.setPreferredSize(new java.awt.Dimension(99, 23));
        PatternDeleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PatternDeleteButtonActionPerformed(evt);
            }
        });

        sequenceDesignPanel.setMinimumSize(new java.awt.Dimension(200, 200));

        javax.swing.GroupLayout sequenceDesignPanelLayout = new javax.swing.GroupLayout(sequenceDesignPanel);
        sequenceDesignPanel.setLayout(sequenceDesignPanelLayout);
        sequenceDesignPanelLayout.setHorizontalGroup(
            sequenceDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 552, Short.MAX_VALUE)
        );
        sequenceDesignPanelLayout.setVerticalGroup(
            sequenceDesignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        seqScrollBar.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
            public void adjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {
                seqScrollBarAdjustmentValueChanged(evt);
            }
        });

        PatternCountLable.setText("Pattern Count: 0");

        PatternClearButton.setText("Clear");
        PatternClearButton.setPreferredSize(new java.awt.Dimension(99, 23));
        PatternClearButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PatternClearButtonActionPerformed(evt);
            }
        });

        MasterSlaveButtonGroup.add(MasterModeRadioButton);
        MasterModeRadioButton.setText("Master Mode");
        MasterModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MasterModeRadioButtonActionPerformed(evt);
            }
        });

        MasterSlaveButtonGroup.add(SlaveModeRadioButton);
        SlaveModeRadioButton.setSelected(true);
        SlaveModeRadioButton.setText("Slave Mode");
        SlaveModeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SlaveModeRadioButtonActionPerformed(evt);
            }
        });

        PatternPeriodLabel.setText("Period");

        LoopCountLabel.setText("Loops");

        PatternPeriodSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1000), Integer.valueOf(1), null, Integer.valueOf(1)));
        PatternPeriodSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                PatternPeriodSpinnerStateChanged(evt);
            }
        });

        LoopCountSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
        LoopCountSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                LoopCountSpinnerStateChanged(evt);
            }
        });

        SlaveTriggerButtonGroup.add(PositiveEdgeTriggerRadioButton);
        PositiveEdgeTriggerRadioButton.setSelected(true);
        PositiveEdgeTriggerRadioButton.setText(" Positive Edge");
        PositiveEdgeTriggerRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                PositiveEdgeTriggerRadioButtonActionPerformed(evt);
            }
        });

        SlaveTriggerButtonGroup.add(NegativeEdgeTriggerRadioButton);
        NegativeEdgeTriggerRadioButton.setText("Negative Edge");
        NegativeEdgeTriggerRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                NegativeEdgeTriggerRadioButtonActionPerformed(evt);
            }
        });

        StartButton.setText("Start");
        StartButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                StartButtonActionPerformed(evt);
            }
        });

        StopButton.setText("Stop");
        StopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                StopButtonActionPerformed(evt);
            }
        });

        jLabel2.setText("Trigger Type");

        MoveToButton.setText("Move Before");
        MoveToButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                MoveToButtonActionPerformed(evt);
            }
        });

        MoveToSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));

        LoadPatternsButton.setText("Load Patterns");
        LoadPatternsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                LoadPatternsButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout SessionControlPanelLayout = new javax.swing.GroupLayout(SessionControlPanel);
        SessionControlPanel.setLayout(SessionControlPanelLayout);
        SessionControlPanelLayout.setHorizontalGroup(
            SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(SessionControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(SessionControlPanelLayout.createSequentialGroup()
                        .addComponent(StartButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(StopButton, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(SlaveModeRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(PatternCountLable)
                    .addComponent(MasterModeRadioButton)
                    .addGroup(SessionControlPanelLayout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addComponent(PositiveEdgeTriggerRadioButton)
                            .addComponent(NegativeEdgeTriggerRadioButton)
                            .addGroup(SessionControlPanelLayout.createSequentialGroup()
                                .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(PatternPeriodLabel)
                                    .addComponent(LoopCountLabel))
                                .addGap(26, 26, 26)
                                .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(LoopCountSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(PatternPeriodSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 51, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                    .addGroup(SessionControlPanelLayout.createSequentialGroup()
                        .addComponent(MoveToButton, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(MoveToSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(LoadPatternsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(PatternDeleteButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(PatternClearButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(15, 15, 15)
                .addComponent(sequenceDesignPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(seqScrollBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        SessionControlPanelLayout.setVerticalGroup(
            SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, SessionControlPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(SessionControlPanelLayout.createSequentialGroup()
                        .addComponent(PatternCountLable)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(LoadPatternsButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(MoveToButton)
                            .addComponent(MoveToSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(PatternDeleteButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(PatternClearButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(8, 8, 8)
                        .addComponent(MasterModeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(PatternPeriodLabel)
                            .addComponent(PatternPeriodSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(LoopCountLabel)
                            .addComponent(LoopCountSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(SlaveModeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(PositiveEdgeTriggerRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(NegativeEdgeTriggerRadioButton)
                        .addGap(25, 25, 25)
                        .addGroup(SessionControlPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(StartButton)
                            .addComponent(StopButton)))
                    .addComponent(seqScrollBar, javax.swing.GroupLayout.DEFAULT_SIZE, 481, Short.MAX_VALUE)
                    .addComponent(sequenceDesignPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        MainPanel.addTab("Session Control", SessionControlPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(MainPanel)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(MainPanel)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnShowGridActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowGridActionPerformed
        slm_.ShowGrid();
        ProjectingPattern_ = true;
        patternDesignPanel.ClearUploadedFlag();
    }//GEN-LAST:event_btnShowGridActionPerformed

    private void btnCalibrateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCalibrateActionPerformed
        if( calThd_ == null || !calThd_.isAlive() ) {
            calThd_ = new CalibrationThread( camera_, slm_, mapping_, cameraEWA_, slmEWA_ );
            calThd_.SetSpotSizeFactor( (Integer)SpotSizeFactorSpinner.getValue() );
            calThd_.SetImageWaitTime( (Integer)ImageWaitTimeSpinner.getValue() * 1000 );
            calThd_.start();
            patternDesignPanel.ClearUploadedFlag();
        }
        else {
            try {
                calThd_.interrupt();
                while( calThd_.isAlive() )
                    Thread.sleep( 1000 );
                slm_.stopSequence();
            } catch (InterruptedException ex) {
                Logger.getLogger(PolygonForm.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_btnCalibrateActionPerformed

    private void LineButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LineButtonActionPerformed
        patternDesignPanel.StartAddingLine();
    }//GEN-LAST:event_LineButtonActionPerformed

    private void PolylineButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PolylineButtonActionPerformed
        patternDesignPanel.StartAddingPolyline();
    }//GEN-LAST:event_PolylineButtonActionPerformed

    private void RectangleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RectangleButtonActionPerformed
        patternDesignPanel.StartAddingRectangle();
    }//GEN-LAST:event_RectangleButtonActionPerformed

    private void EllipseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_EllipseButtonActionPerformed
        patternDesignPanel.StartAddingEllipse();
    }//GEN-LAST:event_EllipseButtonActionPerformed

    private void PolygonButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PolygonButtonActionPerformed
        patternDesignPanel.StartAddingPolygon();
    }//GEN-LAST:event_PolygonButtonActionPerformed

    private void UploadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_UploadButtonActionPerformed
        patternDesignPanel.Upload();
        ProjectingPattern_ = true;
    }//GEN-LAST:event_UploadButtonActionPerformed

    private void SelectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SelectButtonActionPerformed
        patternDesignPanel.StartSelectObject();
    }//GEN-LAST:event_SelectButtonActionPerformed

    private void SelectKeyPointButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SelectKeyPointButtonActionPerformed
        patternDesignPanel.StartSelectKeyPoint();
    }//GEN-LAST:event_SelectKeyPointButtonActionPerformed

    private void AddToSeqButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_AddToSeqButtonActionPerformed
        patternDesignPanel.AddToSequence();
    }//GEN-LAST:event_AddToSeqButtonActionPerformed

    private void DeleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DeleteButtonActionPerformed
        patternDesignPanel.DeleteSelectedVectorGraph();
    }//GEN-LAST:event_DeleteButtonActionPerformed

    private void ImageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ImageButtonActionPerformed
        patternDesignPanel.ChangeBackgroundImage();
    }//GEN-LAST:event_ImageButtonActionPerformed

    private void PenWidthSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_PenWidthSpinnerStateChanged
        patternDesignPanel.setPenWidth((Integer)PenWidthSpinner.getValue());
    }//GEN-LAST:event_PenWidthSpinnerStateChanged

    private void TransparencySpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_TransparencySpinnerStateChanged
        patternDesignPanel.setTransparency((Integer)TransparencySpinner.getValue());
    }//GEN-LAST:event_TransparencySpinnerStateChanged

    private void ColorComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ColorComboBoxActionPerformed
        patternDesignPanel.setColor((Integer)ColorComboBox.getSelectedIndex()==0?Color.BLACK:Color.WHITE);
    }//GEN-LAST:event_ColorComboBoxActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if(!patternDesignPanel.promptSave())
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE );
        else{
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            updateTitle();
        }
        if( calThd_ != null && calThd_.isAlive() )
            calThd_.interrupt();
        if( sequenceDesignPanel.IsSessionAlive() )
            sequenceDesignPanel.Stop();
    }//GEN-LAST:event_formWindowClosing

    private void MainPanelStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_MainPanelStateChanged
        if( MainPanel.getSelectedIndex() == PatternDesignPanelIndex )
            patternDesignPanel.setUINotify(this);
        if( MainPanel.getSelectedIndex() == SessionControlPanelIndex ){
            sequenceDesignPanel.setUINotify(this);
            sequenceDesignPanel.updateScrollBar();
        }
        
        if( LastPanelIndex_ == PatternDesignPanelIndex && patternDesignPanel.PromptClearUploadedPattern( false ) == JOptionPane.OK_OPTION )
            ProjectingPattern_ = false;
        LastPanelIndex_ = MainPanel.getSelectedIndex();
    }//GEN-LAST:event_MainPanelStateChanged

    private void ClearImageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ClearImageButtonActionPerformed
        patternDesignPanel.ClearBackgroundImage();
    }//GEN-LAST:event_ClearImageButtonActionPerformed

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        displayInfo( evt, "FormKeyPressed");
    }//GEN-LAST:event_formKeyPressed

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        System.out.println( "Form Width = " + getWidth() + ", height = " + getHeight() );
    }//GEN-LAST:event_formComponentResized

    private void seqScrollBarAdjustmentValueChanged(java.awt.event.AdjustmentEvent evt) {//GEN-FIRST:event_seqScrollBarAdjustmentValueChanged
        sequenceDesignPanel.repaint();
    }//GEN-LAST:event_seqScrollBarAdjustmentValueChanged

    private void PatternDeleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PatternDeleteButtonActionPerformed
        sequenceDesignPanel.DeleteSelected();
    }//GEN-LAST:event_PatternDeleteButtonActionPerformed

    private void MoveToButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MoveToButtonActionPerformed
        int n = (Integer)MoveToSpinner.getValue();
        sequenceDesignPanel.MoveTo( n - 1 );
    }//GEN-LAST:event_MoveToButtonActionPerformed

    private void PatternClearButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PatternClearButtonActionPerformed
        sequenceDesignPanel.DeleteAll();
    }//GEN-LAST:event_PatternClearButtonActionPerformed

    private void PasteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PasteButtonActionPerformed
        patternDesignPanel.Paste();
    }//GEN-LAST:event_PasteButtonActionPerformed

    private void MasterModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_MasterModeRadioButtonActionPerformed
        sequenceDesignPanel.setMasterMode(true);
    }//GEN-LAST:event_MasterModeRadioButtonActionPerformed

    private void SlaveModeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SlaveModeRadioButtonActionPerformed
        sequenceDesignPanel.setMasterMode(false);
    }//GEN-LAST:event_SlaveModeRadioButtonActionPerformed

    private void PatternPeriodSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_PatternPeriodSpinnerStateChanged
        sequenceDesignPanel.setPatternPeriod((Integer)PatternPeriodSpinner.getValue());
    }//GEN-LAST:event_PatternPeriodSpinnerStateChanged

    private void LoopCountSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_LoopCountSpinnerStateChanged
        sequenceDesignPanel.setLoopCount((Integer)LoopCountSpinner.getValue());
    }//GEN-LAST:event_LoopCountSpinnerStateChanged

    private void PositiveEdgeTriggerRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_PositiveEdgeTriggerRadioButtonActionPerformed
        sequenceDesignPanel.setPositiveEdgeTrigger(true);
    }//GEN-LAST:event_PositiveEdgeTriggerRadioButtonActionPerformed

    private void NegativeEdgeTriggerRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_NegativeEdgeTriggerRadioButtonActionPerformed
        sequenceDesignPanel.setPositiveEdgeTrigger(false);
    }//GEN-LAST:event_NegativeEdgeTriggerRadioButtonActionPerformed

    private void StartButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_StartButtonActionPerformed
        sequenceDesignPanel.setMasterMode( MasterModeRadioButton.isSelected() );
        sequenceDesignPanel.setPatternPeriod( (Integer)PatternPeriodSpinner.getValue() );
        sequenceDesignPanel.setLoopCount( (Integer)LoopCountSpinner.getValue() );
        sequenceDesignPanel.setPositiveEdgeTrigger( PositiveEdgeTriggerRadioButton.isSelected() );
        sequenceDesignPanel.Start();
        ProjectingPattern_ = false;
        MainPanel.setSelectedIndex(0);
        patternDesignPanel.ClearUploadedFlag();
    }//GEN-LAST:event_StartButtonActionPerformed

    private void StopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_StopButtonActionPerformed
        sequenceDesignPanel.Stop();
        ProjectingPattern_ = false;
    }//GEN-LAST:event_StopButtonActionPerformed

    private void btnLightOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLightOffActionPerformed
        slm_.ShowGrayscale(0);
        ProjectingPattern_ = false;
        patternDesignPanel.ClearUploadedFlag();
    }//GEN-LAST:event_btnLightOffActionPerformed

    private void btnLightOnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLightOnActionPerformed
        slm_.ShowGrayscale(255);
        ProjectingPattern_ = true;
        patternDesignPanel.ClearUploadedFlag();
    }//GEN-LAST:event_btnLightOnActionPerformed

    private void ShowImageButtonStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_ShowImageButtonStateChanged
        patternDesignPanel.setShowSampleImage(ShowImageButton.isSelected());
        if( ShowImageButton.isSelected() ){
            ShowImageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/NoCamera.png")));
            ShowImageButton.setToolTipText( "Don't show camera image" );
        }
        else{
            ShowImageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/micromanager/polygon/images/Camera.png")));
            ShowImageButton.setToolTipText( "Show camera image" );
        }
    }//GEN-LAST:event_ShowImageButtonStateChanged

    private void ClearUploadButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ClearUploadButtonActionPerformed
        patternDesignPanel.StopUploadedPattern();
        ProjectingPattern_ = false;
    }//GEN-LAST:event_ClearUploadButtonActionPerformed

    private void LoadPatternsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_LoadPatternsButtonActionPerformed
        sequenceDesignPanel.loadPatterns();
    }//GEN-LAST:event_LoadPatternsButtonActionPerformed

    private void SaveAsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveAsButtonActionPerformed
        patternDesignPanel.SaveAs();
    }//GEN-LAST:event_SaveAsButtonActionPerformed
    
    private void displayInfo(KeyEvent e, String keyStatus){
        int id = e.getID();
        String keyString;
        if (id == KeyEvent.KEY_TYPED) {
            char c = e.getKeyChar();
            keyString = "key character = '" + c + "'";
        } else {
            int keyCode = e.getKeyCode();
            keyString = "key code = " + keyCode
                    + " ("
                    + KeyEvent.getKeyText(keyCode)
                    + ")";
        }
        
        int modifiersEx = e.getModifiersEx();
        String modString = "extended modifiers = " + modifiersEx;
        String tmpString = KeyEvent.getModifiersExText(modifiersEx);
        if (tmpString.length() > 0) {
            modString += " (" + tmpString + ")";
        } else {
            modString += " (no extended modifiers)";
        }
        
        String actionString = "action key? ";
        if (e.isActionKey()) {
            actionString += "YES";
        } else {
            actionString += "NO";
        }
        
        String locationString = "key location: ";
        int location = e.getKeyLocation();
        if (location == KeyEvent.KEY_LOCATION_STANDARD) {
            locationString += "standard";
        } else if (location == KeyEvent.KEY_LOCATION_LEFT) {
            locationString += "left";
        } else if (location == KeyEvent.KEY_LOCATION_RIGHT) {
            locationString += "right";
        } else if (location == KeyEvent.KEY_LOCATION_NUMPAD) {
            locationString += "numpad";
        } else { // (location == KeyEvent.KEY_LOCATION_UNKNOWN)
            locationString += "unknown";
        }
        
        System.out.println( keyStatus );
        System.out.println( "\t" + keyString );
        System.out.println( "\t" + modString );
        System.out.println( "\t" + actionString );
        System.out.println( "\t" + locationString );
    }
    
    @Override
    public void setUIFlag( long n )
    {
        // PatternDesignPanel
        SaveButton.setEnabled( ( n & UIFlag_Save ) != 0 );
        UndoButton.setEnabled( ( n & UIFlag_UnDo ) != 0 );
        RedoButton.setEnabled( ( n & UIFlag_ReDo ) != 0 );
        CopyButton.setEnabled( ( n & UIFlag_Copy ) != 0 );
        PasteButton.setEnabled( ( n & UIFlag_Paste ) != 0 );
        UploadButton.setEnabled( ( n & UIFlag_Upload ) != 0 );
        ClearUploadButton.setEnabled( ( n & UIFlag_ClearUpload ) != 0 );
        
        SelectKeyPointButton.setEnabled( ( n & UIFlag_SelectKeyPoint ) != 0 );
        DeleteButton.setEnabled( ( n & UIFlag_Delete ) != 0 );
        AddToSeqButton.setEnabled( ( n & UIFlag_Add2Seq ) != 0 );
        ShowImageButton.setEnabled( ( n & UIFlag_UpdateSample ) != 0 );
        
        // SessionControlPanel
        PatternCountLable.setText( "Pattern Count: " + seq_.size() );
        MoveToButton.setEnabled( ( n & UIFlag_MoveTo ) != 0 );
        PatternDeleteButton.setEnabled( ( n & UIFlag_DeleteSelected ) != 0 );
        PatternClearButton.setEnabled( ( n & UIFlag_DeleteAll ) != 0 );
        PatternPeriodSpinner.setEnabled( ( n & UIFlag_PatternPeriod ) != 0 );
        LoopCountSpinner.setEnabled( ( n & UIFlag_LoopCount ) != 0 );
        PositiveEdgeTriggerRadioButton.setEnabled( ( n & UIFlag_PositiveEdgeTrigger ) != 0 );
        NegativeEdgeTriggerRadioButton.setEnabled( ( n & UIFlag_NegativeEdgeTrigger ) != 0 );
        StartButton.setEnabled( ( n & UIFlag_Start ) != 0 );
        StopButton.setEnabled( ( n & UIFlag_Stop ) != 0 );
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton AddToSeqButton;
    private javax.swing.JButton ClearImageButton;
    private javax.swing.JButton ClearUploadButton;
    private javax.swing.JComboBox ColorComboBox;
    private javax.swing.JButton CopyButton;
    private javax.swing.JButton DeleteButton;
    private javax.swing.JPanel DevicePanel;
    private javax.swing.JButton EllipseButton;
    private javax.swing.JButton ImageButton;
    private javax.swing.JPanel ImagePanel;
    private javax.swing.JSpinner ImageWaitTimeSpinner;
    private javax.swing.JButton LineButton;
    private javax.swing.JToggleButton LoadPatternsButton;
    private javax.swing.JLabel LoopCountLabel;
    private javax.swing.JSpinner LoopCountSpinner;
    private javax.swing.JTabbedPane MainPanel;
    private javax.swing.JRadioButton MasterModeRadioButton;
    private javax.swing.ButtonGroup MasterSlaveButtonGroup;
    private javax.swing.JToggleButton MoveToButton;
    private javax.swing.JSpinner MoveToSpinner;
    private javax.swing.JRadioButton NegativeEdgeTriggerRadioButton;
    private javax.swing.JButton NewButton;
    private javax.swing.JButton OpenButton;
    private javax.swing.JButton PasteButton;
    private javax.swing.JButton PatternClearButton;
    private javax.swing.JLabel PatternCountLable;
    private javax.swing.JToggleButton PatternDeleteButton;
    private javax.swing.JPanel PatternDesignPanel;
    private javax.swing.JLabel PatternPeriodLabel;
    private javax.swing.JSpinner PatternPeriodSpinner;
    private javax.swing.JLabel PenWidthLabel;
    private javax.swing.JSpinner PenWidthSpinner;
    private javax.swing.JButton PolygonButton;
    private javax.swing.JButton PolylineButton;
    private javax.swing.JRadioButton PositiveEdgeTriggerRadioButton;
    private javax.swing.JButton RectangleButton;
    private javax.swing.JButton RedoButton;
    private javax.swing.JButton SaveAsButton;
    private javax.swing.JButton SaveButton;
    private javax.swing.JButton SelectButton;
    private javax.swing.JButton SelectKeyPointButton;
    private javax.swing.JPanel SessionControlPanel;
    private javax.swing.JToggleButton ShowImageButton;
    private javax.swing.JRadioButton SlaveModeRadioButton;
    private javax.swing.ButtonGroup SlaveTriggerButtonGroup;
    private javax.swing.JSpinner SpotSizeFactorSpinner;
    private javax.swing.JButton StartButton;
    private javax.swing.JButton StopButton;
    private javax.swing.JLabel TransparencyLabel;
    private javax.swing.JSpinner TransparencySpinner;
    private javax.swing.JButton UndoButton;
    private javax.swing.JButton UploadButton;
    private javax.swing.JButton btnCalibrate;
    private javax.swing.JButton btnLightOff;
    private javax.swing.JButton btnLightOn;
    private javax.swing.JButton btnShowGrid;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel lblMsg;
    private org.micromanager.polygon.PatternDesignPanel patternDesignPanel;
    private javax.swing.JScrollBar seqScrollBar;
    private org.micromanager.polygon.SequenceDesignPanel sequenceDesignPanel;
    // End of variables declaration//GEN-END:variables
}
