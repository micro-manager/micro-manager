/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bdvcompatibility;

import acq.MultiResMultipageTiffStorage;
import bdv.AbstractViewerImgLoader;
import bdv.img.cache.Cache;
import bdv.img.cache.CacheHints;
import bdv.img.cache.CachedCellImg;
import bdv.img.cache.LoadingStrategy;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.cache.VolatileImgCells;
import bdv.img.cache.VolatileImgCells.CellCache;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Fraction;

/**
 *
 * @author Henry
 */
public class MagellanImgLoader extends AbstractViewerImgLoader<UnsignedByteType, VolatileUnsignedByteType> {

   private final VolatileGlobalCellCache< VolatileByteArray > bdvCache_;
   private MultiResMultipageTiffStorage tiffStorage_;
   private final double[][] mipmapResolutions_;
   private final long[][] imageDimensions_;
   private final AffineTransform3D[] mipmapTransforms_;

   public MagellanImgLoader(MultiResMultipageTiffStorage tiffStorage) {
     super(new UnsignedByteType(), new VolatileUnsignedByteType());
      tiffStorage_ = tiffStorage;
      //CacheArrayLoader<A> loader, 
      //int maxNumTimepoints
      //int maxNumSetups -- Setup = channel in our context
      //int maxNumLevels -- maximum number of resolution levels defined later in constructor
      //int numFetcherThreads
      int numResLevels = tiffStorage_.getNumResLevels();
      long fullResHeight = tiffStorage_.getTileHeight() * tiffStorage_.getNumRows();
      long fullResWidth = tiffStorage_.getTileWidth() * tiffStorage_.getNumCols();
      
      bdvCache_ = new VolatileGlobalCellCache<VolatileByteArray>(
              new MultiResMPTiffVolatileByteArrayLoader(tiffStorage),
              tiffStorage.getNumFrames(), tiffStorage.getNumChannels(), numResLevels, 10);
      
      mipmapResolutions_ = new double[numResLevels][]; //esentially x, y z pixel sizes, specific to resolution level
      imageDimensions_ = new long[ numResLevels ][];
      mipmapTransforms_ = new AffineTransform3D[ numResLevels ];

      for (int resLevelIndex = 0; resLevelIndex < numResLevels; resLevelIndex++)  {
         double xySize = tiffStorage_.getPixelSizeXY() * Math.pow(2, resLevelIndex);
         double zSize = tiffStorage_.getPixelSizeZ(); //doesn't cahnge since no downsampling of Z
         
          mipmapResolutions_[resLevelIndex] = new double[]{ xySize, xySize, zSize }; 
          //TODO: this only works for fixed area, not explore acquisitions at the moment          
          imageDimensions_[resLevelIndex] = new long[]{ fullResWidth >> resLevelIndex, fullResHeight >> resLevelIndex, tiffStorage_.getMaxSliceIndex() };
         
         final AffineTransform3D mipmapTransform = new AffineTransform3D();
         //Affine transform diagonal (e.g. pixel sizes)
         //value, row, col
         mipmapTransform.set(xySize, 0, 0);
         mipmapTransform.set(xySize, 1, 1);
         mipmapTransform.set(zSize, 2, 2);

         //Affine transform 4th colum (offsets)
         //TODO: add negative pixel offset for explore acquisitions
         mipmapTransform.set(0, 0, 3);
         mipmapTransform.set(0, 1, 3);
         mipmapTransform.set(0, 2, 3);
         
         mipmapTransforms_[resLevelIndex] = mipmapTransform;
      }
   }

   private < T extends NativeType<T>> CachedCellImg< T, VolatileByteArray> prepareCachedImage(final ViewId view, final int resLevelIndex, final LoadingStrategy loadingStrategy) {
      final long[] dimensions = imageDimensions_[resLevelIndex];
      //the only “shape” of block that your CacheArrayLoader needs to be able to load (plus they will be aligned at multiples of tileWidth, tileHeight, 
      final int[] cellDimensions = new int[]{tiffStorage_.getTileWidth(), tiffStorage_.getTileHeight(), 1}; 

      final int priority = tiffStorage_.getNumResLevels() - 1 - resLevelIndex;
      final CacheHints cacheHints = new CacheHints(loadingStrategy, priority, false);
      final CellCache< VolatileByteArray> c = bdvCache_.new VolatileCellCache(view.getTimePointId(), view.getViewSetupId(), resLevelIndex, cacheHints);
      final VolatileImgCells< VolatileByteArray> cells = new VolatileImgCells< VolatileByteArray>(c, new Fraction(), dimensions, cellDimensions);
      final CachedCellImg< T, VolatileByteArray> img = new CachedCellImg< T, VolatileByteArray>(cells);
      return img;
   }

   @Override
   public RandomAccessibleInterval<UnsignedByteType> getImage(final ViewId view, final int level) {
      final CachedCellImg< UnsignedByteType, VolatileByteArray> img = prepareCachedImage(view, level, LoadingStrategy.BLOCKING);
      final UnsignedByteType linkedType = new UnsignedByteType(img);
      img.setLinkedType(linkedType);
      return img;
   }

   @Override
   public RandomAccessibleInterval<VolatileUnsignedByteType> getVolatileImage(final ViewId view, final int level) {
      final CachedCellImg< VolatileUnsignedByteType, VolatileByteArray> img = prepareCachedImage(view, level, LoadingStrategy.VOLATILE);
      final VolatileUnsignedByteType linkedType = new VolatileUnsignedByteType(img);
      img.setLinkedType(linkedType);
      return img;
   }

   @Override
   public double[][] getMipmapResolutions(final int setup) {
      return mipmapResolutions_;
   }

   @Override
   public net.imglib2.realtransform.AffineTransform3D[] getMipmapTransforms(final int setup) {
      return mipmapTransforms_;
   }

   @Override
   public int numMipmapLevels(final int setup) {
     return tiffStorage_.getNumResLevels();
   }

   @Override
   public Cache getCache() {
      return bdvCache_;
   }

}
