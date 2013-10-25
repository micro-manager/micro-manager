package HDF;



import ij.IJ;
import java.io.UnsupportedEncodingException;
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

/**
 *
 * @author henrypinkard
 */
public class HDFUtils {
   
     //Return dataspace, datatype, dataset IDs
   public static int[] createDataSet(int locationID, String name, long[] size, int type)
           throws HDF5LibraryException, HDF5Exception {
      
      //1) Create and initialize a dataspace for the dataset
      // number of dimensions, array with size of each dimension, array with max size of each dimension
      int dataSpaceID = H5.H5Screate_simple(size.length, size, null);

      //2) Define a datatype for the dataset by using method that copies existing datatype
      int dataTypeID = H5.H5Tcopy(type);
      H5.H5Tset_order(dataTypeID, HDF5Constants.H5T_ORDER_LE);
      
      //3) Create and initialize the dataset
      int dataSetID = H5.H5Dcreate(locationID, name, dataTypeID, dataSpaceID,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      
      return new int[]{dataSpaceID, dataTypeID, dataSetID};
   }
   
   public static int[] createCompressedDataSet(int locationID, String name, long[] size, int type, long[] chunk)
           throws HDF5LibraryException, HDF5Exception {
      
      //1) Create and initialize a dataspace for the dataset
      // number of dimensions, array with size of each dimension, array with max size of each dimension
      int dataSpaceID = H5.H5Screate_simple(size.length, size, null);

      //2) Define a datatype for the dataset by using method that copies existing datatype
      int dataTypeID = H5.H5Tcopy(type);
      H5.H5Tset_order(dataTypeID, HDF5Constants.H5T_ORDER_LE);
      
      //Optionally create property list specifiying compression
      int propListID = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
      H5.H5Pset_deflate(propListID, 2);
      H5.H5Pset_chunk(propListID, chunk.length, chunk);
              
      //3) Create and initialize the dataset
      int dataSetID = H5.H5Dcreate(locationID, name, dataTypeID, dataSpaceID,
              HDF5Constants.H5P_DEFAULT, propListID, HDF5Constants.H5P_DEFAULT);
      
      return new int[]{dataSpaceID, dataTypeID, dataSetID, propListID};
   }
        
   public static void writeStringAttribute(int objectID, String name, String value) throws HDF5LibraryException, HDF5Exception {
      //Create dataspace for attribute
      int dataspaceID = H5.H5Screate_simple(1, new long[]{value.length()}, null);
      int attID = H5.H5Acreate(objectID, name, HDF5Constants.H5T_C_S1, dataspaceID,
              HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
      try {
         H5.H5Awrite(attID, HDF5Constants.H5T_C_S1, value.getBytes("US-ASCII"));
      } catch (UnsupportedEncodingException ex) {
         IJ.log("Can't encode string");
      }
      //Close dataspace and attribute
      H5.H5Sclose(dataspaceID);
      H5.H5Aclose(attID);
   }
   
   
   
}
