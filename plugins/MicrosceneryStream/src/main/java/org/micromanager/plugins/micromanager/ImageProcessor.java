package org.micromanager.plugins.micromanager;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ImageProcessor implements Processor {
    private final MicrosceneryContext mmContext;

    public ImageProcessor(MicrosceneryContext mmContext) {
        this.mmContext = mmContext;
    }

    @Override
    public void processImage(Image image, ProcessorContext context) {
        Metadata meta = image.getMetadata();
        Vector3f pos = new Vector3f(
                meta.getXPositionUm().floatValue(),
                meta.getYPositionUm().floatValue(),
                meta.getZPositionUm().floatValue());

        ByteBuffer buf = MemoryUtil.memAlloc(
                mmContext.micromanagerWrapper.hardwareDimensions().getByteSize());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        short[] sa = (short[]) image.getRawPixels();
        buf.asShortBuffer().put(sa);

        mmContext.micromanagerWrapper.externalSnap(pos,buf);

        context.outputImage(image);
    }
}
