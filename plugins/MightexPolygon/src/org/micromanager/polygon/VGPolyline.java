///////////////////////////////////////////////////////////////////////////////
// FILE:          VGPolyline.java
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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import static java.awt.Cursor.CROSSHAIR_CURSOR;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import static org.micromanager.polygon.VectorGraphElement.KeyPointRadius;

/**
 *
 * @author Wayne
 */
public class VGPolyline implements VectorGraphElement {
    private Color color_;
    private float width_;
    private final ArrayList pts_ = new ArrayList();
    
    // color
    @Override
    public void setColor( Color color ) { color_ = color; }
    
    // width
    @Override
    public void setWidth( float width ) { width_ = width; }
    
    // Points
    public int getPointCount()
    {
        return pts_.size();
    }
    public Point2D.Float getPoint( int idx )
    {
        if( idx < 0 || idx >= pts_.size() )
            throw new ArrayIndexOutOfBoundsException();
        return (Point2D.Float)pts_.get( idx );
    }
    public void setPoint( int idx, Point2D.Float pt )
    {
        if( idx < 0 || idx >= pts_.size() )
            pts_.add( pt );
        else
            pts_.set( idx, pt );
    }
    public void RemoveDuplicatePoints()
    {
        for( int i = pts_.size() - 1; i > 0; i-- ){
            if( pts_.get(i).equals(pts_.get(i-1)) )
                pts_.remove( i );
        }
    }

    // compare
    @Override
    public boolean isSameAs( VectorGraphElement vg )
    {
        if( vg instanceof VGPolyline ){
            VGPolyline e = ( VGPolyline ) vg;
            if( e.color_ != color_ || e.width_ != width_ )
                return false;
            for( int i = 0; i < pts_.size(); i++ )
                if( pts_.get( i ) != e.pts_.get( i ) )
                    return false;
            return true;
        }
        else
            return false;
    }

    // clone
    @Override
    public VectorGraphElement clone()
    {
        VGPolyline e = new VGPolyline();
        e.color_ = color_;
        e.width_ = width_;
        for (Object pt : pts_) {
            Point2D.Float p = (Point2D.Float) pt;
            e.pts_.add( new Point2D.Float( p.x, p.y ) );
        }
        return e;
    }

    // check if object is within range of view
    @Override
    public boolean isInRange()
    {
        for( Object pt : pts_ ){
            Point2D.Float p = (Point2D.Float) pt;
            if( p.x >= 0 && p.x <= 1 && p.y >= 0 && p.y <= 1 )
                return true;
        }
        return false;
    }

    // centre point
    @Override
    public Point2D.Float getCentre()
    {
        float x = 0, y = 0;
        for( Object pt : pts_ ){
            Point2D.Float p = (Point2D.Float) pt;
            x += p.x;
            y += p.y;
        }
        int n = pts_.size();
        if( n > 0 )
            return new Point2D.Float( x / n, y / n );
        else
            return new Point2D.Float( 0.5f, 0.5f );
    }

    // draw object in a rectangle
    @Override
    public void draw( Graphics g, Rectangle2D.Float r, boolean selected )
    {
        if( pts_.size() < 2 )
            return;

        Path2D.Float p2 = new Path2D.Float();
        boolean first = true;
        for( Object obj : pts_ ){
            Point2D.Float pt = (Point2D.Float) obj;
            float x = r.x + pt.x * r.width;
            float y = r.y + pt.y * r.height;
            if( first ){
                first = false;
                p2.moveTo( x, y );
            }
            else
                p2.lineTo( x, y );
        }
        
        Graphics2D g2 = ( Graphics2D ) g;
        float w = width_ * (float) Math.sqrt( r.width * r.height );      
        g2.setStroke( new BasicStroke( w ) );
        g2.setColor( color_ );
        g2.draw( p2 );
        g2.setStroke( new BasicStroke( 1 ) );
        
        if( selected ){
            g2.setColor( VectorGraphElement.SelectedColor );
            for( Object obj : pts_ ){
                Point2D.Float pt = (Point2D.Float) obj;
                float x = r.x + pt.x * r.width;
                float y = r.y + pt.y * r.height;
                g2.fill(new Rectangle2D.Float( x - KeyPointRadius, y - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            }
        }
    }

    // check if a mouse click selects the object
    @Override
    public boolean isSelected( Point pt, Rectangle2D.Float r )
    {
        Point2D.Float pt1, pt2 = null;
        for( Object obj : pts_ ){
            Point2D.Float p = (Point2D.Float) obj;
            float x = r.x + p.x * r.width;
            float y = r.y + p.y * r.height;
            pt1 = pt2;
            pt2 = new Point2D.Float( x, y );
            if( pt1 != null && VectorGraphUtility.IsLineSelected( pt, pt1, pt2 ))
                return true;
        }
        return false;
    }

    // key point selection
    @Override
    public int selectKeyPoint( Point pt, Rectangle2D.Float r )
    {
        for( int i = 0; i < pts_.size(); i++ ){
            Point2D.Float p = (Point2D.Float) pts_.get( i );
            float x = r.x + p.x * r.width;
            float y = r.y + p.y * r.height;
            if( VectorGraphUtility.IsKeyPointSelected( pt, new Point2D.Float( x, y ) ) )
                return i;
        }
        return -1;
    }
    @Override
    public Cursor getKeyPointCursor( int kpidx )
    {
        return Cursor.getPredefinedCursor( CROSSHAIR_CURSOR );
    }
    
    // move object
    @Override
    public void move( float xOfs, float yOfs )
    {
        for( Object obj : pts_ ){
            Point2D.Float p = (Point2D.Float) obj;
            p.x += xOfs;
            p.y += yOfs;
        }
    }
    
    // move key point
    @Override
    public void moveKeyPoint( int kpidx, Point2D.Float pt )
    {
        if( kpidx >= 0 && kpidx < pts_.size() ) {
            Point2D.Float p = (Point2D.Float)pts_.get( kpidx );
            p.x = pt.x;
            p.y = pt.y;
        }
        else
            throw new ArrayIndexOutOfBoundsException();
    }

}

