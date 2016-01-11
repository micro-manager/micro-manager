#include "DynError.h"

// nothing to construct
DynError::DynError()
{
	err = 0;
};					
	
// nothing to destroy
DynError::~DynError()
{
};	

// copy constructor	
DynError::DynError(const DynError& oDynError)		
{
	err = oDynError.err;
	descr = oDynError.descr;
}
