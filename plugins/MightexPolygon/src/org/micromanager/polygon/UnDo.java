///////////////////////////////////////////////////////////////////////////////
// FILE:          UnDo.java
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

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author Wayne
 */
public class UnDo {
    private int first_;
    private int last_;
    private int state_;
    
    private File path_;
    
    private LoadSave ls_;
    
    public boolean create( LoadSave ls )
    {
        ls_ = ls;
        first_ = last_ = state_ = 0;
        path_ = Files.createTempDir();
        return path_.exists();
    }
    
    public void destory()
    {
        if( path_ != null ){
            Utility.DeleteFolder( path_ );
            path_ = null;
        }
    }
    
    public boolean CanUnDo()
    {
        return first_ != state_;
    }
    
    public boolean CanReDo()
    {
        return first_ != last_ && state_ != last_ -1;
    }
    
    public boolean UnDo()
    {
        if( !CanUnDo() )
            return false;
        return LoadState( state_ - 1 );
    }
    
    public boolean ReDo()
    {
        if( !CanReDo() )
            return false;
        return LoadState( state_ + 1 );
    }
    
    public boolean SaveState()
    {
        if( CanReDo() )
            DupState();
        return SaveState( last_ );
    }
    
    private boolean SaveState( int state )
    {
        String fn = path_ + "\\" + state + ".undo";
        if( !ls_.Save( fn ) )
            return false;
        
        state_ = state;
        last_ = state_ + 1;
        return true;
    }
    
    private boolean LoadState( int state )
    {
        String fn = path_ + "\\" + state + ".undo";
        if( !ls_.Load( fn ) )
            return false;
        
        state_ = state;
        return true;
    }
    
    private boolean DupState()
    {
        String fn = path_ + "\\" + state_ + ".undo";
        String fn2 = path_ + "\\" + last_ + ".undo";
        try{
            Files.copy( new File(fn), new File(fn2) );
            last_++;
            return true;
        }
        catch( IOException ex ){
            return false;
        }
    }
}
