#ifndef CODEUTILITY_H
#define CODEUTILITY_H
#include <string>
#include <iostream>
#include <math.h>


class CodeUtility
{
public:

static void DebugOutput( const std::string& message);
static bool StringToBool( const std::string& value);

static inline long nint( double value )
{ 
   return (long)floor( 0.5 + value);
};



};

#endif // CODEUTILITY_H
