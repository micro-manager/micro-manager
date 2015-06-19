///////////////////////////////////////////////////////////////////////////////
// FILE:          VGLine.java
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
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 *
 * @author Wayne
 */
public class VGLine implements VectorGraphElement {
    private Color color_;
    private float width_;
    private Point2D.Float pt1_ = new Point2D.Float( 0, 0 );
    private Point2D.Float pt2_ = new Point2D.Float( 0, 0 );
    
    // color
    @Override
    public void setColor( Color color ) { color_ = color; }
    
    // width
    @Override
    public void setWidth( float width ) { width_ = width; }
    
    // Points
    public Point2D.Float getPoint( int idx )
    {
        if( idx < 0 || idx > 1 )
            throw new ArrayIndexOutOfBoundsException();
        return idx == 0 ? pt1_ : pt2_;
    }
    public void setPoint( int idx, Point2D.Float pt )
    {
        if( idx < 0 || idx > 1 )
            throw new ArrayIndexOutOfBoundsException();
        if( idx == 0 ){
            pt1_.x = pt.x;
            pt1_.y = pt.y;
        }
        else{
            pt2_.x = pt.x;
            pt2_.y = pt.y;
        }
    }
    
    // compare
    @Override
    public boolean isSameAs( VectorGraphElement vg )
    {
        if( vg instanceof VGLine ){
            VGLine line = ( VGLine ) vg;
            return line.color_ == color_ && line.width_ == width_ && line.pt1_ == pt1_ && line.pt2_ == pt2_;
        }
        else
            return false;
    }
    
    // clone
    @Override
    public VectorGraphElement clone()
    {
        VGLine line = new VGLine();
        line.color_ = color_;
        line.width_ = width_;
        line.pt1_ = (Point2D.Float)pt1_.clone();
        line.pt2_ = (Point2D.Float)pt2_.clone();
        return line;
    }
    
    // check if object is within range of view
    @Override
    public boolean isInRange()
    {
        return pt1_.x >=0 && pt1_.x <=1 && pt1_.y >=0 && pt1_.y <= 1 || pt2_.x >=0 && pt2_.x <=1 && pt2_.y >=0 && pt2_.y <= 1;
    }
    
    // centre point
    @Override
    public Point2D.Float getCentre()
    {
        return new Point2D.Float( ( pt1_.x + pt2_.x ) / 2, ( pt1_.y + pt2_.y ) / 2 );
    }
    
    // draw object in a rectangle
    @Override
    public void draw( Graphics g, Rectangle2D.Float r, boolean selected )
    {
        float x1 = r.x + pt1_.x * r.width;
        float y1 = r.y + pt1_.y * r.height;
        float x2 = r.x + pt2_.x * r.width;
        float y2 = r.y + pt2_.y * r.height;
        float w = width_ * (float) Math.sqrt( r.width * r.height );      
        Graphics2D g2 = ( Graphics2D ) g;
        g2.setStroke( new BasicStroke( w ) );
        g2.setColor( color_ );
        g2.draw( new Line2D.Float( x1, y1, x2, y2 ) );
        g2.setStroke( new BasicStroke( 1 ) );
        
        if( selected ){
            g2.setColor( VectorGraphElement.SelectedColor );
            g2.fill(new Rectangle2D.Float( x1 - KeyPointRadius, y1 - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill( new Rectangle2D.Float( x2 - KeyPointRadius, y2 - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
        }
    }
    
    // check if a mouse click selects the object
    @Override
    public boolean isSelected( Point pt, Rectangle2D.Float r )
    {
        Point2D.Float pt1 = new Point2D.Float( r.x + pt1_.x * r.width, r.y + pt1_.y * r.height );
        Point2D.Float pt2 = new Point2D.Float( r.x + pt2_.x * r.width, r.y + pt2_.y * r.height );
        return VectorGraphUtility.IsLineSelected( pt, pt1, pt2 );
    }
    
    // key point selection
    @Override
    public int selectKeyPoint( Point pt, Rectangle2D.Float r )
    {
        Point2D.Float pt1 = new Point2D.Float( r.x + pt1_.x * r.width, r.y + pt1_.y * r.height );
        Point2D.Float pt2 = new Point2D.Float( r.x + pt2_.x * r.width, r.y + pt2_.y * r.height );
        if( VectorGraphUtility.IsKeyPointSelected( pt, pt1) )
            return 0;
        if( VectorGraphUtility.IsKeyPointSelected( pt, pt2) )
            return 1;
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
        pt1_.x += xOfs;
        pt1_.y += yOfs;
        pt2_.x += xOfs;
        pt2_.y += yOfs;
    }
    
    // move key point
    @Override
    public void moveKeyPoint( int kpidx, Point2D.Float pt )
    {
        if( kpidx == 0 ){
            pt1_.x = pt.x;
            pt1_.y = pt.y;
        }
        else if( kpidx == 1 ){
            pt2_.x = pt.x;
            pt2_.y = pt.y;
        }
        else
            throw new ArrayIndexOutOfBoundsException();
    }
    
}
