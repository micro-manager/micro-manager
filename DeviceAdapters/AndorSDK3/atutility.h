//---------------------------------------------------------------------------

#ifndef atutilityH
#define atutilityH
//---------------------------------------------------------------------------
#include "atcore.h"

#define AT_ERR_INVALIDOUTPUTPIXELENCODING 1002
#define AT_ERR_INVALIDINPUTPIXELENCODING 1003

#ifdef __cplusplus
extern "C" {
#endif

int AT_EXP_CONV AT_ConvertBuffer(AT_U8* inputBuffer,
                                            AT_U8* outputBuffer,
                                            AT_64 width,
                                            AT_64 height,
                                            AT_64 stride,
                                            const AT_WC * inputPixelEncoding,
                                            const AT_WC * outputPixelEncoding);
int AT_EXP_CONV AT_InitialiseUtilityLibrary();
int AT_EXP_CONV AT_FinaliseUtilityLibrary();

#ifdef __cplusplus
}
#endif

#endif
