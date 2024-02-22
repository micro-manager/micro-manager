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

    private long lastImage = 0;

    public ImageProcessor(MicrosceneryContext mmContext) {
        this.mmContext = mmContext;
    }

    @Override
    public void processImage(Image image, ProcessorContext context) {
        Metadata meta = image.getMetadata();

        float imgRate = mmContext.msSettings.get("Stream.imageRateLimitPerSec",Float.MAX_VALUE);
        long timeBetweenImages = (long) (1000 / imgRate);
        long now = System.currentTimeMillis();
        if (lastImage + timeBetweenImages > now){
            context.outputImage(image);
            return;
        }
        lastImage = now;

        String cam = mmContext.msSettings.get("Stream.Camera","any");
        if (!cam.equals("any") && !image.getMetadata().getCamera().equals(cam)){
            context.outputImage(image);
            return;
        }

        Vector3f pos = new Vector3f(
                meta.getXPositionUm().floatValue(),
                meta.getYPositionUm().floatValue(),
                meta.getZPositionUm().floatValue());

        ByteBuffer buf = MemoryUtil.memAlloc(image.getWidth()*image.getHeight()*image.getBytesPerPixel());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        switch (image.getBytesPerPixel() ){
            case 1:
                buf.put((byte[]) image.getRawPixels());
                buf.flip();
                break;
            case 2:
                short[] sa = (short[]) image.getRawPixels();
                buf.asShortBuffer().put(sa);
                break;
        }

        mmContext.micromanagerWrapper.externalSnap(pos,buf);

        context.outputImage(image);
    }
}
