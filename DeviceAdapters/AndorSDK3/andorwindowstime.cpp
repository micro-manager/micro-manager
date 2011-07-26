//******************************************************************************
// Andor Technology
// Author : Eamonn Boyle
// Date   : 30-Jan-2007
//******************************************************************************
// Class       : TAndorTime
// Description : A Windows implementation of the TAndorTime class, a simple time
//               data type abstraction.
//******************************************************************************
#include "windows.h"
#include "andortime.h"
//******************************************************************************

//******************************************************************************
// Function    : GetCurrentSystemTime
// Description : This function sets the passed TAndorTime object to the current
//               system time.
// Parameters  :
//    _time - The object to store the time in
// Returns     : zero
//******************************************************************************
int TAndorTime::GetCurrentSystemTime(TAndorTime & _time)
{
  LARGE_INTEGER time;
  andoru64 ui64_ratio = 1;
  bool mb_useFileTime = true;

  if (QueryPerformanceCounter(&time)) { // Count value and test that High Perf counters avilable
    LARGE_INTEGER freq;
    QueryPerformanceFrequency(&freq); // Number of counts per second

    double d_ratio = ((static_cast<andoru64>(freq.HighPart) << 32) +
                      static_cast<andoru64>(freq.LowPart)) /
                     1000000.0;
    if (d_ratio >= 1) { // Should always be true, never less than 1 count per millisecond
      mb_useFileTime = false;
      memcpy(&(_time.mui64_time), &time, sizeof(_time.mui64_time));
      ui64_ratio = static_cast<andoru64>(d_ratio);
    }
  }

  if (mb_useFileTime) { // Use lower resoultion (about 10ms)
    FILETIME currTime;
    GetSystemTimeAsFileTime(&currTime);
    memcpy(&(_time.mui64_time), &currTime, sizeof(_time.mui64_time));
    ui64_ratio = 10; // FILETIME is given in 100-nanosecond intervals so must scale count down
  }
  _time.mui64_time /= ui64_ratio;
  return 0;
}

//******************************************************************************
// Function    : GetCurrentSystemTime
// Description : This function sets this objects time to the current
//               system time.
// Returns     : zero
//******************************************************************************
int TAndorTime::GetCurrentSystemTime()
{
  return GetCurrentSystemTime(*this);
}

//******************************************************************************
// Function    : Overloaded subtraction operator
//******************************************************************************
TAndorTime operator-(const TAndorTime & _arg1, const TAndorTime & _arg2)
{
  TAndorTime result;
  result.mui64_time = _arg1.mui64_time - _arg2.mui64_time;
  return result;
}

//******************************************************************************
// Function    : Overloaded addition operator
//******************************************************************************
TAndorTime operator+(const TAndorTime & _arg1, const TAndorTime & _arg2)
{
  TAndorTime result;
  result.mui64_time = _arg1.mui64_time + _arg2.mui64_time;
  return result;
}

//******************************************************************************
// Function    : Assignment
//******************************************************************************
TAndorTime & TAndorTime::operator=(const TAndorTime & _arg)
{
  this->mui64_time = _arg.mui64_time;
  return *this;
}
