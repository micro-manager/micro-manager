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

    public MicrosceneryContext(Studio studio) {
        mmCon = new MMConnection(studio.core());
        micromanagerWrapper = new MicromanagerWrapper(mmCon,200,false);
        server = new RemoteMicroscopeServer(micromanagerWrapper, zContext,new SliceStorage(mmCon.getHeight()*mmCon.getWidth()*500));
        msSettings  = Util.getMicroscenerySettings();
    }
}
