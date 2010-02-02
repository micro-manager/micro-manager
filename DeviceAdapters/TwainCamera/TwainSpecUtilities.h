#pragma once
#include <windows.h>
#include "twain.h"
class TwainSpecUtilities
{
public:
	TwainSpecUtilities(void);
	~TwainSpecUtilities(void);
// adapted from Twain Specification 2.0

/**********************************************************
* FloatToFix32
* Convert a floating point value into a FIX32.
**********************************************************/
static TW_FIX32 FloatToFIX32 (float floater)
{
   TW_FIX32 Fix32_value;
	bool minus = (floater < 0.);

	TW_INT32 value = (TW_INT32) (floater * 65536.0 + (minus?(-0.5):(0.5)));
   Fix32_value.Whole = value >> 16;
   Fix32_value.Frac = value & 0x0000ffffL;
   return (Fix32_value);

};

/**********************************************************
* Fix32ToFloat
* Convert a FIX32 value into a floating point value.
**********************************************************/
static float FIX32ToFloat (TW_FIX32    fix32)
{
	float floater;
	floater = (float) fix32.Whole + (float) (fix32.Frac / 65536.0);
	return floater;
};

	// return name of compression scheme
	static const char*const TwainCompressionScheme(const int TwainCompressionScheme)
	{
		switch( TwainCompressionScheme)
		{
case TWCP_NONE:
			return "TWCP_NONE";
		case TWCP_PACKBITS:
			return "TWCP_PACKBITS";
		case TWCP_GROUP31D:
			return "TWCP_GROUP31D"; //2 /* Follows CCITT spec (no End Of Line)          */
		case TWCP_GROUP31DEOL:
			return "TWCP_GROUP31DEOL"; //3 /* Follows CCITT spec (has End Of Line)         */
		case TWCP_GROUP32D:
			return "TWCP_GROUP32D"; //4 /* Follows CCITT spec (use cap for K Factor)    */
		case TWCP_GROUP4:
			return "TWCP_GROUP4";  //5 /* Follows CCITT spec                           */
		case TWCP_JPEG:
			return "TWCP_JPEG"; //6 /* Use capability for more info                 */
		case TWCP_LZW:
			return "TWCP_LZW"; //7 /* Must license from Unisys and IBM to use      */
		case TWCP_JBIG:
			return "TWCP_JBIG"; //8
		default:
			return "unknown";
		}
	}

};
