//---------------------------------------------------------------------------

#ifndef atunpackerH
#define atunpackerH
//---------------------------------------------------------------------------
#include "atcore.h"


AT_EXP_MOD int AT_EXP_CONV AT_UnpackBuffer(AT_U8* inputBuffer,
                                 AT_U8* outputBuffer,
                                 AT_64 width,
                                 AT_64 height,
                                 AT_64 stride,
                                 const AT_WC * inputPixelEncoding,
                                 const AT_WC * outputPixelEncoding,
                                 int threads);

#endif
