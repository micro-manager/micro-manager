#ifndef _DBGBOX_H_
#define _DBGBOX_H_
#pragma once
#include "windows.h"
#include <string>

int DbgBox( std::string message__, std::string caption__, unsigned int buttonsType__ = MB_OK);




#endif //_DBGBOX_H_