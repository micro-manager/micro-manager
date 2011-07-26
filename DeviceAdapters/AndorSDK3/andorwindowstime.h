//******************************************************************************
// Andor Technology
// Author : Eamonn Boyle
// Date   : 30-Jan-2007
//******************************************************************************
// Class       : TAndorTime
// Description : A Windows implementation of the TAndorTime class, a simple time
//               data type abstraction.
//******************************************************************************
#ifndef andorwindowstimeH
#define andorwindowstimeH

#include "andorvartypes.h"
//******************************************************************************

class TAndorTime
{
// Functions
public:
  TAndorTime()
  {
    mui64_time = 0;
  }

  // Stores the current system time in the passed TAndorTime object
  static int GetCurrentSystemTime(TAndorTime & _time);

  int GetCurrentSystemTime();

  // Overload the minus operator
  friend TAndorTime operator -(const TAndorTime & _arg1, const TAndorTime & _arg2);

  // Overload the add operator
  friend TAndorTime operator +(const TAndorTime & _arg1, const TAndorTime & _arg2);

  TAndorTime & operator=(const TAndorTime & _arg);

  inline andoru64 GetTimeus() const
  {
    return mui64_time;
  }

  inline andoru64 GetTimeMs() const
  {
    return mui64_time / 1000;
  }

  inline andoru64 GetTimeSeconds() const
  {
    return mui64_time / (1000 * 1000);
  }


// Data
public:
  // Store current system time as a 64 bit unsigned integer in microsecond UTC format
  // i.e. number of microseconds since midnight, January 1, 1970
  andoru64 mui64_time;
};
#endif
