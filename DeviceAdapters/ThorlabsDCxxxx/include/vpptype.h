/*---------------------------------------------------------------------------*/
/* Distributed by VXIplug&play Systems Alliance                              */
/*                                                                           */
/* Do not modify the contents of this file.                                  */
/*---------------------------------------------------------------------------*/
/*                                                                           */
/* Title   : VPPTYPE.H                                                       */
/* Date    : 02-14-95                                                        */
/* Purpose : VXIplug&play instrument driver header file                      */
/*                                                                           */
/*---------------------------------------------------------------------------*/

#ifndef __VPPTYPE_HEADER__
#define __VPPTYPE_HEADER__

#include "visatype.h"

/*- Completion and Error Codes ----------------------------------------------*/

#define VI_WARN_NSUP_ID_QUERY     (          0x3FFC0101L)
#define VI_WARN_NSUP_RESET        (          0x3FFC0102L)
#define VI_WARN_NSUP_SELF_TEST    (          0x3FFC0103L)
#define VI_WARN_NSUP_ERROR_QUERY  (          0x3FFC0104L)
#define VI_WARN_NSUP_REV_QUERY    (          0x3FFC0105L)

#define VI_ERROR_PARAMETER1       (_VI_ERROR+0x3FFC0001L)
#define VI_ERROR_PARAMETER2       (_VI_ERROR+0x3FFC0002L)
#define VI_ERROR_PARAMETER3       (_VI_ERROR+0x3FFC0003L)
#define VI_ERROR_PARAMETER4       (_VI_ERROR+0x3FFC0004L)
#define VI_ERROR_PARAMETER5       (_VI_ERROR+0x3FFC0005L)
#define VI_ERROR_PARAMETER6       (_VI_ERROR+0x3FFC0006L)
#define VI_ERROR_PARAMETER7       (_VI_ERROR+0x3FFC0007L)
#define VI_ERROR_PARAMETER8       (_VI_ERROR+0x3FFC0008L)
#define VI_ERROR_FAIL_ID_QUERY    (_VI_ERROR+0x3FFC0011L)
#define VI_ERROR_INV_RESPONSE     (_VI_ERROR+0x3FFC0012L)


/*- Additional Definitions --------------------------------------------------*/

#define VI_ON                     (1)
#define VI_OFF                    (0)

#endif

/*- The End -----------------------------------------------------------------*/

