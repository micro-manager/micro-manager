///////////////////////////////////////////////////////////////////////////////
// FILE:          VGPolygon.java
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
import static java.awt.Cursor.CROSSHAIR_CURSOR;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import static org.micromanager.polygon.VectorGraphElement.KeyPointRadius;

/**
 *
 * @author Wayne
 */
public class VGPolygon implements VectorGraphElement {
    private Color color_;
    private final ArrayList pts_ = new ArrayList();
    
    // color
    @Override
    public void setColor( Color color ) { color_ = color; }
    
    // width
    @Override
    public void setWidth( float width ) {}
    
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
        if( pts_.size() > 2 && pts_.get(0).equals( pts_.get( pts_.size() - 1)))
            pts_.remove( pts_.size() - 1 );
    }
    
    // compare
    @Override
    public boolean isSameAs( VectorGraphElement vg )
    {
        if( vg instanceof VGPolyline ){
            VGPolygon e = ( VGPolygon ) vg;
            if( e.color_ != color_ )
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
        VGPolygon e = new VGPolygon();
        e.color_ = color_;
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
        Graphics2D g2 = ( Graphics2D ) g;
        g2.setColor( color_ );

        if( pts_.size() < 2 )
            return;
        else if( pts_.size() == 2 ){
            Point2D.Float pt1 = (Point2D.Float) pts_.get(0);
            Point2D.Float pt2 = (Point2D.Float) pts_.get(1);
            float x1 = r.x + pt1.x * r.width;
            float y1 = r.y + pt1.y * r.height;
            float x2 = r.x + pt2.x * r.width;
            float y2 = r.y + pt2.y * r.height;
            g2.drawLine( (int) x1, (int) y1, (int) x2, (int) y2 );
        }
        else{
            Polygon p = new Polygon();
            Point2D.Float pt;
            float x, y;
            for( Object obj : pts_ ){
                pt = (Point2D.Float) obj;
                x = r.x + pt.x * r.width;
                y = r.y + pt.y * r.height;
                p.addPoint( (int)x, (int)y );
            }
            pt = (Point2D.Float) pts_.get(0);
            x = r.x + pt.x * r.width;
            y = r.y + pt.y * r.height;
            p.addPoint( (int)x, (int)y );

            g2.fill( p );
        }
        
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
        if( pts_.size() < 3 )
            return false;
        
        Polygon plg = new Polygon();
        for( Object obj : pts_ ){
            Point2D.Float p = (Point2D.Float) obj;
            float x = r.x + p.x * r.width;
            float y = r.y + p.y * r.height;
            plg.addPoint( (int)x, (int) y );
        }
/*        Point2D.Float p = (Point2D.Float) pts_.get(0);
        float x = r.x + p.x * r.width;
        float y = r.y + p.y * r.height;
        plg.addPoint( (int)x, (int) y );
*/        
        return plg.contains( pt );
/*        Point2D.Float p = (Point2D.Float) pts_.get(0);
        float x = r.x + p.x * r.width;
        float y = r.y + p.y * r.height;
        Point2D.Float pt1 = new Point2D.Float(), pt2 = new Point2D.Float( x, y );
        for( int i = 1; i < pts_.size(); i++ ){
            p = (Point2D.Float) pts_.get( i );
            x = r.x + p.x * r.width;
            y = r.y + p.y * r.height;
            pt1.x = pt2.x;
            pt1.y = pt2.y;
            pt2.x = x;
            pt2.y = y;
            if( VectorGraphUtility.IsLineSelected( pt, pt1, pt2 ))
                return true;
        }
        p = (Point2D.Float) pts_.get( 0 );
        x = r.x + p.x * r.width;
        y = r.y + p.y * r.height;
        pt1.x = x;
        pt1.y = y;
        return VectorGraphUtility.IsLineSelected( pt, pt1, pt2 );*/
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
