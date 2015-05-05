/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

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