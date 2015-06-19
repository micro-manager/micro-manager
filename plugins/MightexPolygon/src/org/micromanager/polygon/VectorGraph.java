///////////////////////////////////////////////////////////////////////////////
// FILE:          VectorGraph.java
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
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

/**
 *
 * @author Wayne
 */
public class VectorGraph implements java.io.Serializable {
    
    private final ArrayList elements_;

    public VectorGraph()
    {
        elements_ = new ArrayList();
    }
    
    public int getCount()
    {
        return elements_.size();
    }
    VectorGraphElement getVectorGraphElement( int idx )
    {
        return (VectorGraphElement)elements_.get(idx);
    }
    void setVectorGraphElement( int idx, VectorGraphElement e )
    {
        elements_.set( idx, e );
    }
    
    public void add( VectorGraphElement e )
    {
        elements_.add( e );
    }
    public void remove( int idx )
    {
        elements_.remove( idx );
    }
    public void clear()
    {
        elements_.clear();
    }
    
    public void draw( Graphics g, Rectangle2D.Float r )
    {
        for( Object e : elements_){
            ((VectorGraphElement)e).draw( g, r,  false );
        }
    }
    
    public int select( Point pt, Rectangle2D.Float r )
    {
        for( int i = elements_.size() - 1; i >= 0; i-- ){
            VectorGraphElement e = (VectorGraphElement) elements_.get(i);
            if( e.isSelected( pt, r ) )
                return i;
        }
        return -1;
    }
    
    
    @Override
    public VectorGraph clone() throws CloneNotSupportedException
    {
        VectorGraph vg = new VectorGraph();
        for( Object e : elements_ ){
            vg.add( ((VectorGraphElement)e).clone() );
        }
        return vg;
    }
    
}
