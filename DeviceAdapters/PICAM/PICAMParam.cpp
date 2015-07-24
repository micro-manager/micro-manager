#include "PICAMParam.h"

PvUniversalParam::PvUniversalParam( std::string aName, PicamParameter aParamId, Universal* aCamera ) :
   PvParamBase( aName, aParamId, aCamera )
{
   this->mName = aName;
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
      //case TYPE_INT8:
      //    os << mValue.int8_val;
      //    return os.str();
      //case TYPE_UNS8:
      //    os << mValue.uns8_val;
      //    return os.str();
      //case TYPE_INT16:
      //    os << mValue.int16_val;
      //    return os.str();
      //case TYPE_UNS16:
      //    os << mValue.uns16_val;
      //    return os.str();
      case PicamValueType_Integer:
         os << mValue.int32_val;
         return os.str();
      case PicamValueType_Boolean:
         os << mValue.rs_bool_val;
         return os.str();
      case PicamValueType_Enumeration:
         for ( unsigned i = 0; i < mEnumValues.size(); ++i )
         {
            if ( mEnumValues[i] == mValue.enum_val )
               return mEnumStrings[i];
         }
         mCamera->LogCamError(__LINE__, "PvUniversalParam::ToString() Enum string not found" );
         return "VALUE NAME NOT FOUND";
         //case TYPE_UNS32:
         //    os << mValue.uns32_val;
         //    return os.str();
      case PicamValueType_FloatingPoint:
         os << mValue.flt64_val;
         return os.str();
      case PicamValueType_LargeInteger:
         os << mValue.long64_val;
         return os.str();
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::ToString() Type not supported" );
         return "VALUE TYPE NOT SUPPORTED";
   }
}

long PvUniversalParam::ToLong()
{
   switch (mType)
   {
      case PicamValueType_Integer:
         return (long)mValue.int32_val;
      case PicamValueType_Enumeration:
         return (long)mValue.enum_val;
      case PicamValueType_Boolean:
         return (long)mValue.rs_bool_val;
      case PicamValueType_FloatingPoint:
         return (long)mValue.flt64_val;
      case PicamValueType_LargeInteger:
         return (long)mValue.long64_val;
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::ToLong() Type not supported" );
         return 0;
   }
}

double PvUniversalParam::ToDouble()
{
   switch (mType)
   {
      case PicamValueType_Integer:
         return (double)mValue.int32_val;
      case PicamValueType_Enumeration:
         return (double)mValue.enum_val;
      case PicamValueType_Boolean:
         return (double)mValue.rs_bool_val;
      case PicamValueType_FloatingPoint:
         return (double)mValue.flt64_val;
      case PicamValueType_LargeInteger:
         return (double)mValue.long64_val;
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::ToDouble() Type not supported" );
         return 0;
   }
}

int PvUniversalParam::Set(std::string aValue)
{
   std::stringstream ss(aValue);
   double dValue = 0;

   switch (mType)
   {
      case PicamValueType_Enumeration:
         // The enum param is a special case, we don't set a number value but
         // a string representation obtained from pl_get_enum_param()
         for ( piint i = 0; i < mEnumStrings.size(); i++)
         {
            if ( mEnumStrings[i].compare( aValue ) == 0 )
            {
               mValue.enum_val = mEnumValues[i];
               return DEVICE_OK;
            }
         }
         mCamera->LogCamError(__LINE__, "PvUniversalParam::Set(string) String not found among enum values" );
         return DEVICE_CAN_NOT_SET_PROPERTY;
      case PicamValueType_Integer:
      case PicamValueType_Boolean:
      case PicamValueType_FloatingPoint:
      case PicamValueType_LargeInteger:
         // Other supported types are value-types. We try to convert the string to a double value which is
         // the larges type supported in universal params, validate it and then set.
         ss >> dValue;
         if (ss.fail())
         {
            mCamera->LogCamError(__LINE__, "PvUniversalParam::Set(string) Failed to convert the param from string" );
            return DEVICE_CAN_NOT_SET_PROPERTY;
         }
         return Set(dValue);

      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::Set(string) Type not supported" );
         return DEVICE_CAN_NOT_SET_PROPERTY;
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
      mCamera->LogCamError(__LINE__, "PvUniversalParam::Set(double) Value out of range" );
      return DEVICE_CAN_NOT_SET_PROPERTY;
   }

   switch (mType)
   {
      case PicamValueType_Integer:
         mValue.int32_val = (piint)aValue;
         return DEVICE_OK;
      case PicamValueType_Enumeration:
         mValue.enum_val = (piint)aValue;
         return DEVICE_OK;
      case PicamValueType_Boolean:
         mValue.rs_bool_val = (pibln)aValue;
         return DEVICE_OK;
      case PicamValueType_FloatingPoint:
         mValue.flt64_val = (piflt)aValue;
         return DEVICE_OK;
      case PicamValueType_LargeInteger:
         mValue.long64_val = (pi64s)aValue;
         return DEVICE_OK;
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::Set(double) Type not supported" );
         return DEVICE_CAN_NOT_SET_PROPERTY;
   }
}


int PvUniversalParam::Read()
{
   return plGetParam(  mValue );
}

int PvUniversalParam::Write()
{
   return plSetParam( mValue );
}

double PvUniversalParam::GetMax()
{
   switch (mType)
   {
      case PicamValueType_Integer:
         return (double)mValueMax.int32_val;
      case PicamValueType_Enumeration:
         return (double)mValueMax.enum_val;
      case PicamValueType_Boolean:
         return (double)mValueMax.rs_bool_val;
      case PicamValueType_FloatingPoint:
         return (double)mValueMax.flt64_val;
      case PicamValueType_LargeInteger:
         return (double)mValueMax.long64_val;
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::GetMax() Type not supported" );
         return 0;
   }
}

double PvUniversalParam::GetMin()
{
   switch (mType)
   {
      case PicamValueType_Integer:
         return (double)mValueMin.int32_val;
      case PicamValueType_Enumeration:
         return (double)mValueMin.enum_val;
      case PicamValueType_Boolean:
         return (double)mValueMin.rs_bool_val;
      case PicamValueType_FloatingPoint:
         return (double)mValueMin.flt64_val;
      case PicamValueType_LargeInteger:
         return (double)mValueMin.long64_val;
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::GetMin() Type not supported" );
         return 0;
   }
}


// PROTECTED

int PvUniversalParam::initialize()
{
   plGetParam( mValue );

   if ( mType == PicamValueType_Enumeration )
   {
      mEnumStrings.clear();
      mEnumValues.clear();



      if (mConstraint_type ==PicamConstraintType_Collection){
         const PicamCollectionConstraint* capable;
         Picam_GetParameterCollectionConstraint(
               mCamera->Handle(), mId,
               PicamConstraintCategory_Capable,
               &capable );

         const pichar* string;
         PicamEnumeratedType type;

         for ( piint i = 0; i < capable->values_count; i++ )
         {
            piint enumStrLen = 0;

            Picam_GetParameterEnumeratedType(mCamera->Handle(), mId, &type );
            Picam_GetEnumerationString( type, (piint)capable->values_array[i], &string );

            enumStrLen=(piint)strlen(string);

            char* enumStrBuf = new char[enumStrLen+1];
            enumStrBuf[enumStrLen] = '\0';
            strcpy(enumStrBuf, string);
            mEnumStrings.push_back( std::string( enumStrBuf ) );
            mEnumValues.push_back( (piint)capable->values_array[i] );

            Picam_DestroyString( string );

            delete[] enumStrBuf;
         }
      }
   }
   else
   {

      switch( mConstraint_type )
      {
         case PicamConstraintType_Range:
            const PicamRangeConstraint* capable;

            Picam_GetParameterRangeConstraint(
                  mCamera->Handle(), mId,
                  PicamConstraintCategory_Capable,
                  &capable);

            switch (mType)
            {
               case PicamValueType_Integer:
                  mValueMin.int32_val=(piint)capable->minimum;
                  mValueMax.int32_val=(piint)capable->maximum;
                  break;
               case PicamValueType_Enumeration:
                  mValueMin.enum_val=(piint)capable->minimum;
                  mValueMax.enum_val=(piint)capable->maximum;
                  break;
               case PicamValueType_Boolean:
                  mValueMin.rs_bool_val=(pibln)capable->minimum;
                  mValueMax.rs_bool_val=(pibln)capable->maximum;
                  break;
               case PicamValueType_FloatingPoint:
                  mValueMin.flt64_val=capable->minimum;
                  mValueMax.flt64_val=capable->maximum;
                  break;
               case PicamValueType_LargeInteger:
                  mValueMax.long64_val=(pi64s)capable->minimum;
                  mValueMax.long64_val=(pi64s)capable->maximum;
                  break;
            }
            Picam_DestroyRangeConstraints(capable);
            break;
         case PicamConstraintType_Collection:
            const PicamCollectionConstraint* capable2;

            Picam_GetParameterCollectionConstraint(
                  mCamera->Handle(), mId,
                  PicamConstraintCategory_Capable,
                  &capable2);

            mValueMin.enum_val=(piint)capable2->values_array[0];
            mValueMax.enum_val=(piint)capable2->values_array[capable2->values_count-1];


            Picam_DestroyCollectionConstraints(capable2);
            break;
      }


   }

   return DEVICE_OK;
}

// PRIVATE

int PvUniversalParam::plGetParam( PvUniversalParamValue& aValueOut )
{
   PicamError pvRet;
   switch (mType)
   {
      case PicamValueType_Integer:
         pvRet=Picam_GetParameterIntegerValue(	mCamera->Handle(), mId, &aValueOut.int32_val);
         break;
      case PicamValueType_Enumeration:
         pvRet = Picam_GetParameterIntegerValue( mCamera->Handle(), mId,  &aValueOut.enum_val );
         break;
      case PicamValueType_Boolean:
         pvRet = Picam_GetParameterIntegerValue( mCamera->Handle(), mId, &aValueOut.rs_bool_val );
         break;
      case PicamValueType_FloatingPoint:
         pvRet = Picam_GetParameterFloatingPointValue( mCamera->Handle(), mId, &aValueOut.flt64_val );
         break;
      case PicamValueType_LargeInteger:
         pvRet = Picam_GetParameterLargeIntegerValue( mCamera->Handle(), mId, &aValueOut.long64_val );
         break;
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::plGetParam() type not supported");
         pvRet = PicamError_UnexpectedError;
         break;
   }

   if (pvRet != PicamError_None)
      return DEVICE_ERR;
   return DEVICE_OK;
}

int PvUniversalParam::plSetParam( PvUniversalParamValue& aValueOut )
{
   PicamError pvRet;

   switch (mType)
   {
      case PicamValueType_Integer:
         pvRet = Picam_SetParameterIntegerValue( mCamera->Handle(), mId, aValueOut.int32_val );
         break;
      case PicamValueType_Enumeration:
         pvRet = Picam_SetParameterIntegerValue( mCamera->Handle(), mId, aValueOut.enum_val );
         break;
      case PicamValueType_Boolean:
         pvRet = Picam_SetParameterIntegerValue( mCamera->Handle(), mId, aValueOut.rs_bool_val );
         break;
      case PicamValueType_FloatingPoint:
         pvRet = Picam_SetParameterFloatingPointValue( mCamera->Handle(), mId, aValueOut.flt64_val );
         break;
      case PicamValueType_LargeInteger:
         pvRet = Picam_SetParameterLargeIntegerValue( mCamera->Handle(), mId, aValueOut.long64_val );
         break;
      default:
         mCamera->LogCamError(__LINE__, "PvUniversalParam::plSetParam() type not supported");
         pvRet = PicamError_UnexpectedError;
         break;
   }

   if (pvRet != PicamError_None)
      return DEVICE_ERR;
   return DEVICE_OK;
}
