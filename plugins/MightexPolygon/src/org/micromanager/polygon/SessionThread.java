///////////////////////////////////////////////////////////////////////////////
// FILE:          SessionThread.java
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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Wayne
 */
public class SessionThread extends Thread {
    private final SLM slm_;
    private final Mapping mapping_;
    private final ArrayList seq_;
    private final Rectangle aoi_;
    
    private boolean masterMode_;
    private int patternPeriod_;
    private int loopCount_;
    private boolean positiveEdgeTrigger_;
    
    private boolean stop_;
    
    SessionThread(SLM slm, Mapping mapping, ArrayList seq, Rectangle aoi)
    {
        slm_ = slm;
        mapping_ = mapping;
        seq_ = seq;
        aoi_ = aoi;
    }
    
    public void setMasterMode( boolean b )
    {
        masterMode_ = b;
    }
    
    public void setMasterModeParameters( int period, int loops )
    {
        patternPeriod_ = period;
        loopCount_ = loops;
    }
    
    public void setSlaveModeParameters( boolean positiveEdgeTrigger )
    {
        positiveEdgeTrigger_ = positiveEdgeTrigger;
    }
    
    @Override
    public void run()
    {
        LinkedList<byte[]> patterns = new LinkedList<byte[]>();

        Rectangle2D.Float aoi2D = new Rectangle2D.Float( aoi_.x, aoi_.y, aoi_.width, aoi_.height );
        for (Object obj : seq_) {
            Pattern ptn = (Pattern) obj;
            BufferedImage bi = new BufferedImage( slm_.getWidth(), slm_.getHeight(), BufferedImage.TYPE_BYTE_GRAY );
            Graphics2D g = bi.createGraphics();
            g.setTransform( mapping_.getCameraToPolygonAffineTransform() );
            g.drawImage( ptn.bi, aoi_.x, aoi_.y, aoi_.width, aoi_.height, null );
            ptn.vg.draw( g, aoi2D );
            DataBufferByte dbb = (DataBufferByte)bi.getRaster().getDataBuffer();
            patterns.add( dbb.getData() );
            if( stop_ )
                return;
        }
        slm_.setTriggerType( masterMode_ ? 0 : ( positiveEdgeTrigger_ ? 2 : 3 ) );
        slm_.loadSequence( patterns );
        if( stop_ )
            return;
        slm_.startSequence();
        if( stop_ )
            return;
        if( masterMode_ ){
            for( int i = 0; i < seq_.size() * loopCount_; i++ ){
                try {
                    slm_.commandTrigger();
                    Thread.sleep( patternPeriod_ );
                } catch (Exception ex) {
                    Logger.getLogger(SessionThread.class.getName()).log(Level.SEVERE, null, ex);
                }
                if( stop_ )
                    break;
            }
            slm_.stopSequence();
        }
    }
    
    public void pleaseStop()
    {
        stop_ = true;
    }
}
