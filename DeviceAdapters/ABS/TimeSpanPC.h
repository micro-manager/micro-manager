#pragma once

#ifdef WIN32
   #define WIN32_LEAN_AND_MEAN
   #include <windows.h>   
   #define snprintf _snprintf 
   #pragma warning(disable : 4996) // disable warning for deperecated CRT functions on Windows 
#endif

class CTimeSpanPC
{
public:
  CTimeSpanPC( bool bStart = true );
  ~CTimeSpanPC(void);
  // start measure (automatically call by construction)
  void start();
  // stop measure (automatically call by getTime_ms if not done)
  void stop();
  // reset the stop time
  void resetStop();
  // get the current timespan
  double getTime_ms( bool bResetStop = false );
  double getTime_us( bool bResetStop = false );

  ULONGLONG getTickCount( bool bResetStop = false );
  
protected:
  LARGE_INTEGER getTime_Delta( bool bResetStop );
private:
  LARGE_INTEGER m_qwStart;
  LARGE_INTEGER m_qwStop;
};
