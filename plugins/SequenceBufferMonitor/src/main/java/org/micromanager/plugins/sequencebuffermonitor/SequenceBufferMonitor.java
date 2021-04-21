/**
 * SequenceBufferMonitor plugin
 *
 * <p>Display Core sequence buffer usage in real time.
 *
 * <p>AUTHOR: Mark Tsuchida COPYRIGHT: University of California, San Francisco, 2014 LICENSE: This
 * file is distributed under the BSD license. License text is included with the source distribution.
 *
 * <p>This file is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * <p>IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */
package org.micromanager.plugins.sequencebuffermonitor;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class SequenceBufferMonitor implements MenuPlugin, SciJavaPlugin {
  private Studio studio_;
  private SequenceBufferMonitorFrame frame_ = null;

  @Override
  public void setContext(Studio studio) {
    studio_ = studio;
  }

  @Override
  public String getSubMenu() {
    return "Developer Tools";
  }

  @Override
  public void onPluginSelected() {
    if (frame_ == null) {
      frame_ = new SequenceBufferMonitorFrame(studio_);
    }
    frame_.setVisible(true);
    frame_.start();
  }

  @Override
  public String getName() {
    return "Sequence Buffer Monitor";
  }

  @Override
  public String getHelpText() {
    return "Display sequence buffer usage";
  }

  @Override
  public String getVersion() {
    return "0.0";
  }

  @Override
  public String getCopyright() {
    return "University of California, San Francisco, 2014";
  }
}
