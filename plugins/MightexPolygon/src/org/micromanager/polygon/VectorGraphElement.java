///////////////////////////////////////////////////////////////////////////////
// FILE:          VectorGraphElement.java
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
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 *
 * @author Wayne
 */
public interface VectorGraphElement extends java.io.Serializable {
    public static final float KeyPointRadius = 2;
    public static final Color SelectedColor = Color.GREEN;

    // set color
    public void setColor( Color color );
    
    // set pen width
    public void setWidth( float width );
    
    // compare
    public boolean isSameAs( VectorGraphElement vg );
    
    // clone
    public VectorGraphElement clone();
    
    // check if object is within range of view
    public boolean isInRange();
    
    // centre point
    public Point2D.Float getCentre();
    
    // draw object in a rectangle
    public void draw( Graphics g, Rectangle2D.Float r, boolean selected );
    
    // check if a mouse click selects the object
    public boolean isSelected( Point pt, Rectangle2D.Float r );
    
    // key point selection
    public int selectKeyPoint( Point pt, Rectangle2D.Float r );
    public Cursor getKeyPointCursor( int kpidx );
    
    // move object
    public void move( float xOfs, float yOfs );
    
    // move key point
    public void moveKeyPoint( int kpidx, Point2D.Float pt );
    
}
