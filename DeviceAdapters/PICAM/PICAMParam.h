#ifndef _PICAM_PARAM_H_
#define _PICAM_PARAM_H_

#include "DeviceBase.h"
#include "PICAMAdapter.h"

#include <algorithm>
#include <iterator>

#ifndef WIN32
typedef long long long64;
#endif

/***
 * A base class for PICAM parameters. This is used for easy access to specific camera parameters.
 */
class PvParamBase
{
   public:

      PvParamBase( std::string aName, PicamParameter aParamId, Universal* aCamera ) :
         mId( aParamId ), mCamera( aCamera ), mName( aName),
         mAvail( FALSE ), mAccess( PicamValueAccess_ReadOnly ), mType(PicamValueType_Integer)
   {
      initialize();
   }

      bool IsAvailable() { return (mAvail == TRUE); }
      bool IsReadOnly()  { return (mAccess == PicamValueAccess_ReadOnly); }
      bool IsEnum()      { return (mType == PicamValueType_Enumeration); }

   protected:

      PicamParameter    mId;
      Universal*        mCamera;
      std::string       mName;

      pibln                mAvail;
      pibln                mReadable;
      PicamConstraintType   mConstraint_type;
      PicamValueAccess      mAccess;
      PicamValueType        mType;

   private:

      int initialize()
      {
         mAvail=TRUE;

         Picam_CanReadParameter(mCamera->Handle(), mId, &mReadable );
         //Picam_IsParameterRelevant(mCamera->Handle(), mId, &mAvail );
         Picam_DoesParameterExist(mCamera->Handle(), mId, &mAvail );
         Picam_GetParameterConstraintType(mCamera->Handle(), mId, &mConstraint_type);


         if ( mAvail )
         {
            Picam_GetParameterValueAccess(mCamera->Handle(), mId, &mAccess );
            Picam_GetParameterValueType(mCamera->Handle(), mId, &mType);
            if (mType>=PicamValueType_Rois)
            {
               // We can ignore this, the mType is not used anyway
               mCamera->LogCamError(__LINE__, "IsParameterRelevant false");
               return DEVICE_ERR;
            }
         }
         return DEVICE_OK;
      }

};

/***
 * Template class for PICAM parameters. This class makes the access to PICAM parameters easier.
 * The user must use the correct parameter type as defined in PICAM manual.
 * Usage: PvParam<int16> prmTemperature = new PvParam<int16>( "Temperature", PARAM_TEMP, this );
 */
template<class T>
class PvParam : public PvParamBase
{
   public:
      PvParam( std::string aName, PicamParameter aParamId, Universal* aCamera ) :
         PvParamBase( aName, aParamId, aCamera ),
         mCurrent(0), mMax(0), mMin(0), mCount(0), mIncrement(0)
   {
      if (IsAvailable())
      {
         Update();
      }
   }

      // Getters
      T     Current()   { return mCurrent; }
      T     Max()       { return mMax; }
      T     Min()       { return mMin; }
      T     Increment() { return mIncrement; }
      piint Count()     { return mCount; }

      /***
       * Returns the current parameter value as string (useful for settings MM property)
       */
      virtual std::string ToString()
      {
         std::ostringstream os;
         os << mCurrent;
         return os.str();
      }

      /***
       * Sets the parameter value but does not apply the settings. Use Write() to
       * send the parameter to the camera.
       * TODO: Revisit the method name (might be confusing to have Set/Apply/Update methods)
       */
      int Set(T aValue)
      {
         mCurrent = aValue;
         return DEVICE_OK;
      }

      /***
       * Reads the current parameter value and range from the camera
       */
      int Update()
      {
         switch(mType){
            case PicamValueType_Integer:
            case PicamValueType_Boolean:
            case PicamValueType_Enumeration:
               if (PicamError_None!=Picam_GetParameterIntegerValue(
                        mCamera->Handle(), mId,
                        (piint *)&mCurrent))
                  return DEVICE_ERR;
               break;
            case PicamValueType_FloatingPoint:
               if (PicamError_None!=Picam_GetParameterFloatingPointValue(
                        mCamera->Handle(), mId,
                        (piflt *)&mCurrent ))
               {
                  return DEVICE_ERR;
               }

               break;
            case PicamValueType_LargeInteger:

               if (PicamError_None!=Picam_GetParameterLargeIntegerValue(
                        mCamera->Handle(), mId,
                        (pi64s *)&mCurrent))
                  return DEVICE_ERR;
               break;
            default:
               return DEVICE_ERR;
         }


         switch( mConstraint_type )
         {
            case PicamConstraintType_None:
               return DEVICE_ERR;
            case PicamConstraintType_Range:
               const PicamRangeConstraint* capable;

               Picam_GetParameterRangeConstraint(
                     mCamera->Handle(), mId,
                     PicamConstraintCategory_Capable,
                     &capable);

               mMin=static_cast<T>(capable->minimum);
               mMax=static_cast<T>(capable->maximum);

               mCount= capable->excluded_values_count;
               mIncrement=capable->increment;

               Picam_DestroyRangeConstraints(capable);
               break;
            case PicamConstraintType_Collection:
               const PicamCollectionConstraint* collection;

               Picam_GetParameterCollectionConstraint(
                     mCamera->Handle(), mId,
                     PicamConstraintCategory_Capable,
                     &collection);

               mMin=static_cast<T>(collection->values_array[0]);
               mMax=static_cast<T>(collection->values_array[collection->values_count-1]);

               mCount = collection->values_count;
               mIncrement = 1.0;

               Picam_DestroyCollectionConstraints(collection);
               break;
            default:
               return DEVICE_ERR;
         }

         return DEVICE_OK;
      }

      /***
       * Sends the parameter value to the camera
       */
      int Apply()
      {
         PicamError err=PicamError_InvalidOperation;

         // Write the current value to camera
         switch(mType){
            case PicamValueType_Integer:
            case PicamValueType_Boolean:
            case PicamValueType_Enumeration:
            case PicamValueType_LargeInteger:
               err=Picam_SetParameterIntegerValue(
                     mCamera->Handle(), mId,
                     (piint )mCurrent);
               break;
            case PicamValueType_FloatingPoint:
               err=Picam_SetParameterFloatingPointValue(
                     mCamera->Handle(), mId,
                     (piflt )mCurrent );
               break;
         }
         //if (plSetParam(mCamera->Handle(), mId, mCurrent) != PV_OK)
         if (err!=PicamError_None)
         {
            Update(); // On failure we need to update the cache with actual camera value
            mCamera->LogCamError(__LINE__, "Picam_SetParameter");
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
         return DEVICE_OK;
      }

      /***
       * Sends the parameter value to the camera
       */
      int OnLineApply()
      {
         pibln onlineable;
         PicamError err=PicamError_InvalidOperation;

         Picam_CanSetParameterOnline(mCamera->Handle(), mId, &onlineable );

         // Write the current value to camera
         if (onlineable){
            switch(mType){
               case PicamValueType_Integer:
               case PicamValueType_Boolean:
               case PicamValueType_Enumeration:
               case PicamValueType_LargeInteger:
                  err=Picam_SetParameterIntegerValueOnline(
                        mCamera->Handle(), mId,
                        (piint )mCurrent);
                  break;
               case PicamValueType_FloatingPoint:
                  err=Picam_SetParameterFloatingPointValueOnline(
                        mCamera->Handle(), mId,
                        (piflt )mCurrent );
                  break;
            }
         }
         //if (plSetParam(mCamera->Handle(), mId, mCurrent) != PV_OK)
         if (err!=PicamError_None)
         {
            Update(); // On failure we need to update the cache with actual camera value
            mCamera->LogCamError(__LINE__, "Picam_SetParameter");
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
         return DEVICE_OK;
      }

   protected:

      T           mCurrent;
      T           mMax;
      T           mMin;
      piint       mCount;     // ATTR_COUNT is always TYPE_UNS32
      T           mIncrement;

   private:

};

/***
 * A special case of PvParam that contains supporting functions for reading the enumerable parameter
 * types.
 */
class PvEnumParam : public PvParam<piint>
{
   public:

      /***
       * Initializes the enumerable PICAM parameter type
       */
      PvEnumParam( std::string aName, PicamParameter aParamId, Universal* aCamera )
         : PvParam<piint>( aName, aParamId, aCamera )
      {
         if ( IsAvailable() )
         {
            enumerate();
         }
      }

      /***
       * Overrided function. Return the enum string instead of the value only.
       */
      std::string ToString()
      {
         if ( IsAvailable() )
         {
            int index = std::distance(mEnumValues.begin(),
                  std::find(mEnumValues.begin(), mEnumValues.end(), mCurrent));
            if (index < mEnumValues.size())
               return mEnumStrings[index];
         }
         return "Invalid";
      }

      /***
       * Returns all available enum values for this parameter
       */
      std::vector<std::string>& GetEnumStrings()
      {
         return mEnumStrings;
      }

      std::vector<piint>& GetEnumValues()
      {
         return mEnumValues;
      }

      /***
       * Sets the enumerable PICAM parameter from string. The string agrument must be exactly the
       * same as obtained from ToString() or GetEnumStrings().
       */
      int Set(const std::string& aValue)
      {

         if ( IsAvailable() ){
            for ( unsigned i = 0; i < mEnumStrings.size(); ++i )
            {
               if ( mEnumStrings[i].compare( aValue ) == 0 )
               {
                  mCurrent = mEnumValues[i];
                  return DEVICE_OK;
               }
            }
         }
         mCamera->LogCamError(__LINE__, "Picam_EnumParam::Set() invalid argument");
         return DEVICE_CAN_NOT_SET_PROPERTY;
      }

      /***
       * Sets the enumerable PICAM parameter from value. The value agrument must be exactly the
       * same as obtained from GetEnumValues().
       */
      int Set(const piint aValue)
      {
         if ( IsAvailable() ){
            mCurrent = aValue;
            return DEVICE_OK;
         }
         mCamera->LogCamError(__LINE__, "PvEnumParam::Set() invalid argument");
         return DEVICE_CAN_NOT_SET_PROPERTY;
      }

      /**
       * Overrided function. If we want to re-read the parameter, we also need to re-enumerate the values.
       */
      int Update()
      {
         PvParam<piint>::Update();
         enumerate();
         return 0;
      }


   private:

      /**
       * Read all the enum values and correspondig string descriptions
       */
      void enumerate()
      {
         mEnumStrings.clear();
         mEnumValues.clear();

         if ( IsAvailable() ){

            piint enumVal;
            // Enumerate the parameter with the index and find actual values and descriptions

            if (mConstraint_type ==PicamConstraintType_Collection){
               const PicamCollectionConstraint* capable;
               Picam_GetParameterCollectionConstraint(
                     mCamera->Handle(), mId,
                     PicamConstraintCategory_Capable,
                     &capable );

               const pichar* string;
               PicamEnumeratedType type;

               Picam_GetParameterEnumeratedType(mCamera->Handle(), mId, &type );

               for ( piint i = 0; i < capable->values_count; i++ )
               {
                  piint enumStrLen = 0;

                  enumVal=(piint)capable->values_array[i];

                  //if (enumVal==mCurrent) mCurrentIndex=i;

                  Picam_GetEnumerationString( type, enumVal, &string );


                  enumStrLen=(piint)strlen(string);

                  char* enumStrBuf = new char[enumStrLen+1];

                  strcpy(enumStrBuf, string);

                  enumStrBuf[enumStrLen] = '\0';
                  mEnumStrings.push_back( std::string( enumStrBuf ) );
                  mEnumValues.push_back( enumVal );

                  Picam_DestroyString( string );

                  delete[] enumStrBuf;
               }
            }
         }
      }

      // Enum values and their corresponding names
      std::vector<std::string> mEnumStrings;
      std::vector<piint>       mEnumValues;

   protected:

      piint           mCurrentIndex;

};


//*****************************************************************************
//********************************************* PvParamUniversal implementation


/***
 * Union used to store universal PICAM parameter value
 */
typedef union
{
   pibln      rs_bool_val;
   //int8       int8_val;
   //uns8       uns8_val;
   //int16      int16_val;
   //uns16      uns16_val;
   piint      int32_val;
   piint      enum_val;
   //uns32      uns32_val;
   piflt      flt64_val;
   pi64s     long64_val;
   //ulong64    ulong64_val; // Not supported since this exceeds the double type range
} PvUniversalParamValue;


/***
 * Class for 'Universal' parameters.
 * The initial idea probably was to have all the PICAM parameters as universal so we'd not
 * have to implement separate handlers (On**Property()) for every parameter. However due to
 * PICAM nature it's impossible to create such universal class. The MM supports Long, String,
 * and Double parameter values, but PICAM supports much more types, there are many parameters
 * that requires special handling (e.g. value conversion) and a change in some parameter
 * requires update of other parameter.
 *
 * This class is not perfect. It can handle only some types of PICAM parameters and it's kept here
 * only for compatibility. See PICAMUniversal::g_UniversalParams.
 */
class PvUniversalParam : public PvParamBase
{
   public:

      PvUniversalParam( std::string aName, PicamParameter aParamId, Universal* aCamera );

      std::vector<std::string>& GetEnumStrings();

      // Getters: MM property can be either string, double or long
      std::string ToString();
      double ToDouble();
      long ToLong();
      // Setters
      int Set(std::string aValue);
      int Set(double aValue);
      int Set(long aValue);

      int Read();
      int Write();

      double GetMax();
      double GetMin();

   protected:

      int initialize();

   protected:

      PvUniversalParamValue mValue;
      PvUniversalParamValue mValueMax;
      PvUniversalParamValue mValueMin;

      // Enum values and their corresponding names obtained by pl_get_enum_param
      std::vector<std::string> mEnumStrings;
      std::vector<int>         mEnumValues;

   private:

      int plGetParam( PvUniversalParamValue& aValueOut );
      int plSetParam( PvUniversalParamValue& aValueOut );

};

#endif // _PICAM_PARAM_H_
