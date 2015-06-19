///////////////////////////////////////////////////////////////////////////////
// FILE:          PatternDesignPanel.java
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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author Wayne
 */
public class PatternDesignPanel extends javax.swing.JPanel {
    private final int State_Select = 10;
    private final int State_MovingVectorGraph = 11;
    
    private final int State_SelectKeyPoint = 20;
    private final int State_MovingKeyPoint = 21;
    
    private final int State_AddLine = 30;
    private final int State_AddLinePoint2 = 31;
    
    private final int State_AddPolyline = 40;
    
    private final int State_AddPolygon = 50;
    
    private final int State_AddRectangle = 60;
    private final int State_AddRectanglePoint2 = 61;
    
    private final int State_AddEllipse = 70;
    private final int State_AddEllipsePoint2 = 71;
    
    private final int State_Pasting = 80;
    
    private Camera camera_;
    private SLM slm_;
    private Mapping mapping_;
    private Rectangle cameraEWA_;
    private Rectangle slmEWA_;

    private ArrayList seq_;
    
    private Pattern pattern_;
    private boolean showSampleImage_;
    
    private int penWidth_;
    private Color color_;
    private int transparency_;
    
    private int state_;
    private int selectedIdx_;
    private int selectedKeyPointIdx_;
    private VectorGraphElement vge2Add_;
    private Point vgeMoveStartPos_ = new Point();
    private boolean dragging_;
    
    private Rectangle2D.Float aoiCamera_ = new Rectangle2D.Float(0, 0, 0, 0);
    private Rectangle2D.Float aoiWindow_ = new Rectangle2D.Float(0, 0, 0, 0);
    private Rectangle2D.Float drawRect_ = new Rectangle2D.Float(0, 0, 0, 0);

    private String fileName_ = new String();
    private boolean modified_;

    private UnDo undo_ = new UnDo();

    private VectorGraphElement vge2Paste_;
    
    private UIFlag view_;

    private boolean uploaded_ = false;
    /**
     * Creates new form PatternDesignPanel
     */
    public PatternDesignPanel() {
        ResetState();
        initComponents();
    }

    /**
     * Set objects involved in pattern design.
     * @param camera
     * @param slm
     * @param mapping 
     * @param cameraEWA 
     */
    public void setMapping( Camera camera, SLM slm, Mapping mapping, Rectangle cameraEWA )
    {
        camera_ = camera;
        slm_ = slm;
        mapping_ = mapping;
        cameraEWA_ = cameraEWA;
    }

    /**
     * Set pattern design UI notifier
     * @param v 
     */
    public void setUINotify( UIFlag v )
    {
        view_ = v;
        updateButtons();
    }
    
    public void setSequence( ArrayList seq )
    {
        seq_ = seq;
    }
    
    /**
     * Set the pattern to be modified
     * @param pattern 
     */
    public void setPattern( Pattern pattern )
    {
        if( !promptSave() )
            return;
        pattern_ = pattern;
        StartUnDo();
        SaveUnDoState();
        fileName_ = "";
        ResetState();
        setModified(false);
        repaint();
    }
    
    /**
     * Get the pattern being edited
     * @return 
     */
    public Pattern getPattern()
    {
        return pattern_;
    }
    
    /**
     * Get pen width
     * @return 
     */
    public int getPenWidth()
    {
        return penWidth_;
    }
    /**
     * Set pen width
     * @param penWidth 
     */
    public void setPenWidth(int penWidth)
    {
        penWidth_ = penWidth;
        if( drawRect_.width < 1 || drawRect_.height < 1 )
            return;
        boolean m = false;
        if( selectedIdx_ >= 0 ){
            pattern_.vg.getVectorGraphElement(selectedIdx_).setWidth((float) (penWidth / Math.sqrt(drawRect_.width * drawRect_.height)));
            m = true;
        }
        if( vge2Add_ != null){
            vge2Add_.setWidth((float) (penWidth / Math.sqrt(drawRect_.width * drawRect_.height)));
            m = true;
        }
        if( m )
            setModified( true );
        repaint();
    }
    
    /**
     * Get color
     * @return 
     */
    public Color getColor()
    {
        return color_;
    }
    /**
     * Set color
     * @param color
     */
    public void setColor(Color color)
    {
        color_ = color;
        boolean m = false;
        if( selectedIdx_ >= 0 ){
            pattern_.vg.getVectorGraphElement(selectedIdx_).setColor(color_);
            m = true;
        }
        if( vge2Add_ != null ){
            vge2Add_.setColor(color_);
            m = true;
        }
        if( m )
            setModified( true );
        repaint();
    }
    
    /**
     * Get transparency
     * @return
     */
    public int getTransparency()
    {
        return transparency_;
    }

    /**
     * Set transparency
     * @param transparency
     */
    public void setTransparency(int transparency)
    {
        transparency_ = transparency;
        repaint();
    }
    
    /**
     * Get showSampleImage option
     * @return 
     */
    public boolean getShowSampleImage()
    {
        return showSampleImage_;
    }
    /**
     * Set showSampleImage option
     * @param b 
     */
    public void setShowSampleImage( boolean b )
    {
        showSampleImage_ = b;
        updateButtons();
        repaint();
    }

    /**
     * Change modified state
     * @param b 
     */
    private void setModified( boolean b )
    {
        if( b )
            SaveUnDoState();
        modified_ = b;
        updateButtons();
    }

    /**
     * Get file name
     * @return 
     */
    public String getFileName()
    {
        return fileName_;
    }
    
    private void StartUnDo()
    {
        if( undo_ == null )
            undo_ = new UnDo();
        else
            undo_.destory();
        undo_.create( pattern_ );
    }
    
    private void SaveUnDoState()
    {
        undo_.SaveState();
        updateButtons();
    }

    /**
     * Paint
     */
    @Override
    public void paint(Graphics g)
    {
        try {
            BufferedImage bi = camera_.getBufferedImage();

            if( cameraEWA_.width > 0 && cameraEWA_.height > 0 ){
                aoiCamera_.x = cameraEWA_.x;
                aoiCamera_.y = cameraEWA_.y;
                aoiCamera_.width = cameraEWA_.width;
                aoiCamera_.height = cameraEWA_.height;
            }
            else{
                aoiCamera_.x = 0;
                aoiCamera_.y = 0;
                aoiCamera_.width = bi.getWidth();
                aoiCamera_.height = bi.getHeight();
            }
            
            float asCamera = (float)bi.getHeight()/bi.getWidth();
            float asWindow = (float)getHeight()/getWidth();
            int x, y, w, h;
            if(asCamera > asWindow){
                h = getHeight();
                w = (int)(h / asCamera);
                x = 0;//(getWidth() - w)/2;
                y = 0;
            }
            else{
                w = getWidth();
                h = (int)(w * asCamera);
                x = 0;
                y = 0;//(getHeight()-h)/2;
            }
            drawRect_.x = x;
            drawRect_.y = y;
            drawRect_.width = w;
            drawRect_.height = h;
            
            Graphics2D g2 = (Graphics2D) g;
            if(showSampleImage_){
                g2.drawImage(bi, x, y, w, h, this);
                g2.setColor(getBackground());
                if(w < getWidth() )
                    g2.fillRect(x + w, 0, getWidth() - w, h);
                if(h < getHeight() )
                    g2.fillRect(0, y + h, w, getHeight() - h);

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) ((100 - transparency_) / 100.0)));
            }
            else{
                g2.setColor(Color.BLACK);
                g2.fillRect(x, y, w, h);
                g2.setColor(getBackground());
                if(w < getWidth() )
                    g2.fillRect(x + w, 0, getWidth() - w, h);
                if(h < getHeight() )
                    g2.fillRect(0, y + h, w, getHeight() - h);
            }
            
            aoiWindow_.x = x + aoiCamera_.x * w / bi.getWidth();
            aoiWindow_.y = y + aoiCamera_.y * h / bi.getHeight();
            aoiWindow_.width = aoiCamera_.width * w / bi.getWidth();
            aoiWindow_.height = aoiCamera_.height * h / bi.getHeight();
            
            pattern_.draw(g, aoiWindow_);
            
            if(selectedIdx_ >=0)
                pattern_.vg.getVectorGraphElement(selectedIdx_).draw(g, aoiWindow_, true);
            
            if(vge2Add_!=null)
                vge2Add_.draw(g, aoiWindow_, true);
            
            if(state_ == State_Pasting){
                vge2Paste_.draw(g, aoiWindow_, true);
            }
            if(showSampleImage_)
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1));
            g2.setColor(Color.RED);
            g2.draw( aoiWindow_ );
        } catch (Exception ex) {
            Logger.getLogger(PatternDesignPanel.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Prompt user to save pattern
     * @return 
     */
    public boolean promptSave()
    {
        if( !modified_ )
            return true;
        int rc = JOptionPane.showConfirmDialog(this, "Pattern is modified, do you want to save it ?", "Pattern Design", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if(rc == JOptionPane.YES_OPTION)
            return Save();
        else if(rc == JOptionPane.NO_OPTION)
            return true;
        else
            return false;
    }

    /** 
     * Clear uploaded flag
     */
    public void ClearUploadedFlag()
    {
        uploaded_ = false;
    }
    
    /**
     * Prompt user if uploaded pattern should be cleared
     * Return:
     *      true:   to continue
     *      false:  operation canceled
     */
    public int PromptClearUploadedPattern(boolean CanCancel)
    {
        if( uploaded_ ){
            slm_.stopSequence();
            uploaded_ = false;
            return JOptionPane.YES_OPTION;
        }
        return JOptionPane.NO_OPTION;
/*
        if( uploaded_ ){
            int rc = JOptionPane.showConfirmDialog(this, "Pattern has been uploaded, do you want to stop it ?", "Pattern Design", CanCancel?JOptionPane.YES_NO_CANCEL_OPTION:JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if(rc == JOptionPane.YES_OPTION){
                slm_.stopSequence();
                uploaded_ = false;
            }
            return rc;
        }
        return JOptionPane.NO_OPTION;
*/        
    }
    /**
     * Create a new pattern
     */
    public void New()
    {
        if( !promptSave() )
            return;
        if( PromptClearUploadedPattern(true) == JOptionPane.CANCEL_OPTION )
            return;
        pattern_.Clear();
        StartUnDo();
        SaveUnDoState();
        fileName_ = "";
        ResetState();
        setModified(false);
        repaint();
    }
    
    /**
     * Read a pattern from a file
     */
    public void Open()
    {
        if( !promptSave() )
            return;
        if( PromptClearUploadedPattern(true) == JOptionPane.CANCEL_OPTION )
            return;
        JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Pattern Files", "ptn");
        fc.setFileFilter(filter);        
        int rc = fc.showOpenDialog(this);
        System.out.println("showOpenFile returns " + rc );
        if(rc != JFileChooser.APPROVE_OPTION)
            return;
        if( pattern_.Load(fc.getSelectedFile().getAbsolutePath()) ){
            StartUnDo();
            SaveUnDoState();
            fileName_ = fc.getSelectedFile().getAbsolutePath();
            ResetState();
            setModified(false);
            repaint();
        }
    }
    
    private boolean Save( String fn )
    {
        try{
            if(fn.length() < 4 || !fn.substring(fn.length() - 4).equalsIgnoreCase(".ptn"))
                fn = fn + ".ptn";
            pattern_.Save(fn);
            fileName_ = fn;
            setModified(false);
            return true;
        }
        catch(Exception e){
            System.out.println(e);
            return false;
        }
    }
    public boolean Save()
    {
        if(fileName_.equals(""))
            return SaveAs();
        return Save(fileName_);
    }

    public boolean SaveAs()
    {
        JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Pattern Files", "ptn");
        fc.setFileFilter(filter);        
        int rc = fc.showSaveDialog(this);
        if(rc == JFileChooser.APPROVE_OPTION) 
            return Save(fc.getSelectedFile().getAbsolutePath());
        else if(rc == JFileChooser.ERROR_OPTION){
            JOptionPane.showMessageDialog(this, "Failed to get a file to save.", "Pattern Design", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        else
            return false;
    }

    /**
     * Upload pattern to SLM
     */
    public void Upload()
    {
        BufferedImage bi = new BufferedImage( slm_.getWidth(), slm_.getHeight(), BufferedImage.TYPE_BYTE_GRAY );

        Graphics2D g = bi.createGraphics();
        g.setTransform( mapping_.getCameraToPolygonAffineTransform() );
        g.drawImage( pattern_.bi, (int)aoiCamera_.x, (int)aoiCamera_.y, (int)aoiCamera_.width, (int)aoiCamera_.height, null );
        pattern_.vg.draw( g, aoiCamera_ );
        Utility.Save( bi, "Upload.bmp" );
        slm_.ShowImage( bi );
        uploaded_ = true;
        updateButtons();
    }

    /**
     * Stop pattern display
     */
    public void StopUploadedPattern()
    {
        slm_.stopSequence();
        uploaded_ = false;
        updateButtons();
    }
    /**
     * Cancel current action
     */
    public void ResetState()
    {
        state_ = State_Select;
        selectedIdx_ = -1;
        selectedKeyPointIdx_ = -1;
        vge2Add_ = null;
    }
    
    /**
     * Begin to add a new line
     */
    public void StartAddingLine()
    {
        ResetState();
        state_ = State_AddLine;
        VGLine vge = new VGLine();
        vge.setColor(color_);
        vge.setWidth((float) (penWidth_ / Math.sqrt(drawRect_.width * drawRect_.height)));
        vge2Add_ = vge;
    }

    /**
     * Begin to add a new rectangle
     */
    public void StartAddingRectangle()
    {
        ResetState();
        state_ = State_AddRectangle;
        VGRectangle vge = new VGRectangle();
        vge.setColor(color_);
        vge2Add_ = vge;
    }
    
    /**
     * Begin to add a new ellipse
     */
    public void StartAddingEllipse()
    {
        ResetState();
        state_ = State_AddEllipse;
        VGEllipse vge = new VGEllipse();
        vge.setColor(color_);
        vge2Add_ = vge;
    }
    
    /**
     * Begin to add a polyline
     */
    public void StartAddingPolyline()
    {
        ResetState();
        state_ = State_AddPolyline;
        VGPolyline vge = new VGPolyline();
        vge.setColor(color_);
        vge.setWidth((float) (penWidth_ / Math.sqrt(drawRect_.width * drawRect_.height)));
        vge2Add_ = vge;
    }
    
    /**
     * Begin to add a polygon
     */
    public void StartAddingPolygon()
    {
        ResetState();
        state_ = State_AddPolygon;
        VGPolygon vge = new VGPolygon();
        vge.setColor(color_);
        vge2Add_ = vge;
    }

    public void ChangeBackgroundImage()
    {
        JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Image Files", "bmp", "png", "jpg", "gif");
        fc.setFileFilter(filter);        
        int rc = fc.showOpenDialog(this);
        System.out.println("showOpenFile returns " + rc );
        if(rc != JFileChooser.APPROVE_OPTION)
            return;
        BufferedImage bi;
        try{
            bi = ImageIO.read( new File(fc.getSelectedFile().getAbsolutePath()));
            pattern_.bi = bi;
            setModified(true);
            repaint();
        }
        catch(IOException ex){
        }
    }
    
    public void ClearBackgroundImage()
    {
        pattern_.ClearBackground();
        setModified(true);
        repaint();
    }
    
    /**
     * Select vector graph element
     */
    public void StartSelectObject()
    {
        ResetState();
        updateButtons();
        repaint();
    }
    
    /**
     * Delete selected vector graph
     */
    public void DeleteSelectedVectorGraph()
    {
        if( state_ == State_Select && selectedIdx_ >= 0 ){
            pattern_.vg.remove( selectedIdx_ );
            selectedIdx_ = -1;
            setModified(true);
            repaint();
        }
    }
    
    /**
     * Select key point of selected vector graph
     */
    public void StartSelectKeyPoint()
    {
        if( state_ == State_Select && selectedIdx_ >= 0 ){
            state_ = State_SelectKeyPoint;
            updateButtons();
        }
    }

    public void Copy()
    {
        if( selectedIdx_ >= 0 ){
            vge2Paste_ = pattern_.vg.getVectorGraphElement( selectedIdx_ ).clone();
            updateButtons();
        }
    }
    
    public void Paste()
    {
        if( vge2Paste_ != null ){
            ResetState();
            state_ = State_Pasting;
            updateButtons();
        }
    }
    
    public void UnDo()
    {
        undo_.UnDo();
        ResetState();
        modified_ = true;
        updateButtons();
        repaint();
    }
    
    public void ReDo()
    {
        undo_.ReDo();
        ResetState();
        modified_ = true;
        updateButtons();
        repaint();
    }

    public void AddToSequence()
    {
        try {
            seq_.add( pattern_.clone() );
        }
        catch( CloneNotSupportedException ex ){
            System.out.println( ex );
        }
    }
    
    private void updateButtons()
    {
        long n = PolygonForm.UIFlag_New | PolygonForm.UIFlag_Open;
        if( modified_ )
            n |= PolygonForm.UIFlag_Save;
        n |= PolygonForm.UIFlag_SaveAs;
        
        if( undo_.CanUnDo() )
            n |= PolygonForm.UIFlag_UnDo;
        if( undo_.CanReDo() )
            n |= PolygonForm.UIFlag_ReDo;
        if( selectedIdx_ >= 0 )
            n |= PolygonForm.UIFlag_Copy;
        if( vge2Paste_ != null )
            n |= PolygonForm.UIFlag_Paste;
        
        n |= PolygonForm.UIFlag_Line | PolygonForm.UIFlag_Rectangle | PolygonForm.UIFlag_Ellipse | PolygonForm.UIFlag_Polyline | PolygonForm.UIFlag_Polygon;
        
        if( cameraEWA_.width > 0 && cameraEWA_.height > 0 )
            n |= PolygonForm.UIFlag_Upload;
        if( uploaded_ )
            n |= PolygonForm.UIFlag_ClearUpload;
        
        n |= PolygonForm.UIFlag_Select;
        
        if( selectedIdx_ >= 0 )
            n |= PolygonForm.UIFlag_SelectKeyPoint | PolygonForm.UIFlag_Delete;
        
        n |= PolygonForm.UIFlag_Add2Seq;
        
        n |= PolygonForm.UIFlag_UpdateSample ;

        if( view_ != null )
            view_.setUIFlag( n );
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setDoubleBuffered(false);
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                formMouseDragged(evt);
            }
            public void mouseMoved(java.awt.event.MouseEvent evt) {
                formMouseMoved(evt);
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                formMouseClicked(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                formMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                formMouseReleased(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 622, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 473, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void formMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMousePressed
        if( drawRect_.width < 1 || drawRect_.height < 1 )
            return;
        
        if( state_ == State_Select ){
            Point pt = evt.getPoint();
            selectedIdx_ = pattern_.vg.select( pt, aoiWindow_ );
            if( selectedIdx_ >= 0 ){
                vgeMoveStartPos_ = pt;
                dragging_ = false;
                state_ = State_MovingVectorGraph;
            }
            updateButtons();
            repaint();
            return;
        }
        
        if( state_ == State_SelectKeyPoint ){
            Point pt = evt.getPoint();
            selectedKeyPointIdx_ = pattern_.vg.getVectorGraphElement( selectedIdx_ ).selectKeyPoint( pt, aoiWindow_ );
            if( selectedKeyPointIdx_ >= 0 ){
                dragging_ = false;
                state_ = State_MovingKeyPoint;
            }
            updateButtons();
            repaint();
            return;
        }
        
        if( state_ == State_Pasting ){
            pattern_.vg.add( vge2Paste_.clone() );
            selectedIdx_ = pattern_.vg.getCount() - 1;
            state_ = State_Select;
            setModified(true);
            repaint();
            return;
        }
        
        if( state_ == State_AddLine ){
            VGLine vge = (VGLine)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(0, pt2);
            vge.setPoint(1, pt2);
            state_ = State_AddLinePoint2;
            repaint();
            return;
        }
        if( state_ == State_AddLinePoint2 ){
            VGLine vge = (VGLine)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(1, pt2);
            pattern_.vg.add( vge2Add_ );
            selectedIdx_ = pattern_.vg.getCount() - 1;
            vge2Add_ = null;
            state_ = State_Select;
            setModified(true);
            repaint();
            return;
        }

        if( state_ == State_AddRectangle ){
            VGRectangle vge = (VGRectangle)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(0, pt2);
            vge.setPoint(1, pt2);
            state_ = State_AddRectanglePoint2;
            repaint();
            return;
        }
        if( state_ == State_AddRectanglePoint2 ){
            VGRectangle vge = (VGRectangle)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(1, pt2);
            pattern_.vg.add( vge2Add_ );
            selectedIdx_ = pattern_.vg.getCount() - 1;
            vge2Add_ = null;
            state_ = State_Select;
            setModified(true);
            repaint();
            return;
        }

        if( state_ == State_AddEllipse ){
            VGEllipse vge = (VGEllipse)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(0, pt2);
            vge.setPoint(1, pt2);
            state_ = State_AddEllipsePoint2;
            repaint();
            return;
        }
        if( state_ == State_AddEllipsePoint2 ){
            VGEllipse vge = (VGEllipse)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(1, pt2);
            pattern_.vg.add( vge2Add_ );
            selectedIdx_ = pattern_.vg.getCount() - 1;
            vge2Add_ = null;
            state_ = State_Select;
            setModified(true);
            repaint();
            return;
        }
        
        if( state_ == State_AddPolyline ){
            VGPolyline vge = (VGPolyline)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            if( evt.getClickCount() == 1 ){
                if( vge.getPointCount() == 0 ){
                    vge.setPoint(-1, pt2);
                    vge.setPoint(-1, pt2);
                }
                else{
                    vge.setPoint( vge.getPointCount() - 1, pt2);
                    if( vge.getPointCount() >= 2 && vge.getPoint( vge.getPointCount() - 2 ) != pt2 )
                        vge.setPoint( - 1, pt2 );
                }
            }
            else{
                vge.RemoveDuplicatePoints();
                if( vge.getPointCount() > 1 ){
                    pattern_.vg.add( vge2Add_ );
                    selectedIdx_ = pattern_.vg.getCount() - 1;
                    vge2Add_ = null;
                    state_ = State_Select;
                    setModified(true);
                }
                else{
                    state_ = State_Select;
                    updateButtons();
                }
            }
            updateButtons();
            repaint();
            return;
        }
        
        if( state_ == State_AddPolygon ){
            VGPolygon vge = (VGPolygon)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            if( evt.getClickCount() == 1 ){
                if( vge.getPointCount() == 0 ){
                    vge.setPoint(-1, pt2);
                    vge.setPoint(-1, pt2);
                }
                else{
                    vge.setPoint( vge.getPointCount() - 1, pt2);
                    if( vge.getPointCount() >= 2 && vge.getPoint( vge.getPointCount() - 2 ) != pt2 )
                        vge.setPoint( - 1, pt2 );
                }
            }
            else{
                vge.RemoveDuplicatePoints();
                if( vge.getPointCount() > 2 ){
                    pattern_.vg.add( vge2Add_ );
                    selectedIdx_ = pattern_.vg.getCount() - 1;
                    vge2Add_ = null;
                    state_ = State_Select;
                    setModified(true);
                }
                else{
                    state_ = State_Select;
                    updateButtons();
                }
            }
            repaint();
            return;
        }
    }//GEN-LAST:event_formMousePressed

    private void formMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseReleased
        if( state_ == State_MovingVectorGraph ){
            if( dragging_ )
                setModified( true );
            state_ = State_Select;
            updateButtons();
        }
        if( state_ == State_MovingKeyPoint ){
            state_ = State_SelectKeyPoint;
            selectedKeyPointIdx_ = -1;
            if( dragging_ )
                setModified( true );
            else
                updateButtons();
        }
    }//GEN-LAST:event_formMouseReleased

    private void formMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseClicked
        // TODO add your handling code here:
    }//GEN-LAST:event_formMouseClicked

    private void formMouseMoved(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseMoved
        if( state_ == State_Pasting ){
            Point2D.Float src = vge2Paste_.getCentre();
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge2Paste_.move( pt2.x - src.x, pt2.y - src.y );
            repaint();
        }
        
        if( state_ == State_AddLinePoint2 ){
            VGLine vge = (VGLine)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(1, pt2);
            repaint();
            return;
        }

        if( state_ == State_AddRectanglePoint2 ){
            VGRectangle vge = (VGRectangle)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(1, pt2);
            repaint();
            return;
        }

        if( state_ == State_AddEllipsePoint2 ){
            VGEllipse vge = (VGEllipse)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            vge.setPoint(1, pt2);
            repaint();
            return;
        }
        
        if( state_ == State_AddPolyline ){
            VGPolyline vge = (VGPolyline)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            if( vge.getPointCount() > 0 )
                vge.setPoint( vge.getPointCount() - 1, pt2 );
            repaint();
            return;
        }

        if( state_ == State_AddPolygon ){
            VGPolygon vge = (VGPolygon)vge2Add_;
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            if( vge.getPointCount() > 0 )
                vge.setPoint( vge.getPointCount() - 1, pt2 );
            repaint();
            return;
        }
    }//GEN-LAST:event_formMouseMoved

    private void formMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseDragged
        if( state_ == State_MovingVectorGraph ){
            Point pt = evt.getPoint();
            pattern_.vg.getVectorGraphElement( selectedIdx_ ).move( ( pt.x - vgeMoveStartPos_.x ) / aoiWindow_.width, ( pt.y - vgeMoveStartPos_.y ) / aoiWindow_.height );
            vgeMoveStartPos_ = pt;
            dragging_ = true;
            repaint();
            return;
        }
        if( state_ == State_MovingKeyPoint ){
            Point pt = evt.getPoint();
            Point2D.Float pt2 = new Point2D.Float( ( pt.x - aoiWindow_.x ) / aoiWindow_.width, ( pt.y - aoiWindow_.y ) / aoiWindow_.height );
            pattern_.vg.getVectorGraphElement( selectedIdx_ ).moveKeyPoint( selectedKeyPointIdx_, pt2 );
            dragging_ = true;
            repaint();
            return;
        }
    }//GEN-LAST:event_formMouseDragged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
