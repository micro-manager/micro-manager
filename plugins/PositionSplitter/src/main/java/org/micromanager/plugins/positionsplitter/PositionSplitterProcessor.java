package org.micromanager.plugins.positionsplitter;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.LogManager;
import org.micromanager.MultiStagePosition;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.StorageRAM;

public class PositionSplitterProcessor extends Processor {

        private final Studio studio_;
        private final LogManager log_;
        private HashMap<String, Datastore> stores_;

        private boolean shouldDisplay = true;

        public PositionSplitterProcessor(Studio studio) {

                studio_ = studio;
                log_ = studio_.logs();
                stores_ = new HashMap<String, Datastore>();

        }

        @Override
        public void processImage(Image image, ProcessorContext context) {

                // when live mode is on do nothing
                if (studio_.live().getIsLiveModeOn()) {
                        context.outputImage(image);
                        return;
                }

                // Get the appropriate datastore
                String posName = image.getMetadata().getPositionName();
                Datastore store = stores_.get(posName);

                try {
                        store.putImage(image);
                } catch (DatastoreFrozenException ex) {
                        Logger.getLogger(PositionSplitterProcessor.class.getName()).log(Level.SEVERE, null, ex);
                } catch (DatastoreRewriteException ex) {
                        Logger.getLogger(PositionSplitterProcessor.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IllegalArgumentException ex) {
                        Logger.getLogger(PositionSplitterProcessor.class.getName()).log(Level.SEVERE, null, ex);
                }
        }

        @Override
        public SummaryMetadata processSummaryMetadata(SummaryMetadata summary) {

                // Create a DataStore for each different positions
                for (MultiStagePosition pos : summary.getStagePositions()) {

                        Datastore store = new DefaultDatastore();
                        store.setStorage(new StorageRAM(store));

                        MultiStagePosition[] stagePositions = {pos};
                        Coords coords = summary.getIntendedDimensions().copy().stagePosition(0).build();
                        SummaryMetadata newMetadata = summary.copy().intendedDimensions(coords).build();

                        try {
                                store.setSummaryMetadata(newMetadata);
                        } catch (DatastoreFrozenException ex) {
                                Logger.getLogger(PositionSplitterProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (DatastoreRewriteException ex) {
                                Logger.getLogger(PositionSplitterProcessor.class.getName()).log(Level.SEVERE, null, ex);
                        }

                        studio_.displays().manage(store);
                        if (shouldDisplay) {
                                studio_.displays().createDisplay(store);
                        }

                        stores_.put(pos.getLabel(), store);
                }

                return summary;
        }

        @Override
        public void cleanup(ProcessorContext context) {
                for (Datastore store : stores_.values()) {
                        store.freeze();
                }
        }

}
