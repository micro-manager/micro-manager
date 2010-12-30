/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.awt.Color;

/**
 *
 * @author arthur
 */
public class ChannelDisplaySettings {
   double gamma = 1.0;
   int min = Integer.MAX_VALUE;
   int max = Integer.MIN_VALUE;
   Color color = Color.white;
   int nbits;
}
