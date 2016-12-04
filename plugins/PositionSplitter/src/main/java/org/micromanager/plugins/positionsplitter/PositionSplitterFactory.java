package org.micromanager.plugins.positionsplitter;

import org.micromanager.LogManager;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class PositionSplitterFactory implements ProcessorFactory {

        private final Studio studio_;
        private final PropertyMap settings_;
        private final LogManager log_;

        public PositionSplitterFactory(Studio studio, PropertyMap settings) {
                studio_ = studio;
                settings_ = settings;
                log_ = studio_.logs();
        }

        @Override
        public Processor createProcessor() {
                return new PositionSplitterProcessor(studio_);
        }
}
