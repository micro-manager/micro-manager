#ifndef _PVCAM_PARAM_H_
#define _PVCAM_PARAM_H_

#include "DeviceBase.h"
#include "PVCAMAdapter.h"

#define MAX_ENUM_STR_LEN 100

#ifndef WIN32
typedef long long long64;
#endif

/***
* A base class for PVCAM parameters. This is used for easy access to specific camera parameters.
*/

class PvParamBase
{
public:

    PvParamBase( std::string aName, uns32 aParamId, Universal* aCamera )
    {
       this->mId = aParamId;
       this->mName = aName;
       this->mCamera = aCamera;
       initialize();
    }

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

/***
* Template class for PVCAM parameters. This class makes the access to PVCAM parameters easier.
* The user must use the correct parameter type as defined in PVCAM manual.
* Usage: PvParam<int16> prmTemperature = new PvParam<int16>( "Temperature", PARAM_TEMP, this );
*/
template<class T>
class PvParam : public PvParamBase
{
public:
    PvParam( std::string aName, uns32 aParamId, Universal* aCamera )
        : PvParamBase( aName, aParamId, aCamera )
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
    T           mCount;
    T           mIncrement;

private:
    
};

/***
* A special case of PvParam that contains supporting functions for reading the enumerable parameter
* types.
*/
class PvEnumParam : public PvParam<uns32>
{
public:

    /***
    * Initializes the enumerable PVCAM parameter type
    */
    PvEnumParam( std::string aName, uns32 aParamId, Universal* aCamera )
        : PvParam<uns32>( aName, aParamId, aCamera )
    {
        mEnumStrings.clear();

        int32 enumVal;
        char enumStr[MAX_ENUM_STR_LEN];
        for ( uns32 i = 0; i < mCount; i++ )
        {
            if ( pl_get_enum_param( mCamera->Handle(), mId, i, &enumVal, enumStr, MAX_ENUM_STR_LEN ) != PV_OK )
            {
                mCamera->LogCamError(__LINE__, "pl_get_enum_param");
                mEnumStrings.push_back( "Unable to read" );
            }
            else
            {
                mEnumStrings.push_back( std::string( enumStr ) );
            }
        }
    }

    /***
    * Overloaded function. Return the enum string instead of value only.
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

    /***
    * Sets the enumerable PVCAM parameter from string. The string agrument must be exactly the
    * same as obtained from ToString() or GetEnumStrings().
    */
    int Set(std::string aValue)
    {
        for ( uns32 i = 0; i < mEnumStrings.size(); i++)
        {
            if ( mEnumStrings[i].compare( aValue ) == 0 )
            {
                mCurrent = i;
                return DEVICE_OK;
            }
        }
        mCamera->LogCamError(__LINE__, "PvEnumParam::Set() invalid argument");
        return DEVICE_CAN_NOT_SET_PROPERTY;
    }


private:

   std::vector<std::string> mEnumStrings; // All string representation of enum values

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
    uns32      enum_val;
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
* and Double parameter values, but PVCAM supports much more types, there are many parameters
* that requires special handling (e.g. value conversion) and a change in some parameter
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
    
    std::vector<std::string> mEnumStrings;

private:

    int plGetParam( int16 aAttr, PvUniversalParamValue& aValueOut );
    int plSetParam( PvUniversalParamValue& aValueOut );

};

#endif // _PVCAM_PARAM_H_
