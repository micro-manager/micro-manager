package org.micromanager.plugins.micromanager;

import fromScenery.Settings;
import microscenery.Util;
import microscenery.hardware.micromanagerConnection.MMConnection;
import microscenery.hardware.micromanagerConnection.MicromanagerWrapper;
import microscenery.network.RemoteMicroscopeServer;
import microscenery.network.SliceStorage;
import org.micromanager.Studio;
import org.zeromq.ZContext;

public class MicrosceneryContext {
    public final ZContext zContext = new ZContext();
    public final RemoteMicroscopeServer server;
    public final Settings msSettings;
    public final MMConnection mmCon;
    public final MicromanagerWrapper micromanagerWrapper;
    final EventListener eventListener;

    public MicrosceneryContext(Studio studio) {
        mmCon = new MMConnection(studio.core());
        try {
            micromanagerWrapper = new MicromanagerWrapper(mmCon, new MMStudioConnector(studio),200,false);
        } catch (IllegalStateException e){
            studio.alerts().postAlert("Microscoscenery: Illegal state", null, e.getMessage());
            throw e;
        }
        server = new RemoteMicroscopeServer(micromanagerWrapper, zContext,new SliceStorage(mmCon.getHeight()*mmCon.getWidth()*500));
        msSettings  = Util.getMicroscenerySettings();
        eventListener = new EventListener(studio, micromanagerWrapper);
    }
}
