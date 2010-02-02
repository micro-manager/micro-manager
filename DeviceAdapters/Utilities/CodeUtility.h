#ifndef CODEUTILITY_H
#define CODEUTILITY_H
#include <string>
#include <iostream>

class CodeUtility
{
public:

static void DebugOutput( const std::string& __message);
static bool StringToBool( const std::string& value__);

static inline long nint( double value )
{ 
	if( value < 0. ) 
	{
		return static_cast<long>(-0.5 + value);
	}
	else
	{
		return static_cast<long>(0.5 + value);
	}
};



};

#endif // CODEUTILITY_H
