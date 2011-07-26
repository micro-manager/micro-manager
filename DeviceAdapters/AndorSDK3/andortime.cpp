//******************************************************************************
// Andor Technology
// Author : Eamonn Boyle
// Date   : 30-Jan-2007
//******************************************************************************
// Class       : TAndorTime
// Description : A simple Time abstraction. See implementations for more details
//******************************************************************************
#include "andortime.h"
//******************************************************************************

#ifdef linux
  #include "andorlinuxtime.cpp"
#else
  #include "andorwindowstime.cpp"
#endif
