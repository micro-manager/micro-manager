#ifndef _PVCAMINCLUDES_H_
#define _PVCAMINCLUDES_H_

#if defined(_WIN32)
    #pragma warning(push)
    #include "../../../3rdpartypublic/Photometrics/PVCAM/SDK/Headers/master.h"
    #include "../../../3rdpartypublic/Photometrics/PVCAM/SDK/Headers/pvcam.h"
    #pragma warning(pop)
#elif defined(__linux__)
    // PVCAM runtime and SDK must be installed in order to build and link the adapter.
    // The header files are then added to include path via PVCAM_SDK_PATH env. var.
    // TODO: Add a copy of .h and .so* files to 3rdpartypublic?
    #include <master.h>
    #include <pvcam.h>
#else
    #error OS not supported
#endif

#endif // _PVCAMINCLUDES_H_
