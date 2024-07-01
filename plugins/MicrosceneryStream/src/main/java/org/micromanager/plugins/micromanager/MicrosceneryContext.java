package org.micromanager.plugins.micromanager;

import fromScenery.Settings;
import microscenery.Util;
import microscenery.hardware.micromanagerConnection.MMCoreConnector;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import microscenery.network.RemoteMicroscopeServer;
import org.micromanager.Studio;
import org.zeromq.ZContext;

public class MicrosceneryContext {
    public final ZContext zContext = new ZContext();
    public final RemoteMicroscopeServer server;
    public final Settings msSettings;
    public final MMCoreConnector mmCon;
    public final MicromanagerWrapper micromanagerWrapper;
    final EventListener eventListener;

    public MicrosceneryContext(Studio studio) {
        msSettings  = Util.getMicroscenerySettings();
        mmCon = new MMCoreConnector(studio.core());
        try {
            micromanagerWrapper = new MicromanagerWrapper(mmCon, new MMStudioConnector(studio, this));
        } catch (IllegalStateException e){
            studio.alerts().postAlert("Microscoscenery: Illegal state", null, e.getMessage());
            throw e;
        }
        server = new RemoteMicroscopeServer(micromanagerWrapper, zContext);
        eventListener = new EventListener(studio, micromanagerWrapper);
    }
}
