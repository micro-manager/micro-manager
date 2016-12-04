package org.micromanager.plugins.positionsplitter;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.scijava.plugin.SciJavaPlugin;
import org.scijava.plugin.Plugin;

@Plugin(type = ProcessorPlugin.class)
public class PositionSplitterPlugin implements ProcessorPlugin, SciJavaPlugin {

        public static String menuName = "Position Splitter";
        public static String tooltipDescription = "Save multiple XY Positions MDA into separate files";
        public static String versionNumber = "1.0";
        public static String copyright = "Hadrien Mary";

        private Studio studio_;

        @Override
        public void setContext(Studio studio) {
                studio_ = studio;
        }

        @Override
        public ProcessorConfigurator createConfigurator(PropertyMap settings) {
                return new PositionSplitterConfigurator(settings, studio_);
        }

        @Override
        public ProcessorFactory createFactory(PropertyMap settings) {
                return new PositionSplitterFactory(studio_, settings);
        }

        @Override
        public String getName() {
                return menuName;
        }

        @Override
        public String getHelpText() {
                return tooltipDescription;
        }

        @Override
        public String getVersion() {
                return versionNumber;
        }

        @Override
        public String getCopyright() {
                return copyright;
        }
}
