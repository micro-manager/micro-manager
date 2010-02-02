/**
* Copyright (c) 2005-2007 Vilppu Tuominen (vtuo@iki.fi)
* University of Tampere, Institute of Medical Technology
*
* http://iki.fi/vtuo/software/largemontage/
*
* This program is free software; you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation; either version 2 of the License, or (at your
* option) any later version.
*
* This program is distributed in the hope that it will be useful, but
* WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* General Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with this program; if not, write to the Free Software Foundation, Inc.,
* 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
*
*/

import java.io.File;

import javax.media.jai.RenderedOp;

/**
 * A composite class, which represents one image of the montage/mosaic. Each
 * <code>MapImage</code> has its own filename, operation node and defined
 * position in the montage (all share the same coordinate space).
 */
public class MapImage {
    
    private File file = null;
    private RenderedOp op = null;
    
    private int xTrans = 0;
    private int yTrans = 0;
    
    /** Constructor. */
    public MapImage(File file, RenderedOp op, int xTrans, int yTrans) {
        this.file = file;
        this.op = op;   
        this.xTrans = xTrans;
        this.yTrans = yTrans;
    }
    
    // --- DEFAULT ACCESSOR-METHODS -------------------------------------------
    
    public File getFile() {
        return file;
    }
    
    public void setFile(File file) {
        this.file = file;
    }
    
    public RenderedOp getOp() {
        return op;
    }
    
    public void setOp(RenderedOp op) {
        this.op = op;
    }
    
    public int getXtrans() {
        return xTrans;
    }
    
    public void setXtrans(int xTrans) {
        this.xTrans = xTrans;
    }
    
    public int getYtrans() {
        return yTrans;
    }
    
    public void setYtrans(int yTrans) {
        this.yTrans = yTrans;
    }
    
}

