///////////////////////////////////////////////////////////////////////////////
// FILE:          VGRectangle.java
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import static org.micromanager.polygon.VectorGraphElement.KeyPointRadius;

/**
 *
 * @author Wayne
 */
public class VGRectangle implements VectorGraphElement {
    private Color color_;
    private Point2D.Float pt1_ = new Point2D.Float( 0, 0 );
    private Point2D.Float pt2_ = new Point2D.Float( 0, 0 );
    
    // color
    @Override
    public void setColor( Color color ) { color_ = color; }
    
    // width
    @Override
    public void setWidth( float width ) {}
    
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
        if( vg instanceof VGRectangle ){
            VGRectangle rc = ( VGRectangle ) vg;
            return rc.color_ == color_ && rc.pt1_ == pt1_ && rc.pt2_ == pt2_;
        }
        else
            return false;
    }
    
    // clone
    @Override
    public VectorGraphElement clone()
    {
        VGRectangle vge = new VGRectangle();
        vge.color_ = color_;
        vge.pt1_ = (Point2D.Float)pt1_.clone();
        vge.pt2_ = (Point2D.Float)pt2_.clone();
        return vge;
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

        float left = Math.min( x1, x2 );
        float right = Math.max( x1, x2 );
        float top = Math.min( y1, y2 );
        float bottom = Math.max( y1, y2 );

        Graphics2D g2 = ( Graphics2D ) g;
        g2.setColor( color_ );
        g2.fill( new Rectangle2D.Float( left, top, right - left, bottom - top ) );
        
        if( selected ){
            g2.setColor( VectorGraphElement.SelectedColor );
            g2.fill(new Rectangle2D.Float( left - KeyPointRadius, top - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill(new Rectangle2D.Float( right - KeyPointRadius, top - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill(new Rectangle2D.Float( left - KeyPointRadius, bottom - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill(new Rectangle2D.Float( right - KeyPointRadius, bottom - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill(new Rectangle2D.Float( ( left + right ) / 2 - KeyPointRadius, top - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill(new Rectangle2D.Float( ( left + right ) / 2 - KeyPointRadius, bottom - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill(new Rectangle2D.Float( left - KeyPointRadius, ( top + bottom ) / 2 - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
            g2.fill(new Rectangle2D.Float( right - KeyPointRadius, ( top + bottom ) / 2 - KeyPointRadius, KeyPointRadius * 2, KeyPointRadius * 2 ) );
        }
    }
    
    // check if a mouse click selects the object
    @Override
    public boolean isSelected( Point pt, Rectangle2D.Float r )
    {
        float x1 = r.x + pt1_.x * r.width;
        float y1 = r.y + pt1_.y * r.height;
        float x2 = r.x + pt2_.x * r.width;
        float y2 = r.y + pt2_.y * r.height;
        Rectangle2D.Float rc = new Rectangle2D.Float();
        rc.x = Math.min( x1, x2 );
        rc.y = Math.min( y1, y2 );
        rc.width = Math.max( x1, x2 ) - rc.x;
        rc.height = Math.max( y1, y2 ) - rc.y;
        return rc.contains( pt.x, pt.y );
/*        Point2D.Float pt1 = new Point2D.Float( r.x + pt1_.x * r.width, r.y + pt1_.y * r.height );
        Point2D.Float pt3 = new Point2D.Float( r.x + pt2_.x * r.width, r.y + pt2_.y * r.height );
        Point2D.Float pt2 = new Point2D.Float( pt3.x, pt1.y );
        Point2D.Float pt4 = new Point2D.Float( pt1.x, pt3.y );
        return VectorGraphUtility.IsLineSelected( pt, pt1, pt2 ) ||
               VectorGraphUtility.IsLineSelected( pt, pt2, pt3 ) ||
               VectorGraphUtility.IsLineSelected( pt, pt3, pt4 ) ||
               VectorGraphUtility.IsLineSelected( pt, pt4, pt1 );*/
    }
    
    // key point selection
    @Override
    public int selectKeyPoint( Point pt, Rectangle2D.Float r )
    {
        Point2D.Float pt1 = new Point2D.Float( r.x + pt1_.x * r.width, r.y + pt1_.y * r.height );
        Point2D.Float pt2 = new Point2D.Float( r.x + pt2_.x * r.width, r.y + pt2_.y * r.height );
        if( VectorGraphUtility.IsKeyPointSelected( pt, pt1) )
            return 0;
        if( VectorGraphUtility.IsKeyPointSelected( pt, new Point2D.Float( ( pt1.x + pt2.x ) / 2, pt1.y ) ) )
            return 1;
        if( VectorGraphUtility.IsKeyPointSelected( pt, new Point2D.Float( pt2.x, pt1.y ) ) )
            return 2;
        if( VectorGraphUtility.IsKeyPointSelected( pt, new Point2D.Float( pt2.x, ( pt1.y + pt2.y ) / 2 ) ) )
            return 3;
        if( VectorGraphUtility.IsKeyPointSelected( pt, pt2) )
            return 4;
        if( VectorGraphUtility.IsKeyPointSelected( pt, new Point2D.Float( ( pt1.x + pt2.x ) / 2, pt2.y ) ) )
            return 5;
        if( VectorGraphUtility.IsKeyPointSelected( pt, new Point2D.Float( pt1.x, pt2.y ) ) )
            return 6;
        if( VectorGraphUtility.IsKeyPointSelected( pt, new Point2D.Float( pt1.x, ( pt1.y + pt2.y ) / 2 ) ) )
            return 7;
        
        return -1;
    }
    
    @Override
    public Cursor getKeyPointCursor( int kpidx )
    {
        boolean f = ( pt2_.x - pt1_.x ) * ( pt2_.y - pt1_.y ) >= 0;
        
        Cursor cur;
        switch( kpidx ){
            case 0:
            case 4:
                cur = f ? Cursor.getPredefinedCursor( Cursor.NW_RESIZE_CURSOR ) : Cursor.getPredefinedCursor( Cursor.NE_RESIZE_CURSOR );
                break;
            case 1:
            case 5:
                cur = Cursor.getPredefinedCursor( Cursor.N_RESIZE_CURSOR );
                break;
            case 2:
            case 6:
                cur = f ? Cursor.getPredefinedCursor( Cursor.NE_RESIZE_CURSOR ) : Cursor.getPredefinedCursor( Cursor.NW_RESIZE_CURSOR );
                break;
            case 3:
            case 7:
                cur = Cursor.getPredefinedCursor( Cursor.E_RESIZE_CURSOR );
                break;
            default:
                cur = null;
                break;
        }
        return cur;
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
        switch( kpidx ){
            case 0:
                pt1_.x = pt.x;
                pt1_.y = pt.y;
                break;
            case 1:
                pt1_.y = pt.y;
                break;
            case 2:
                pt2_.x = pt.x;
                pt1_.y = pt.y;
                break;
            case 3:
                pt2_.x = pt.x;
                break;
            case 4:
                pt2_.x = pt.x;
                pt2_.y = pt.y;
                break;
            case 5:
                pt2_.y = pt.y;
                break;
            case 6:
                pt1_.x = pt.x;
                pt2_.y = pt.y;
                break;
            case 7:
                pt1_.x = pt.x;
                break;
            default:
                throw new ArrayIndexOutOfBoundsException( "Key point index " + kpidx + " invalid" );
        }
    }
    
    
}
