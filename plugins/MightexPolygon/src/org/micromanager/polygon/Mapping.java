///////////////////////////////////////////////////////////////////////////////
// FILE:          Mapping.java
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

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Wayne
 */
public class Mapping {
    private boolean valid_;
    AffineTransform at_;
    
    public Mapping()
    {
        valid_ = false;
    }
    
    public int SetMapping( Point p1, Point w1, Point p2, Point w2, Point p3, Point w3 )
    {
        if( p1.x == p2.x || p1.y == p2.y || w1.x == w2.x || w1.y == w2.y )
            return -1;

        try{
            AffineTransform at1 = getCameraToPolygonAffineTransform( p1, w1, p2, w2, false );
            AffineTransform at2 = getCameraToPolygonAffineTransform( p1, w1, p2, w2, true );

            Point2D wf = at1.inverseTransform(p3, null);
            int df = (int)( ( w3.x - wf.getX() ) * ( w3.x - wf.getX() ) + ( w3.y - wf.getY() ) * ( w3.y - wf.getY() ) );
        
            Point2D wt = at2.inverseTransform(p3, null);
            int dt = (int)( ( w3.x - wt.getX() ) * ( w3.x - wt.getX() ) + ( w3.y - wt.getY() ) * ( w3.y - wt.getY() ) );

            at_ = df >= dt ? at2 : at1;
            valid_ = true;

            Utility.LogMsg( "Mapping: square error = " + ( df >= dt ? dt : df ) );
            return df >= dt ? dt : df;
        }
        catch( Exception ex ){
            return -1;
        }
    }

    public Point W2P( int wx, int wy )
    {
        Point2D p = at_.transform( new Point2D.Float( wx, wy ), null);
        return new Point( (int)p.getX(), (int)p.getY() );
    }

    public Point P2W( int px, int py )
    {
        try {
            Point2D p = at_.inverseTransform( new Point2D.Float( px, py ), null );
            return new Point( (int)p.getX(), (int)p.getY() );
        } catch (NoninvertibleTransformException ex) {
            Logger.getLogger(Mapping.class.getName()).log(Level.SEVERE, null, ex);
            return new Point( 0, 0 );
        }
    }

    public boolean IsValid()
    {
        return valid_;
    }

    private AffineTransform getCameraToPolygonAffineTransform(Point p1, Point w1, Point p2, Point w2, boolean swapxy )
    {
        double m00, m01, m02, m10, m11, m12;
        if(!swapxy){
            m00 = (double) ( p2.x - p1.x ) / ( w2.x - w1.x );
            m01 = 0;
            m02 = p1.x - m00 * w1.x;
            m10 = 0;
            m11 = (double) ( p2.y - p1.y ) / ( w2.y - w1.y );
            m12 = p1.y - m11 * w1.y;
        }
        else{
            m00 = 0;
            m01 = (double) ( p2.x - p1.x ) / ( w2.y - w1.y );
            m02 = p1.x - m01 * w1.y;
            m10 = (double) ( p2.y - p1.y ) / ( w2.x - w1.x );
            m11 = 0;
            m12 = p1.y - m10 * w1.x;
        }
        return new AffineTransform( m00, m10, m01, m11, m02, m12 );
    }
    
    public AffineTransform getCameraToPolygonAffineTransform()
    {
        return at_; 
    }
}
