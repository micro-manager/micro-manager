#include "DynError.h"

DynError::DynError(void)
{
	err = 0;
}

DynError::DynError(const DynError& oDynError) // copy constructor
{
	err = oDynError.err;
	descr = oDynError.descr;
}

DynError::~DynError(void)
{
}

