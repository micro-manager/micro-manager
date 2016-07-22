///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.plugins.magellan.misc;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;

/**
 *
 * @author Henry
 */
public class ExactlyOneRowSelectionModel extends DefaultListSelectionModel {

    public ExactlyOneRowSelectionModel () {
       super();
       setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
       this.setSelectionInterval(0, 0);
    }

    @Override
    public void clearSelection() {
    }

    @Override
    public void removeSelectionInterval(int index0, int index1) {
    }

}
