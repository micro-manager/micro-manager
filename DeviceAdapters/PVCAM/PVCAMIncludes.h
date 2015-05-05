#ifndef _PVCAMINCLUDES_H_
#define _PVCAMINCLUDES_H_

#ifdef WIN32
#pragma warning(push)
#include "../../../3rdpartypublic/Photometrics/PVCAM/SDK/Headers/master.h"
#include "../../../3rdpartypublic/Photometrics/PVCAM/SDK/Headers/pvcam.h"
#pragma warning(pop)
#endif

#ifdef __APPLE__
#define __mac_os_x
#include <PVCAM/master.h>
#include <PVCAM/pvcam.h>
#endif

#ifdef linux
#include <pvcam/master.h>
#include <pvcam/pvcam.h>
#endif

#endif // _PVCAMINCLUDES_H_