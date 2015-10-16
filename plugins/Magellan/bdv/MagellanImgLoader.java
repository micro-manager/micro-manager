/////////////////////////////////////////////////////////////////////////////////
//// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
////
//// COPYRIGHT:    University of California, San Francisco, 2015
////
//// LICENSE:      This file is distributed under the BSD license.
////               License text is included with the source distribution.
////
////               This file is distributed in the hope that it will be useful,
////               but WITHOUT ANY WARRANTY; without even the implied warranty
////               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
////
////               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
////               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
////               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
////
//
//package bdv;
//
//import acq.MultiResMultipageTiffStorage;
//import bdv.AbstractViewerImgLoader;
//import bdv.img.cache.Cache;
//import bdv.img.cache.CacheArrayLoader;
//import bdv.img.cache.CacheHints;
//import bdv.img.cache.CachedCellImg;
//import bdv.img.cache.LoadingStrategy;
//import bdv.img.cache.VolatileGlobalCellCache;
//import bdv.img.cache.VolatileImgCells;
//import bdv.img.cache.VolatileImgCells.CellCache;
//import misc.MD;
//import mpicbg.spim.data.sequence.ViewId;
//import net.imglib2.RandomAccessibleInterval;
//import net.imglib2.Volatile;
//import net.imglib2.img.basictypeaccess.volatiles.VolatileAccess;
//import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
//import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
//import net.imglib2.realtransform.AffineTransform3D;
//import net.imglib2.type.NativeType;
//import net.imglib2.type.numeric.integer.UnsignedByteType;
//import net.imglib2.type.numeric.integer.UnsignedShortType;
//import net.imglib2.type.volatiles.VolatileUnsignedByteType;
//import net.imglib2.type.volatiles.VolatileUnsignedShortType;
//import net.imglib2.util.Fraction;
//
///**
// *
// * @author Henry
// */
//public abstract class MagellanImgLoader< T extends NativeType< T >, V extends Volatile< T > & NativeType< V >, A extends VolatileAccess >
//		extends AbstractViewerImgLoader< T, V > {
//
//   
//   protected MagellanImgLoader(LegacyMagellanImgLoader loader) {
//     super(new LegacyMagellanImgLoader<T, V, A>
//  
//   }
//
//
//
//}
