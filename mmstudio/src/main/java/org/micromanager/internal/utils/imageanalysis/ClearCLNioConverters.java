package org.micromanager.internal.utils.imageanalysis;

import clearcl.ClearCLBuffer;
import clearcl.ClearCLContext;
import clearcl.ClearCLImage;
import clearcl.enums.HostAccessType;
import clearcl.enums.ImageChannelDataType;
import clearcl.enums.ImageChannelOrder;
import clearcl.enums.KernelAccessType;
import clearcl.enums.MemAllocMode;
import clearcl.interfaces.ClearCLImageInterface;
import coremem.enums.NativeTypeEnum;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * @author nico
 */
public class ClearCLNioConverters {

   /**
    * convertNioTiClearCL.
    *
    * <p>Author: @nicost 6 2019
    *
    * @param context    ClearCLContext instance - the thing that knows about the GPU
    * @param source     Java Direct Buffer containing intensity (pixel) data
    * @param dimensions Dimensions of the image. Should be 2D or 3D (higher?) Mismatch
    *                   with the input data may be disastrous
    * @return ClearCLBuffer containing a copy of the input data
    */
   public static ClearCLBuffer convertNioTiClearCLBuffer(ClearCLContext context,
                                                         Buffer source,
                                                         long[] dimensions) {
      ClearCLBuffer target;
      NativeTypeEnum type = null;
      if (source instanceof ByteBuffer) {
         type = NativeTypeEnum.UnsignedByte;
      }
      else if (source instanceof ShortBuffer) {
         type = NativeTypeEnum.UnsignedShort;
      }
      else if (source instanceof FloatBuffer) {
         type = NativeTypeEnum.Float;
      } // Todo: other types, exception when type not found
      target = context.createBuffer(MemAllocMode.Best,
            HostAccessType.ReadWrite,
            KernelAccessType.ReadWrite,
            1L,
            type,
            dimensions);
      target.readFrom(source, true);

      return target;
   }

   public static ClearCLImage convertNioTiClearCLImage(ClearCLContext context,
                                                       Buffer source,
                                                       long[] dimensions) {
      ClearCLImage target;
      ImageChannelDataType type = null;
      if (source instanceof ByteBuffer) {
         type = ImageChannelDataType.UnsignedInt8;
      }
      else if (source instanceof ShortBuffer) {
         type = ImageChannelDataType.UnsignedInt16;
      }
      else if (source instanceof FloatBuffer) {
         type = ImageChannelDataType.Float;
      } // Todo: other types, exception when type not found
      target = context.createImage(HostAccessType.ReadWrite,
            KernelAccessType.ReadWrite,
            ImageChannelOrder.R,
            type,
            dimensions);
      target.readFrom(source, true);

      return target;
   }

   /**
    * Copies Intensity data from ClearCLBuffer to Java Direct Buffer Input is
    * either ClearCLBuffer or ClearCLImage Caller will also need dimensions:
    * source.getDimensions()
    *
    * @param source ClearCLBuffer to be convert to Java direct Buffer
    * @return Java Direct Buffer
    */
   public static Buffer convertClearCLBufferToNio(ClearCLImageInterface source) {
      Buffer buffer = null;
      if (null != source.getNativeType()) {
         switch (source.getNativeType()) {
            case UnsignedByte:
               buffer =
                     ByteBuffer.allocate((int) (source.getSizeInBytes()
                           / source.getNativeType()
                           .getSizeInBytes()));
               source.writeTo(buffer, true);
               break;
            case UnsignedShort:
               buffer =
                     ShortBuffer.allocate((int) (source.getSizeInBytes()
                           / source.getNativeType()
                           .getSizeInBytes()));
               source.writeTo(buffer, true);
               break;
            case Float:
               buffer =
                     FloatBuffer.allocate((int) (source.getSizeInBytes()
                           / source.getNativeType()
                           .getSizeInBytes()));
               source.writeTo(buffer, true);
               break;
            default:
               // Todo: other types, exception when type not found
               break;
         }
      }
      return buffer;
   }

}