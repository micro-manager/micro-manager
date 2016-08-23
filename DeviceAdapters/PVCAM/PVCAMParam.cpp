#include "PVCAMParam.h"

PvUniversalParam::PvUniversalParam( const std::string& aDebugName, uns32 aParamId, Universal* aCamera, bool aDbgPrint ) :
    PvParamBase( aDebugName, aParamId, aCamera, aDbgPrint )
{
    this->mId = aParamId;
    this->mCamera = aCamera;
    initialize();
}

std::vector<std::string>& PvUniversalParam::GetEnumStrings()
{
    return mEnumStrings;
}

std::string PvUniversalParam::ToString()
{
    std::ostringstream os;
    switch (mType)
    {
    case TYPE_INT8:
        os << mValue.int8_val;
        return os.str();
    case TYPE_UNS8:
        os << mValue.uns8_val;
        return os.str();
    case TYPE_INT16:
        os << mValue.int16_val;
        return os.str();
    case TYPE_UNS16:
        os << mValue.uns16_val;
        return os.str();
    case TYPE_INT32:
        os << mValue.int32_val;
        return os.str();
    case TYPE_BOOLEAN:
        os << mValue.rs_bool_val;
        return os.str();
    case TYPE_ENUM:
        for ( unsigned i = 0; i < mEnumValues.size(); ++i )
        {
            if ( mEnumValues[i] == mValue.enum_val )
                return mEnumStrings[i];
        }
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::ToString() Enum string not found" );
        return "VALUE NAME NOT FOUND";
    case TYPE_UNS32:
        os << mValue.uns32_val;
        return os.str();
    case TYPE_FLT64:
        os << mValue.flt64_val;
        return os.str();
#ifdef TYPE_INT64
    case TYPE_INT64:
        os << mValue.long64_val;
        return os.str();
#endif
    default:
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::ToString() Type not supported" );
        return "VALUE TYPE NOT SUPPORTED";
    }
}

long PvUniversalParam::ToLong()
{
    switch (mType)
    {
    case TYPE_INT8:
        return (long)mValue.int8_val;
    case TYPE_UNS8:
        return (long)mValue.uns8_val;
    case TYPE_INT16:
        return (long)mValue.int16_val;
    case TYPE_UNS16:
        return (long)mValue.uns16_val;
    case TYPE_INT32:
        return (long)mValue.int32_val;
    case TYPE_ENUM:
        return (long)mValue.enum_val;
    case TYPE_BOOLEAN:
        return (long)mValue.rs_bool_val;
    case TYPE_UNS32:
        return (long)mValue.uns32_val;
    case TYPE_FLT64:
        return (long)mValue.flt64_val;
#ifdef TYPE_INT64
    case TYPE_INT64:
        return (long)mValue.long64_val;
#endif
    default:
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::ToLong() Type not supported" );
        return 0;
    }
}

double PvUniversalParam::ToDouble()
{
    switch (mType)
    {
    case TYPE_INT8:
        return (double)mValue.int8_val;
    case TYPE_UNS8:
        return (double)mValue.uns8_val;
    case TYPE_INT16:
        return (double)mValue.int16_val;
    case TYPE_UNS16:
        return (double)mValue.uns16_val;
    case TYPE_INT32:
        return (double)mValue.int32_val;
    case TYPE_ENUM:
        return (double)mValue.enum_val;
    case TYPE_BOOLEAN:
        return (double)mValue.rs_bool_val;
    case TYPE_UNS32:
        return (double)mValue.uns32_val;
    case TYPE_FLT64:
        return (double)mValue.flt64_val;
#ifdef TYPE_INT64
    case TYPE_INT64:
        return (double)mValue.long64_val;
#endif
    default:
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::ToDouble() Type not supported" );
        return 0;
    }
}

int PvUniversalParam::Set(std::string aValue)
{
    std::stringstream ss(aValue);
    double dValue = 0;

    switch (mType)
    {
    case TYPE_ENUM:
        // The enum param is a special case, we don't set a number value but
        // a string representation obtained from pl_get_enum_param()
        for ( uns32 i = 0; i < mEnumStrings.size(); i++)
        {
            if ( mEnumStrings[i].compare( aValue ) == 0 )
            {
                mValue.enum_val = mEnumValues[i];
                return DEVICE_OK;
            }
        }
        return mCamera->LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
            "PvUniversalParam::Set(string) String not found among enum values" );
    case TYPE_INT8:
    case TYPE_UNS8:
    case TYPE_INT16:
    case TYPE_UNS16:
    case TYPE_INT32:
    case TYPE_BOOLEAN:
    case TYPE_UNS32:
    case TYPE_FLT64:
#ifdef TYPE_INT64
    case TYPE_INT64:
#endif
        // Other supported types are value-types. We try to convert the string to a double value which is 
        // the larges type supported in universal params, validate it and then set.
        ss >> dValue;
        if (ss.fail())
        {
            return mCamera->LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
                "PvUniversalParam::Set(string) Failed to convert the param from string" );
        }
        return Set(dValue);

    default:
        return mCamera->LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
            "PvUniversalParam::Set(string) Type not supported" );
    }
}

int PvUniversalParam::Set(long aValue)
{
    return Set((double)aValue);
}

int PvUniversalParam::Set(double aValue)
{
    // Here is where all the overloaded Set method calls end up for value-type parameters
    if ( aValue < GetMin() || aValue > GetMax() )
    {
        return mCamera->LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
            "PvUniversalParam::Set(double) Value out of range" );
    }

    switch (mType)
    {
    case TYPE_INT8:
        mValue.int8_val = (int8)aValue;
        return DEVICE_OK;
    case TYPE_UNS8:
        mValue.uns8_val = (uns8)aValue;
        return DEVICE_OK;
    case TYPE_INT16:
        mValue.int16_val = (int16)aValue;
        return DEVICE_OK;
    case TYPE_UNS16:
        mValue.uns16_val = (uns16)aValue;
        return DEVICE_OK;
    case TYPE_INT32:
        mValue.int32_val = (int32)aValue;
        return DEVICE_OK;
    case TYPE_ENUM:
        mValue.enum_val = (uns32)aValue;
        return DEVICE_OK;
    case TYPE_BOOLEAN:
        mValue.rs_bool_val = (rs_bool)aValue;
        return DEVICE_OK;
    case TYPE_UNS32:
        mValue.uns32_val = (uns32)aValue;
        return DEVICE_OK;
    case TYPE_FLT64:
        mValue.flt64_val = (flt64)aValue;
        return DEVICE_OK;
#ifdef TYPE_INT64
    case TYPE_INT64:
        mValue.long64_val = (long64)aValue;
        return DEVICE_OK;
#endif
    default:
        return mCamera->LogAdapterError(DEVICE_CAN_NOT_SET_PROPERTY, __LINE__,
            "PvUniversalParam::Set(double) Type not supported" );
    }
}


int PvUniversalParam::Read()
{
    return plGetParam( ATTR_CURRENT, mValue );
}

int PvUniversalParam::Write()
{
    return plSetParam( mValue );
}

double PvUniversalParam::GetMax()
{
    switch (mType)
    {
    case TYPE_INT8:
        return (double)mValueMax.int8_val;
    case TYPE_UNS8:
        return (double)mValueMax.uns8_val;
    case TYPE_INT16:
        return (double)mValueMax.int16_val;
    case TYPE_UNS16:
        return (double)mValueMax.uns16_val;
    case TYPE_INT32:
        return (double)mValueMax.int32_val;
    case TYPE_ENUM:
        return (double)mValueMax.enum_val;
    case TYPE_BOOLEAN:
        return (double)mValueMax.rs_bool_val;
    case TYPE_UNS32:
        return (double)mValueMax.uns32_val;
    case TYPE_FLT64:
        return (double)mValueMax.flt64_val;
#ifdef TYPE_INT64
    case TYPE_INT64:
        return (double)mValueMax.long64_val;
#endif
    default:
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::GetMax() Type not supported" );
        return 0;
    }
}

double PvUniversalParam::GetMin()
{
    switch (mType)
    {
    case TYPE_INT8:
        return (double)mValueMin.int8_val;
    case TYPE_UNS8:
        return (double)mValueMin.uns8_val;
    case TYPE_INT16:
        return (double)mValueMin.int16_val;
    case TYPE_UNS16:
        return (double)mValueMin.uns16_val;
    case TYPE_INT32:
        return (double)mValueMin.int32_val;
    case TYPE_ENUM:
        return (double)mValueMin.enum_val;
    case TYPE_BOOLEAN:
        return (double)mValueMin.rs_bool_val;
    case TYPE_UNS32:
        return (double)mValueMin.uns32_val;
    case TYPE_FLT64:
        return (double)mValueMin.flt64_val;
#ifdef TYPE_INT64
    case TYPE_INT64:
        return (double)mValueMin.long64_val;
#endif
    default:
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::GetMin() Type not supported" );
        return 0;
    }
}

// PROTECTED

int PvUniversalParam::initialize()
{
    plGetParam( ATTR_CURRENT, mValue );

    if ( mType == TYPE_ENUM )
    {
        mEnumStrings.clear();
        mEnumValues.clear();

        uns32 count;
        int32 enumValue;
        if (pl_get_param( mCamera->Handle(), mId, ATTR_COUNT, (void_ptr)&count) != PV_OK)
        {
            mCamera->LogPvcamError(__LINE__, "PvUniversalParam::initialize() pl_get_param ATTR_COUNT");
            return DEVICE_ERR;
        }
        for ( uns32 i = 0; i < count; i++ )
        {
            uns32 enumStrLen;
            if ( pl_enum_str_length( mCamera->Handle(), mId, i, &enumStrLen ) != PV_OK )
            {
                mCamera->LogPvcamError(__LINE__, "PvUniversalParam::initialize() pl_enum_str_length");
                return DEVICE_ERR;
            }
            char* enumStrBuf = new char[enumStrLen+1];
            enumStrBuf[enumStrLen] = '\0';
            if (pl_get_enum_param( mCamera->Handle(), mId, i, &enumValue, enumStrBuf, enumStrLen) != PV_OK )
            {
                mCamera->LogPvcamError(__LINE__, "PvUniversalParam::initialize() pl_get_enum_param");
                return DEVICE_ERR;
            }
            mEnumStrings.push_back(std::string( enumStrBuf ));
            mEnumValues.push_back(enumValue);
            delete[] enumStrBuf;
        }
    }
    else
    {
        plGetParam( ATTR_MIN, mValueMin );
        plGetParam( ATTR_MAX, mValueMax );
    }

    return DEVICE_OK;
}

// PRIVATE

int PvUniversalParam::plGetParam( int16 aAttr, PvUniversalParamValue& aValueOut )
{
    rs_bool pvRet;
    switch (mType)
    {
    case TYPE_INT8:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.int8_val );
        break;
    case TYPE_UNS8:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.uns8_val );
        break;
    case TYPE_INT16:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.int16_val );
        break;
    case TYPE_UNS16:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.uns16_val );
        break;
    case TYPE_INT32:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.int32_val );
        break;
    case TYPE_ENUM:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.enum_val );
        break;
    case TYPE_BOOLEAN:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.rs_bool_val );
        break;
    case TYPE_UNS32:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.uns32_val );
        break;
    case TYPE_FLT64:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.flt64_val );
        break;
#ifdef TYPE_INT64
    case TYPE_INT64:
        pvRet = pl_get_param( mCamera->Handle(), mId, aAttr, &aValueOut.long64_val );
        break;
#endif
    default:
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::plGetParam() type not supported");
        pvRet = PV_FAIL;
        break;
    }

    if (pvRet != PV_OK)
        return DEVICE_ERR;
    return DEVICE_OK;
}

int PvUniversalParam::plSetParam( PvUniversalParamValue& aValueOut )
{
    rs_bool pvRet;
    switch (mType)
    {
    case TYPE_INT8:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.int8_val );
        break;
    case TYPE_UNS8:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.uns8_val );
        break;
    case TYPE_INT16:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.int16_val );
        break;
    case TYPE_UNS16:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.uns16_val );
        break;
    case TYPE_INT32:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.int32_val );
        break;
    case TYPE_ENUM:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.enum_val );
        break;
    case TYPE_BOOLEAN:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.rs_bool_val );
        break;
    case TYPE_UNS32:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.uns32_val );
        break;
    case TYPE_FLT64:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.flt64_val );
        break;
#ifdef TYPE_INT64
    case TYPE_INT64:
        pvRet = pl_set_param( mCamera->Handle(), mId, &aValueOut.long64_val );
        break;
#endif
    default:
        mCamera->LogAdapterError(DEVICE_ERR, __LINE__, "PvUniversalParam::plSetParam() type not supported");
        pvRet = PV_FAIL;
        break;
    }

    if (pvRet != PV_OK)
        return DEVICE_ERR;
    return DEVICE_OK;
}
