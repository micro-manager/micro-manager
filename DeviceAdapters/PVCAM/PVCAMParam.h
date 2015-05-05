#ifndef _PVCAM_PARAM_H_
#define _PVCAM_PARAM_H_

#include "../../MMDevice/DeviceBase.h"
#include "PVCAMAdapter.h"

#ifndef WIN32
typedef long long long64;
#endif

/***
* A base class for PVCAM parameters. This is used for easy access to specific camera parameters.
*/
class PvParamBase
{
public:

    PvParamBase( std::string aName, uns32 aParamId, Universal* aCamera ) :
        mId( aParamId ), mCamera( aCamera ), mName( aName),
        mAvail( FALSE ), mAccess( ACC_READ_ONLY ), mType( TYPE_INT32 )
    {
       initialize();
    }

    virtual ~PvParamBase() {}

    bool IsAvailable() { return (mAvail == TRUE); }
    bool IsReadOnly()  { return (mAccess == ACC_READ_ONLY); }
    bool IsEnum()      { return (mType == TYPE_ENUM); }

protected:

    uns32       mId;
    Universal*  mCamera;
    std::string mName;

    rs_bool     mAvail;
    uns16       mAccess;
    uns16       mType;

private:

    int initialize()
    {
        if (pl_get_param(mCamera->Handle(), mId, ATTR_AVAIL, &mAvail ) != PV_OK)
        {
            mAvail = FALSE;
            mCamera->LogCamError(__LINE__, "pl_get_param ATTR_AVAIL");
            return DEVICE_ERR;
        }
        if ( mAvail )
        {
            if (pl_get_param(mCamera->Handle(), mId, ATTR_ACCESS, &mAccess ) != PV_OK)
            {
                mAccess = ACC_READ_ONLY;
                mCamera->LogCamError(__LINE__, "pl_get_param ATTR_ACCESS");
                return DEVICE_ERR;
            }
            if (pl_get_param(mCamera->Handle(), mId, ATTR_TYPE, &mType ) != PV_OK)
            {
                mType = 0; // We can ignore this, the mType is not used anyway
                mCamera->LogCamError(__LINE__, "pl_get_param ATTR_TYPE");
                return DEVICE_ERR;
            }
        }
        return DEVICE_OK;
    }

};


#ifdef PVCAM_SMART_STREAMING_SUPPORTED
template<>
class PvParam <smart_stream_type>: public PvParamBase
{
    public:
    PvParam( std::string aName, uns32 aParamId, Universal* aCamera ) : PvParamBase( aName, aParamId, aCamera )
    {
        mCurrent.entries=SMART_STREAM_MAX_EXPOSURES;
        mCurrent.params = new uns32[SMART_STREAM_MAX_EXPOSURES];
        if (IsAvailable())
        {
            Update();
        }
    }
    
    ~PvParam()
    {
        delete[] mCurrent.params;
    }
    


    // Getters
    smart_stream_type     Current()   { return mCurrent; }
    smart_stream_type     Max()       { return mMax; }
    smart_stream_type     Min()       { return mMin; }
    smart_stream_type     Increment() { return mIncrement; }
    smart_stream_type     Count()     { return mCount; }

/***
    * Returns the current parameter value as string (useful for settings MM property)
    */
    virtual std::string ToString()
    {
       std::ostringstream os;
       for (int i = 0; i < mCurrent.entries; i++)
          os << mCurrent.params[i] << ';';
       return os.str();

    }

    /***
    * Sets the parameter value but does not apply the settings. Use Write() to 
    * send the parameter to the camera.
    * TODO: Revisit the method name (might be confusing to have Set/Apply/Update methods)
    */
    int Set(smart_stream_type aValue)
    {
       mCurrent = aValue;
       return DEVICE_OK;
    }

    /***
    * Reads the current parameter value and range from the camera
    */
    int Update()
    {
       // must be set to max exposures number so we can retrieve current number of exposures
       // from the camera in case new requested exposures count is greater than the previous  
       mCurrent.entries=SMART_STREAM_MAX_EXPOSURES;
       if (pl_get_param(mCamera->Handle(), mId, ATTR_CURRENT, &mCurrent ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_CURRENT");
           mCurrent.entries = 0;
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_MIN, &mMin ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_MIN");
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_MAX, &mMax ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_MAX");
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_COUNT, &mCount ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_COUNT");
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_INCREMENT, &mIncrement ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_INCREMENT");
           return DEVICE_ERR;
       }
       return DEVICE_OK;
    }


    /***
    * Sends the parameter value to the camera
    */
    int Apply()
    {
        // Write the current value to camera
        if (pl_set_param(mCamera->Handle(), mId, (void_ptr)&mCurrent) != PV_OK)
        {
           Update(); // On failure we need to update the cache with actual camera value
           mCamera->LogCamError(__LINE__, "pl_set_param");
           return DEVICE_CAN_NOT_SET_PROPERTY;
        }
        return DEVICE_OK;
    }

protected:

    smart_stream_type mCurrent;
    smart_stream_type mMax;
    smart_stream_type mMin;
    smart_stream_type mCount;     // PARAM_SMART_STREAM_EXP_PARAMS is the only parameter where ATTR_COUNT is not uns32 type
    smart_stream_type mIncrement;

};
#endif

/***
* Template class for PVCAM parameters. This class makes the access to PVCAM parameters easier.
* The user must use the correct parameter type as defined in PVCAM manual.
* Usage: PvParam<int16> prmTemperature = new PvParam<int16>( "Temperature", PARAM_TEMP, this );
*/
template<class T>
class PvParam : public PvParamBase
{
public:
    PvParam( std::string aName, uns32 aParamId, Universal* aCamera ) :
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
    uns32 Count()     { return mCount; }

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
       if (pl_get_param(mCamera->Handle(), mId, ATTR_CURRENT, &mCurrent ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_CURRENT");
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_MIN, &mMin ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_MIN");
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_MAX, &mMax ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_MAX");
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_COUNT, &mCount ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_COUNT");
           return DEVICE_ERR;
       }
       if (pl_get_param(mCamera->Handle(), mId, ATTR_INCREMENT, &mIncrement ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "pl_get_param ATTR_INCREMENT");
           return DEVICE_ERR;
       }
       return DEVICE_OK;
    }

    /***
    * Sends the parameter value to the camera
    */
    int Apply()
    {
        // Write the current value to camera
        if (pl_set_param(mCamera->Handle(), mId, (void_ptr)&mCurrent) != PV_OK)
        {
           Update(); // On failure we need to update the cache with actual camera value
           mCamera->LogCamError(__LINE__, "pl_set_param");
           return DEVICE_CAN_NOT_SET_PROPERTY;
        }
        return DEVICE_OK;
    }

protected:

    T           mCurrent;
    T           mMax;
    T           mMin;
    uns32       mCount;     // ATTR_COUNT is always TYPE_UNS32
    T           mIncrement;

private:
    
};

/***
* A special case of PvParam that contains supporting functions for reading the enumerable parameter
* types.
*/
class PvEnumParam : public PvParam<int32>
{
public:

    /***
    * Initializes the enumerable PVCAM parameter type
    */
    PvEnumParam( std::string aName, uns32 aParamId, Universal* aCamera )
        : PvParam<int32>( aName, aParamId, aCamera )
    {
        if ( IsAvailable() )
        {
            enumerate();
        }
    }

    /***
    * Overrided function. Instead of returning the current enum index, we return the value.
    */
    int32 Current()
    {
        return mEnumValues[mCurrent];
    }

    /***
    * Overrided function. Return the enum string instead of the value only.
    */
    std::string ToString()
    {
        return mEnumStrings[mCurrent];
    }

    /***
    * Returns all available enum values for this parameter
    */
    std::vector<std::string>& GetEnumStrings()
    {
        return mEnumStrings;
    }

    std::vector<int32>& GetEnumValues()
    {
        return mEnumValues;
    }

    /***
    * Sets the enumerable PVCAM parameter from string. The string agrument must be exactly the
    * same as obtained from ToString() or GetEnumStrings().
    */
    int Set(const std::string& aValue)
    {
        for ( unsigned i = 0; i < mEnumStrings.size(); ++i )
        {
            if ( mEnumStrings[i].compare( aValue ) == 0 )
            {
                mCurrent = i; // mCurrent contains an index of the current value
                return DEVICE_OK;
            }
        }
        mCamera->LogCamError(__LINE__, "PvEnumParam::Set() invalid argument");
        return DEVICE_CAN_NOT_SET_PROPERTY;
    }

    /***
    * Sets the enumerable PVCAM parameter from value. The value agrument must be exactly the
    * same as obtained from GetEnumValues().
    */
    int Set(const int32 aValue)
    {
        for ( unsigned i = 0; i < mEnumValues.size(); ++i)
        {
            if ( mEnumValues[i] == aValue )
            {
                mCurrent = i; // mCurrent contains an index of the current value
                return DEVICE_OK;
            }
        }
        mCamera->LogCamError(__LINE__, "PvEnumParam::Set() invalid argument");
        return DEVICE_CAN_NOT_SET_PROPERTY;
    }

    /**
    * Overrided function. If we want to re-read the parameter, we also need to re-enumerate the values.
    */
    int Update()
    {
        int err = PvParam<int32>::Update();
        if (err != DEVICE_OK)
            return err;
        enumerate();
        return DEVICE_OK;
    }


private:

    /**
    * Read all the enum values and correspondig string descriptions
    */
    void enumerate()
    {
        mEnumStrings.clear();
        mEnumValues.clear();

        int32 enumVal;
        // Enumerate the parameter with the index and find actual values and descriptions
        for ( uns32 i = 0; i < mCount; i++ )
        {
            uns32 enumStrLen = 0;
            if ( pl_enum_str_length( mCamera->Handle(), mId, i, &enumStrLen ) != PV_OK )
            {
                mCamera->LogCamError(__LINE__, "pl_enum_str_length");
                break;
            }
            char* enumStrBuf = new char[enumStrLen+1];
            enumStrBuf[enumStrLen] = '\0';
            if ( pl_get_enum_param( mCamera->Handle(), mId, i, &enumVal, enumStrBuf, enumStrLen ) != PV_OK )
            {
                mCamera->LogCamError(__LINE__, "pl_get_enum_param");
                mEnumStrings.push_back( "Unable to read" );
                mEnumValues.push_back(0);
                break;
            }
            else
            {
                mEnumStrings.push_back( std::string( enumStrBuf ) );
                mEnumValues.push_back( enumVal );
            }
            delete[] enumStrBuf;
        }
   }

   // Enum values and their corresponding names
   std::vector<std::string> mEnumStrings;
   std::vector<int32>       mEnumValues;

};


//*****************************************************************************
//********************************************* PvParamUniversal implementation


/***
* Union used to store universal PVCAM parameter value
*/
typedef union
{
    rs_bool    rs_bool_val;
    int8       int8_val;
    uns8       uns8_val;
    int16      int16_val;
    uns16      uns16_val;
    int32      int32_val;
    int32      enum_val;
    uns32      uns32_val;
    flt64      flt64_val;
    long64     long64_val;
    //ulong64    ulong64_val; // Not supported since this exceeds the double type range
} PvUniversalParamValue;


/***
* Class for 'Universal' parameters.
* The initial idea probably was to have all the PVCAM parameters as universal so we'd not
* have to implement separate handlers (On**Property()) for every parameter. However due to
* PVCAM nature it's impossible to create such universal class. The MM supports Long, String,
* and Double parameter values, but PVCAM supports many more types, there are many parameters
* that require special handling (e.g. value conversion) and a change in some parameter
* requires update of other parameter.
*
* This class is not perfect. It can handle only some types of PVCAM parameters and it's kept here
* only for compatibility. See PVCAMUniversal::g_UniversalParams.
*/
class PvUniversalParam : public PvParamBase
{
public:

    PvUniversalParam( std::string aName, uns32 aParamId, Universal* aCamera );

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

    int plGetParam( int16 aAttr, PvUniversalParamValue& aValueOut );
    int plSetParam( PvUniversalParamValue& aValueOut );

};

#endif // _PVCAM_PARAM_H_
