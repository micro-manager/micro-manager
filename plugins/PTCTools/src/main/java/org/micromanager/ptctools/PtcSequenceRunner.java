package org.micromanager.ptctools;

import javax.swing.JLabel;

/**
 * Simple interface, used to pass a function to another function (in Executor)
 *
 * @author Nico
 */
public interface PtcSequenceRunner {
  public void doSequence(JLabel resultLabel);
}
