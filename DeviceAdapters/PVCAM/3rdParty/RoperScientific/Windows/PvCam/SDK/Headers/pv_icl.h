/*****************************************************************************/
/************* IMAGER CONTROL LANGUAGE: A PVCAM OPTION LIBRARY ***************/
/*****************************************************************************/
/*            Copyright (C) 1994, Photometrics, Ltd.                         */
/*                          2001, Roper Scientific                           */
/*****************************************************************************/
/*
 * The functions in this library depend upon and make calls to the functions
 * in the regular PVCAM library.  Because of that, this requires the PVCAM
 * library to be present.  This file should be included after the include
 * files "master.h" and "pvcam.h".
 *
 *****************************************************************************/
#ifndef _PV_ICL_H
#define _PV_ICL_H


/******************************** ERROR CODES ********************************/
#define CLASS101_ERROR 10100      /* ICL lib may use errors 10100 - 10199    */

enum c101_error_vals 
{
  C101_ICL_UNKNOWN_ERROR=CLASS101_ERROR,/* ICL OPTION LIBRARY: unknown error */
  C101_ICL_LIB_NOT_INIT, /* the script library hasn't been initialized yet   */
  C101_ICL_LIB_INITED,   /* the script library has already been initialized  */
  C101_ICL_NO_BEGIN,     /* the "script_begin" command was never seen        */
  C101_ICL_END_TOO_SOON, /* the text ended before "script_end" instruction   */
  C101_ICL_INST_INVAL,   /* a script instruction could not be correctly read */
  C101_ICL_OPEN_PAREN,   /* an opening parenthesis should have been present  */
  C101_ICL_ILLEGAL_CHAR, /* an illegal character or symbol was seen          */
  C101_ICL_BAD_COMMA,    /* unexpected comma                                 */
  C101_ICL_BAD_NUMBER,   /* unexpected numeric parameter; comma needed?      */
  C101_ICL_BAD_CL_PAREN, /* unexpected closing parenthesis; extra comma?     */
  C101_ICL_NO_SEMICOLON, /* the semicolon was missing from this instruction  */
  C101_ICL_TOO_MANY_ARG, /* this instruction has too many parameters         */
  C101_ICL_TOO_FEW_ARG,  /* this instruction doesn't have enough parameters  */
  C101_ICL_ARG_IS_ZERO,  /* this argument must be greater than zero          */
  C101_ICL_ARG_OVER_65K, /* this argument must be 65,535 or less             */
  C101_ICL_ARG_INVALID,  /* this argument is invalid or illegal              */
  C101_ICL_OVER_LOOP,    /* the loops are nested too deeply                  */
  C101_ICL_UNDER_LOOP,   /* there are too many "loop_end" instructions       */
  C101_ICL_UNEVEN_LOOP,  /* "loop_begin" commands don't match "loop_end"     */
  C101_ICL_BIN_TOO_LARG, /* a readout's binning exceeds its size             */
  C101_ICL_RGN_TOO_LARG, /* the readout region does not fit on the CCD       */
  C101_ICL_DISPLAY_SMAL, /* displayed data is less than the collected data   */
  C101_ICL_DISPLAY_LARG, /* displayed data is more than the collected data   */
  C101_ICL_NO_FRAME_XFR, /* this camera doesn't have a separate storage array*/
  C101_ICL_NO_MPP,       /* this camera does not allow MPP mode              */
  C101_ICL_TOO_COMPLEX,  /* this script exceeds available program memory     */
  C101_NOT_SUPPORTED,    /* this camera does not support script download     */

  C101_END  
};


/********************************* TYPEDEFS **********************************/
typedef struct {                       /* ONE IMAGE "DISPLAY", FOR ICL       */
  uns16    x;                          /* image width  to display, in pixels */
  uns16    y;                          /* image height to display, in pixels */
  void_ptr disp_addr;                  /* starting address for this image    */
}           icl_disp_type,
PV_PTR_DECL icl_disp_ptr;


/********************************* CONSTANTS *********************************/


/**************************** FUNCTION PROTOTYPES ****************************/
#ifdef PV_C_PLUS_PLUS                  /* The prevents C++ compilers from    */
extern "C" {                           /*   performing "name mangling".      */
#endif

boolean PV_DECL pl_exp_init_script    ( void );
boolean PV_DECL pl_exp_uninit_script  ( void );
boolean PV_DECL pl_exp_setup_script   ( int16 hcam, 
                                        char_const_ptr script,
                                        uns32_ptr stream_size, 
                                        uns32_ptr num_rects );
boolean PV_DECL pl_exp_display_script ( int16 hcam,
                                        icl_disp_ptr user_disp_array, 
                                        void_ptr pixel_stream );
boolean PV_DECL pl_exp_listerr_script ( int16 hcam, 
                                        char_ptr err_char,
                                        uns32_ptr err_char_num, 
                                        uns32_ptr err_line,
                                        uns32_ptr err_ch_in_line );


#define pl_exp_start_script           ( hcam, pixel_stream ) \
        pl_exp_start_seq              ( hcam, pixel_stream )

#ifdef PV_C_PLUS_PLUS
}
#endif


#endif /* _PV_ICL_H */
