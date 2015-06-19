///////////////////////////////////////////////////////////////////////////////
// FILE:          SequenceDesignPanel.java
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
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 *
 * @author Wayne
 */
public class SequenceDesignPanel extends javax.swing.JPanel {
    public static int GridWidth = 200;
    public static int GridHeight = 200;
    public static int Edge = 10;
    public static int TitleHeight = 40;

    PolygonForm form_;
    private SLM slm_;
    private Mapping mapping_;
    private Rectangle aoi_;;
    
    private ArrayList seq_;
    private JScrollBar sb_;
    
    private UIFlag view_;

    private boolean [] selected_;
    private int lastSelectedIdx_;
    
    private boolean masterMode_;
    private int patternPeriod_;
    private int loopCount_;
    private boolean positiveEdgeTrigger_;
    
    private boolean dragging_;

    private SessionThread sessionThd_;
    
    /**
     * Creates new form SequenceDesignPanel
     */
    public SequenceDesignPanel() {
        initComponents();
    }

    public void setParent(PolygonForm f)
    {
        form_ = f;
    }
    
    public void setMapping( SLM slm, Mapping mapping, Rectangle aoi )
    {
        slm_ = slm;
        mapping_ = mapping;
        aoi_ = aoi;
    }
    
    public void setSequence( ArrayList seq )
    {
        seq_ = seq;
    }
    
    public void setScrollBar( JScrollBar sb )
    {
        sb_ = sb;
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
    
    public void updateButtons()
    {
        long n = 0;

        boolean somePatternSelected = IsTherePatternSelected();
        if( somePatternSelected )
            n |= PolygonForm.UIFlag_MoveTo | PolygonForm.UIFlag_DeleteSelected;
        if( seq_.size() > 0 )
            n |= PolygonForm.UIFlag_DeleteAll;
        if( masterMode_ )
            n |= PolygonForm.UIFlag_PatternPeriod | PolygonForm.UIFlag_LoopCount;
        else
            n |= PolygonForm.UIFlag_PositiveEdgeTrigger | PolygonForm.UIFlag_NegativeEdgeTrigger;
        n |= PolygonForm.UIFlag_Stop;
        if( sessionThd_ != null && sessionThd_.isAlive() )
            n &= ~PolygonForm.UIFlag_Start;
        else if( mapping_.IsValid() && seq_.size() > 0 )
            n |= PolygonForm.UIFlag_Start;
        
        view_.setUIFlag( n );
    }

    public void loadPatterns()
    {
        JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("Pattern Files", "ptn");
        fc.setFileFilter(filter);      
        fc.setMultiSelectionEnabled(true);
        int rc = fc.showOpenDialog(this);
        if(rc != JFileChooser.APPROVE_OPTION)
            return;
        File files[] = fc.getSelectedFiles();
        Pattern ptn = new Pattern();
        int pos = -1;
        for( int i = 0; i < seq_.size(); i++ ){
            if( getSelectFlag(i) ){
                pos = i;
                break;
            }
        }
        for( int i = 0; i < files.length; i++ ){
            if( ptn.Load( files[i].getAbsolutePath() ) ){
                try {
                    if( pos >= 0 ){
                        seq_.add( pos + i, ptn.clone() );
                    }
                    else
                        seq_.add( ptn.clone() );
                } catch (CloneNotSupportedException ex) {
                    Logger.getLogger(SequenceDesignPanel.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        if( pos >= 0 ){
            selectOne( pos );
            selectTo( pos + files.length - 1 );
        }
        else{
            selectOne( seq_.size() - files.length );
            selectTo( seq_.size() - 1 );
        }
            
        updateButtons();
        repaint();
    }
    public void setMasterMode( boolean m )
    {
        masterMode_ = m;
        updateButtons();
    }
    public boolean getMasterMode()
    {
        return masterMode_;
    }
    
    public void setPatternPeriod( int period )
    {
        patternPeriod_ = period;
    }
    public int getPatternPeriod()
    {
        return patternPeriod_;
    }
    
    public void setLoopCount( int count )
    {
        loopCount_ = count;
    }
    public int getLoopCount()
    {
        return loopCount_;
    }
    
    public void setPositiveEdgeTrigger( boolean b )
    {
        positiveEdgeTrigger_ = b;
    }
    public boolean getPositiveEdgeTrigger()
    {
        return positiveEdgeTrigger_;
    }
    
    private void allocateSelectionFlags()
    {
        if( seq_.size() < 1 ){
            lastSelectedIdx_ = -1;
            return;
        }

        if( selected_ != null && selected_.length == seq_.size() )
            return;
        
        boolean [] flags = new boolean[seq_.size()];
        if( selected_ != null ){
            int n = Math.min( seq_.size(), selected_.length );
            System.arraycopy( selected_, 0, flags, 0, n);
        }
        
        selected_ = flags;
    }
    private boolean getSelectFlag( int n )
    {
        if( selected_ == null )
            return false;
        else if( n >=0 && n < selected_.length )
            return selected_[ n ];
        else
            return false;
    }
    private boolean IsTherePatternSelected()
    {
        if( selected_ == null )
            return false;
        
        for( int i = 0; i < selected_.length; i++ ){
            if( selected_[i] )
                return true;
        }
        return false;
    }
    private void clearSelection()
    {
        allocateSelectionFlags();
        if( selected_ != null ){
            for( int i = 0; i < selected_.length; i++ )
                selected_[i] = false;
        }
        updateButtons();
    }
    private void selectOne( int n )
    {
        clearSelection();
        if( selected_ != null ){
            lastSelectedIdx_ = n;
            selected_[ n ] = true;
        }
        updateButtons();
    }
    private void swapSelection( int n )
    {
        allocateSelectionFlags();
        if( selected_ != null ){
            lastSelectedIdx_ = n;
            selected_[ n ] = !selected_[n];
        }
        updateButtons();
    }
    private void selectTo( int n )
    {
        if( lastSelectedIdx_ >=0 && lastSelectedIdx_ < seq_.size() ){
            clearSelection();
            int start = Math.min( lastSelectedIdx_ , n );
            int end = Math.max( lastSelectedIdx_ , n );
            for( int i = start; i <= end; i++)
                selected_[ i ] = true;
            lastSelectedIdx_ = n;
            updateButtons();
        }
        else
            selectOne( n );
    }
    private void addSelectTo( int n )
    {
        if( lastSelectedIdx_ >=0 && lastSelectedIdx_ < seq_.size() ){
            allocateSelectionFlags();
            int start = Math.min( lastSelectedIdx_ , n );
            int end = Math.max( lastSelectedIdx_ , n );
            for( int i = start; i <= end; i++)
                selected_[ i ] = true;
            lastSelectedIdx_ = n;
        }
        else
            swapSelection( n );
        updateButtons();
    }
    
    private void getPatternRectangle( int idx, Rectangle2D.Float r )
    {
        int ppr = getWidth() / GridWidth;
        r.width = GridWidth;
        r.height = GridHeight;
        r.x = idx % ppr * GridWidth;
        r.y = idx / ppr * GridHeight;
    }
    private void getPatternImageRectangle( int idx, Rectangle2D.Float r )
    {
        int ppr = getWidth() / GridWidth;
        r.width = GridWidth - Edge * 2;
        r.height = GridHeight - Edge - TitleHeight;
        r.x = idx % ppr * GridWidth + Edge;
        r.y = idx / ppr * GridHeight + TitleHeight;
    }
    
    public void updateScrollBar()
    {
        if( seq_.size() == 0 ){
            sb_.setValue(0);
            sb_.setMaximum(0);
            return;
        }
        
        int ppr = getWidth() / GridWidth;
        int ppc = getHeight() / GridHeight;
        System.out.println( "Panel Width = " + getWidth() + ", height = " + getHeight() );
        int n = ppr * ppc;
        
        int max = Math.max( 0, seq_.size() + 1);
        int start = sb_.getValue();
        if( start > max )
            start = max;
        
        if( start <= sb_.getMaximum() ){
            sb_.setValue( start );
            sb_.setMaximum( max );
        }
        else{
            sb_.setMaximum( max );
            sb_.setValue( start );
        }
        sb_.setVisibleAmount( n );
        sb_.repaint();
    }
    
    public void DeleteSelected()
    {
        boolean f = false;
    
        for( int i = seq_.size() - 1; i >=0; i-- ){
            if( getSelectFlag( i ) ){
                f = true;
                seq_.remove( i );
            }
        }
        if( f ){
            clearSelection();
            repaint();
        }
    }
    
    public void DeleteAll()
    {
        if( seq_.size() > 0 ){
            seq_.clear();
            clearSelection();
            repaint();
        }
    }
    
    private void MoveSelectedTo( int n )
    {
        if( n > seq_.size() )
            n = seq_.size();
        
        int numberBeforeTarget = 0;
        for( int i = 0; i < n; i++ ){
            if( selected_[i] )
                numberBeforeTarget ++;
        }
        ArrayList selectedPatterns = new ArrayList();
        for( int i = seq_.size() - 1; i >= 0; i-- ){
            if( selected_[i] )
                selectedPatterns.add( seq_.remove( i ) );
        }
        
        n -= numberBeforeTarget;
        for (Object selectedPattern : selectedPatterns) {
            seq_.add(n, selectedPattern);
        }
        
        if( selectedPatterns.size() > 0 && selectedPatterns.size() < seq_.size() ){
            selectOne( n );
            selectTo( n + selectedPatterns.size() - 1 );
            repaint();
        }
    }
    public void MoveTo( int n )
    {
        if( getSelectFlag(n) ){
            JOptionPane.showMessageDialog(this, "Selected patterns could not be moved before one on them. They can be moved before a unselected one.", "Move To failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        MoveSelectedTo( n );
    }

    public void Start()
    {
        if( sessionThd_ == null || !sessionThd_.isAlive() ) {
            sessionThd_ = new SessionThread( slm_, mapping_, seq_, aoi_ );
            sessionThd_.setMasterMode( masterMode_ );
            sessionThd_.setMasterModeParameters( patternPeriod_, loopCount_ );
            sessionThd_.setSlaveModeParameters( positiveEdgeTrigger_ );
            sessionThd_.start();
            updateButtons();
        }
    }
    
    public void Stop()
    {
        while( sessionThd_ != null && sessionThd_.isAlive() ) {
            sessionThd_.pleaseStop();
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Logger.getLogger(SequenceDesignPanel.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        slm_.stopSequence();
        updateButtons();
    }

    public boolean IsSessionAlive()
    {
        return sessionThd_ != null && sessionThd_.isAlive();
    }
    
    @Override
    public void paint(Graphics g)
    {
        int ppr = getWidth() / GridWidth;
        int ppc = getHeight() / GridHeight;
        int n = ppr * ppc;
        
        Font f = new Font( Font.SANS_SERIF, 3, TitleHeight - Edge * 2 );
        g.setFont( f );
        g.clearRect(0, 0, getWidth(), getHeight());
        
        if( sb_ != null && seq_ != null ){
            int start = sb_.getValue();
            int cnt = Math.min( n, seq_.size() - start );

            Rectangle2D.Float r = new Rectangle2D.Float();
            for( int i = 0; i < cnt; i++ ){
                if( getSelectFlag( start + i ) ){
                    getPatternRectangle( i, r );
                    g.setColor( Color.GREEN );
                    g.fillRect( (int)r.x, (int)r.y, (int)r.width, (int)r.height );
                }
                getPatternImageRectangle( i, r );
                Pattern p = (Pattern)seq_.get( start + i );
                p.draw( g, r );
                String s = String.format( "%d", start + i + 1 );
                g.setColor(Color.red);
                g.drawString( s, i % ppr * GridWidth + Edge, i / ppr * GridHeight + TitleHeight - Edge );
            }
        }
        
        g.setColor( Color.BLACK );
        for( int i = 0; i <= ppr; i++ ){
            int x = GridWidth * i;
            g.drawLine(x, 0, x, GridHeight * ppc );
        }
        for( int i = 0; i <= ppc; i++){
            int y = GridHeight * i;
            g.drawLine(0, y, GridWidth * ppr, y);
        }
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                formMouseDragged(evt);
            }
        });
        addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                formMouseWheelMoved(evt);
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                formMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                formMouseReleased(evt);
            }
        });
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        updateScrollBar();
        repaint();
    }//GEN-LAST:event_formComponentResized

    private void formMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMousePressed
        int ppr = getWidth() / GridWidth;
        int ppc = getHeight() / GridHeight;
        int n = ppr * ppc;
        
        Point pt = evt.getPoint();
        
        int selectedIdx = -1;
        
        Rectangle2D.Float r = new Rectangle2D.Float();
        for( int i = 0; i < n; i++ ){
            getPatternRectangle( i, r );
            if( r.contains( pt.x, pt.y ) ){
                selectedIdx = i + sb_.getValue();
                break;
            }
        }
        if( selectedIdx <0 || selectedIdx >= seq_.size() )
            clearSelection();
        else if( evt.getClickCount() > 1 ){
            try {
                form_.editPattern( ((Pattern) seq_.get( selectedIdx ) ).clone() );
            }
            catch( Exception ex ){
                
            }
        }
        else{
            if( evt.isControlDown() ){
                if( evt.isShiftDown() )
                    addSelectTo( selectedIdx );
                else
                    swapSelection( selectedIdx );
            }
            else if( evt.isShiftDown() )
                selectTo( selectedIdx );
            else
                selectOne( selectedIdx );
        }
        repaint();
        

    }//GEN-LAST:event_formMousePressed

    private void formMouseDragged(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseDragged
        dragging_ = true;
        setCursor( Cursor.getPredefinedCursor( Cursor.MOVE_CURSOR ) );
    }//GEN-LAST:event_formMouseDragged

    private void formMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_formMouseReleased
        setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) );
        if( !dragging_ )
            return;
        dragging_ = false;
        
        int ppr = getWidth() / GridWidth;
        int ppc = getHeight() / GridHeight;
        int n = ppr * ppc;
        
        Point pt = evt.getPoint();
        
        int target = -1;
        
        Rectangle2D.Float r = new Rectangle2D.Float();
        for( int i = 0; i < n; i++ ){
            getPatternRectangle( i, r );
            if( r.contains( pt.x, pt.y ) ){
                target = i + sb_.getValue();
                break;
            }
        }
        if( target > seq_.size() )
            target = seq_.size();
        
        if( target >= 0 && !getSelectFlag( target ) )
            MoveSelectedTo( target );
    }//GEN-LAST:event_formMouseReleased

    private void formMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_formMouseWheelMoved
        int ppr = getWidth() / GridWidth;
        int ppc = getHeight() / GridHeight;
        int n = ppr * ppc;
        
        int value = sb_.getValue() + evt.getWheelRotation() * ppr;
        if( value > sb_.getMaximum() - n + 1 )
            value = sb_.getMaximum() - n + 1;
        if( value < 0 )
            value = 0;
        sb_.setValue( value  );

        System.out.println( " evt.getButton() = " + evt.getButton() + ", BUTTON1 = " + MouseEvent.BUTTON1 );
        if( SwingUtilities.isLeftMouseButton(evt) ){
            dragging_ = true;
            setCursor( Cursor.getPredefinedCursor( Cursor.MOVE_CURSOR ) );
        }
        
        repaint();
    }//GEN-LAST:event_formMouseWheelMoved


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
