
#include <algorithm>

#include "CodeUtility.h"

#ifdef _WINDOWS
#ifdef _CONSOLE
#include <iostream>
#else
#include <windows.h>
#endif // _CONSOLE
#else
	//std::cerr << message.c_str();
#endif



void CodeUtility::DebugOutput( const std::string& message)
{

#ifdef _WINDOWS
#ifdef _CONSOLE
	std::cerr << message.c_str();
#else
	OutputDebugString( message.c_str());
#endif // _CONSOLE
#else
	std::cerr << message.c_str();
#endif
};


bool CodeUtility::StringToBool( const std::string& value)
{
	std::string v2(value);
	bool ret = false;

   std::transform( v2.begin(), v2.end(), v2.begin(), toupper);
	std::string::iterator ii = v2.begin();

	if('T' == *ii )
	{
		ret = true;
	}
	else if( '1' == *ii)
	{
		ret = true;
	}
	else if( 'Y' == *ii)
	{
		ret = true;
	}
	else if( "ON" == v2)
	{
		ret = true;
	}

	return ret;

}





