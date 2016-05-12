#ifndef _PVCAM_PARAM_H_
#define _PVCAM_PARAM_H_

#include "../../MMDevice/DeviceBase.h"
#include "PVCAMAdapter.h"

#ifndef WIN32
typedef long long long64;
typedef unsigned long long ulong64;
#endif

/***
* A base class for PVCAM parameters. This is used for easy access to specific camera parameters.
*/
class PvParamBase
{
public:

    PvParamBase( const std::string& aDebugName, uns32 aParamId, Universal* aCamera, bool aDbgPrint ) :
        mId( aParamId ), mCamera( aCamera ), mDebugName( aDebugName ),
        mAvail( FALSE ), mAccess( ACC_READ_ONLY ), mType( TYPE_INT32 )
    {
       initialize(aDbgPrint);
    }

    virtual ~PvParamBase() {}

    bool IsAvailable()      { return (mAvail == TRUE); }
    bool IsReadOnly()       { return (mAccess == ACC_READ_ONLY); }
    bool IsEnum()           { return (mType == TYPE_ENUM); }
    std::string DebugName() { return mDebugName; }

protected:

    uns32       mId;
    Universal*  mCamera;
    std::string mDebugName;

    rs_bool     mAvail;
    uns16       mAccess;
    uns16       mType;

private:

    int initialize(bool aDbgPrint)
    {
        if (aDbgPrint)
            mCamera->LogMessage("PVCAM: Initializing " + mDebugName + "...");
        if (pl_get_param(mCamera->Handle(), mId, ATTR_AVAIL, &mAvail ) != PV_OK)
        {
            mAvail = FALSE;
            mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_AVAIL failed");
            return DEVICE_ERR;
        }
        if (aDbgPrint)
            mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_AVAIL: " + (mAvail ? "TRUE" : "FALSE"));
        if ( mAvail )
        {
            if (pl_get_param(mCamera->Handle(), mId, ATTR_ACCESS, &mAccess ) != PV_OK)
            {
                mAccess = ACC_READ_ONLY;
                mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_ACCESS failed");
                return DEVICE_ERR;
            }
            if (aDbgPrint)
                mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_ACCESS: " + CDeviceUtils::ConvertToString(mAccess));
            if (pl_get_param(mCamera->Handle(), mId, ATTR_TYPE, &mType ) != PV_OK)
            {
                mType = 0; // We can ignore this, the mType is not used anyway
                mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_TYPE failed");
                return DEVICE_ERR;
            }
            if (aDbgPrint)
                mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_TYPE: " + CDeviceUtils::ConvertToString(mType));
        }
        if (aDbgPrint)
            mCamera->LogMessage("PVCAM: " + mDebugName + " initialized");
        return DEVICE_OK;
    }
};


#ifdef PVCAM_SMART_STREAMING_SUPPORTED
template<>
class PvParam <smart_stream_type>: public PvParamBase
{
    public:
    PvParam( const std::string& aDebugName, uns32 aParamId, Universal* aCamera, bool aDbgPrint ) : PvParamBase( aDebugName, aParamId, aCamera, aDbgPrint )
    {
        mCurrent.entries=SMART_STREAM_MAX_EXPOSURES;
        mCurrent.params = new uns32[SMART_STREAM_MAX_EXPOSURES];
        if (IsAvailable())
        {
            Update(aDbgPrint);
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
    int Update(bool aDbgPrint = false)
    {
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: Updating " + mDebugName + "...");
       // must be set to max exposures number so we can retrieve current number of exposures
       // from the camera in case new requested exposures count is greater than the previous  
       mCurrent.entries=SMART_STREAM_MAX_EXPOSURES;
       if (pl_get_param(mCamera->Handle(), mId, ATTR_CURRENT, &mCurrent ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + "ATTR_CURRENT failed");
           mCurrent.entries = 0;
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_CURRENT (entries): " + CDeviceUtils::ConvertToString(mCurrent.entries));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_MIN, &mMin ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_MIN failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_MIN (entries): " + CDeviceUtils::ConvertToString(mMin.entries));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_MAX, &mMax ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_MAX failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_MAX (entries): " + CDeviceUtils::ConvertToString(mMax.entries));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_COUNT, &mCount ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_COUNT failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_COUNT (entries): " + CDeviceUtils::ConvertToString(mCount.entries));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_INCREMENT, &mIncrement ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_INCREMENT failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_INCREMENT (entries): " + CDeviceUtils::ConvertToString(mIncrement.entries));

       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " update complete");
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
* Usage: PvParam<int16> prmTemperature = new PvParam<int16>( "PARAM_TEMP", PARAM_TEMP, this );
*/
template<class T>
class PvParam : public PvParamBase
{
public:
    PvParam( const std::string& aDebugName, uns32 aParamId, Universal* aCamera, bool aDbgPrint ) :
        PvParamBase( aDebugName, aParamId, aCamera, aDbgPrint ),
        mCurrent(0), mMax(0), mMin(0), mCount(0), mIncrement(0)
    {
       if (IsAvailable())
       {
           Update(aDbgPrint);
       }
    }

    // Getters
    T     Current()   { return mCurrent; }
    T     Max()       { return mMax; }
    T     Min()       { return mMin; }
    T     Increment() { return mIncrement; }
    uns32 Count()     { return mCount; }
    T     Default()   { return mDefault; }

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

    int SetAndApply(T aValue)
    {
        int nRet = Set(aValue);
        if (nRet != DEVICE_OK)
            return nRet;
        return Apply();
    }

    /***
    * Reads the current parameter value and range from the camera
    */
    int Update(bool aDbgPrint = false)
    {
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: Updating " + mDebugName + "...");

       if (pl_get_param(mCamera->Handle(), mId, ATTR_CURRENT, &mCurrent ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_CURRENT failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_CURRENT " + CDeviceUtils::ConvertToString((int)mCurrent));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_MIN, &mMin ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_MIN failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_MIN: " + CDeviceUtils::ConvertToString((int)mMin));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_MAX, &mMax ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_MAX failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_MAX: " + CDeviceUtils::ConvertToString((int)mMax));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_COUNT, &mCount ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_COUNT failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_COUNT: " + CDeviceUtils::ConvertToString((int)mCount));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_INCREMENT, &mIncrement ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_INCREMENT failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_INCREMENT: " + CDeviceUtils::ConvertToString((int)mIncrement));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_DEFAULT, &mDefault ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_DEFAULT failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_DEFAULT: " + CDeviceUtils::ConvertToString((int)mDefault));

       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " update complete");
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
    T           mDefault;

private:
    
};

/***
* Template class for PVCAM parameters of char* type
*/
class PvStringParam : public PvParamBase
{
public:
    PvStringParam( const std::string& aDebugName, uns32 aParamId, uns32 aMaxStrLen, Universal* aCamera, bool aDbgPrint ) :
        PvParamBase( aDebugName, aParamId, aCamera, aDbgPrint ),
        mTemp(NULL), mMaxLen(aMaxStrLen), mCurrent(""), mMax(""), mMin(""), mCount(0), mIncrement("")
    {
       mTemp = new char[mMaxLen];
       if (IsAvailable())
       {
           Update(aDbgPrint);
       }
    }
    ~PvStringParam()
    {
        delete mTemp;
    }

    // Getters
    std::string     Current()   { return mCurrent; }
    std::string     Max()       { return mMax; }
    std::string     Min()       { return mMin; }
    std::string     Increment() { return mIncrement; }
    uns32           Count()     { return mCount; }
    std::string     Default()   { return mDefault; }

    /***
    * Returns the current parameter value as string (useful for settings MM property)
    */
    virtual std::string ToString()
    {
        return mCurrent;
    }

    /***
    * Reads the current parameter value and range from the camera
    */
    int Update(bool aDbgPrint = false)
    {
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: Updating " + mDebugName + "...");

       if (pl_get_param(mCamera->Handle(), mId, ATTR_CURRENT, mTemp ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_CURRENT failed");
           return DEVICE_ERR;
       }
       mCurrent.assign(mTemp);
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_CURRENT '" + mCurrent + "'");

       if (pl_get_param(mCamera->Handle(), mId, ATTR_MIN, mTemp ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_MIN failed");
           return DEVICE_ERR;
       }
       mMin.assign(mTemp);
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_MIN: '" + mMin + "'");

       if (pl_get_param(mCamera->Handle(), mId, ATTR_MAX, mTemp ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_MAX failed");
           return DEVICE_ERR;
       }
       mMax.assign(mTemp);
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_MAX: '" + mMax + "'");

       if (pl_get_param(mCamera->Handle(), mId, ATTR_COUNT, &mCount ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_COUNT failed");
           return DEVICE_ERR;
       }
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_COUNT: " + CDeviceUtils::ConvertToString((int)mCount));

       if (pl_get_param(mCamera->Handle(), mId, ATTR_INCREMENT, mTemp ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_INCREMENT failed");
           return DEVICE_ERR;
       }
       mIncrement.assign(mTemp);
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_INCREMENT: '" + mIncrement + "'");

       if (pl_get_param(mCamera->Handle(), mId, ATTR_DEFAULT, mTemp ) != PV_OK)
       {
           mCamera->LogCamError(__LINE__, "PVCAM: pl_get_param for " + mDebugName + " ATTR_DEFAULT failed");
           return DEVICE_ERR;
       }
       mDefault.assign(mTemp);
       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " ATTR_DEFAULT: '" + mDefault + "'");

       if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " update complete");
       return DEVICE_OK;
    }

protected:

    char*       mTemp;
    uns32       mMaxLen;

    std::string mCurrent;
    std::string mMax;
    std::string mMin;
    uns32       mCount;     // ATTR_COUNT is always TYPE_UNS32
    std::string mIncrement;
    std::string mDefault;

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
    PvEnumParam( const std::string& aDebugName, uns32 aParamId, Universal* aCamera, bool aDbgPrint )
        : PvParam<int32>( aDebugName, aParamId, aCamera, aDbgPrint )
    {
        const bool bIsAvail = IsAvailable();
        if ( bIsAvail )
        {
            enumerate(aDbgPrint);
        }
    }

    /***
    * Overrided function. Return the enum string instead of the value only.
    */
    std::string ToString()
    {
        return mEnumMap[mCurrent];
    }

    /***
    * Returns all available enum strings for this parameter
    */
    std::vector<std::string>& GetEnumStrings()
    {
        return mEnumStrings;
    }
    /***
    * Returns the string that corresponds to given value
    */
    std::string GetEnumString(int32 value)
    {
        return mEnumMap[value];
    }
    /***
    * Retruns all available enum values for this parameter
    */
    std::vector<int32>& GetEnumValues()
    {
        return mEnumValues;
    }
    /***
    * Returns the value that corresponds to given string
    */
    int32 GetEnumValue(const std::string& string)
    {
        return mEnumMapReverse[string];
    }

    /***
    * Sets the enumerable PVCAM parameter from string. The string agrument must be exactly the
    * same as obtained from ToString() or GetEnumStrings().
    */
    int Set(const std::string& aValue)
    {
        std::map<std::string, int32>::iterator it = mEnumMapReverse.find(aValue);
        if (it == mEnumMapReverse.end())
        {
            mCamera->LogCamError(__LINE__, "PvEnumParam::Set() invalid argument");
            return DEVICE_CAN_NOT_SET_PROPERTY;
        }
        mCurrent = it->second;
        return DEVICE_OK;
    }

    /***
    * Sets the enumerable PVCAM parameter from value. The value agrument must be exactly the
    * same as obtained from GetEnumValues().
    */
    int Set(const int32 aValue)
    {
        // Make sure the value is one of the allowed values
        std::map<int32, std::string>::iterator it = mEnumMap.find(aValue);
        if (it == mEnumMap.end())
        {
            mCamera->LogCamError(__LINE__, "PvEnumParam::Set() invalid argument");
            return DEVICE_CAN_NOT_SET_PROPERTY;
        }
        mCurrent = it->first;
        return DEVICE_OK;
    }

    /**
    * Overrided function. If we want to re-read the parameter, we also need to re-enumerate the values.
    */
    int Update()
    {
        int err = PvParam<int32>::Update();
        if (err != DEVICE_OK)
            return err;
        enumerate(false);
        return DEVICE_OK;
    }


private:

    /**
    * Read all the enum values and correspondig string descriptions
    */
    void enumerate(bool aDbgPrint)
    {
        if (aDbgPrint)
           mCamera->LogMessage("PVCAM: Enumerating " + mDebugName + "...");

        mEnumStrings.clear();
        mEnumValues.clear();
        mEnumMap.clear();
        mEnumMapReverse.clear();

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
                const std::string enumStr( enumStrBuf );
                mEnumStrings.push_back( enumStr );
                mEnumValues.push_back( enumVal );
                mEnumMap[enumVal] = enumStr;
                mEnumMapReverse[enumStr] = enumVal;
                if (aDbgPrint)
                {
                    mCamera->LogMessage("PVCAM: " + mDebugName
                        + ", value=" + CDeviceUtils::ConvertToString(enumVal)
                        + ", string=" + enumStr);
                }
            }
            delete[] enumStrBuf;
        }
        if (aDbgPrint)
           mCamera->LogMessage("PVCAM: " + mDebugName + " enumeration complete");
   }

   // Enum values and their corresponding names, stored in vectors in the
   // exactly the same order as retrieved from PVCAM.
   std::vector<std::string> mEnumStrings;
   std::vector<int32>       mEnumValues;
   // Enum map contains the same info as the previous two vectors but
   // is used for faster searching and retrieval.
   std::map<int32, std::string> mEnumMap;
   std::map<std::string, int32> mEnumMapReverse;
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

    PvUniversalParam( const std::string& aDebugName, uns32 aParamId, Universal* aCamera, bool bDbgPrint );

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
