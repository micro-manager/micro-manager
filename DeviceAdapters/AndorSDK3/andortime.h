//******************************************************************************
// Andor Technology
// Author : Eamonn Boyle
// Date   : 30-Jan-2007
//******************************************************************************
// Class       : TAndorTime
// Description : A simple Time abstraction. See implementations for more details
//******************************************************************************
#ifndef andortime_H
#define andortime_H
//******************************************************************************

#ifdef ANDOR_API_UNIX
  #include "andorlinuxtime.h"
#else
  #include "andorwindowstime.h"
#endif

#endif
