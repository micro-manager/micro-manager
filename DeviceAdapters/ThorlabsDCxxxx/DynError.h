#pragma once

#include <string>

//////////////////////////////////////////////////////////////////////////////
// Error codes
//
#define ERR_PORT_CHANGE_FORBIDDEN	101
#define ERR_INVALID_DEVICE			102
#define ERR_NO_DEVICE_CONNECTED		103
#define ERR_DCxxxx_OFFSET			120

/****************************************************************************
 class: 			DynError
 description:	This class represents one dynamic error which can be read from
 					the device and can be stored in an error vector.
****************************************************************************/
class DynError
{
public:
	DynError();					
	~DynError();								// nothing to destroy
	DynError(const DynError& oDynError); // copy constructor

public:
	int err;				// error number
	std::string descr;		// error description
};
