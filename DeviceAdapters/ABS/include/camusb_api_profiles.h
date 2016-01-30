///////////////////////////////////////////////////////////////////////////////
/*!
*
* \file            CamUSB_API_Profiles.h
* \brief      API camera profiles functions
                    Allow common access to the camera profiles                    
* \version      1.00
* \author      ABS GmbH Jena (HBau)
*
* \date 16.11.2010 -> created
*
*/
///////////////////////////////////////////////////////////////////////////////
#ifndef _CAMUSB_API_PROFILES_H_
#define _CAMUSB_API_PROFILES_H_

// -------------------------- Includes ----------------------------------------
//
#include "CamUSB_API.h"   //!< include base header
#include <string>       //!< standard c++ string class: std::string 
#include <vector>       //!< standard c++ vector class: std::vector 

#pragma warning( disable: 4505 )

/////////////////////////////////////////////////////////////////////////////
//! \name Functions: Configuration
//!@{

USBAPI i32 CCONV CamUSB_ProfileGetListItems( const i32 iDevice,                                             
                                            const char* szProfileDir,
                                            i32 &iItems );

USBAPI i32 CCONV CamUSB_ProfileGetListItem( const i32 iDevice,
                                           i32 iIndex,
                                           char* szProfilePath,
                                           u32   dwProfilePathSize );

// --------------------------------------------------------------------------
// CamUSB_ProfileGetList
//! \brief    Search the passed path for camera profiles 
//!             (for the selected camera)
//!
//!  \param    iDevice        Camera index number, that identifies the 
//!                  camera device which should be used with this
//!                  function
//! \param    strProfileDir  path to the profile directory to look for 
//!                             camera profiles
//! \param    szSettingsName  name of settings data which should be used
//!
//! \retval    camera error code see #retOK
//!
static i32 CamUSB_ProfileGetList( const i32 iDevice,
                           const std::string &strProfileDir, 
                           std::vector<std::string> &strProfileLst )
{
    char szProfile[MAX_PATH] = {0};
    i32  iRC;
    i32  iCountItems = 0;

    iRC = CamUSB_ProfileGetListItems( iDevice, strProfileDir.c_str(), iCountItems );

    if (retOK == iRC)
    {
        strProfileLst.clear();
        for (i32 i=0; i < iCountItems; i++)
        {
            iRC = CamUSB_ProfileGetListItem( iDevice, i, szProfile, MAX_PATH );
            if (iRC == retOK)
            {
                strProfileLst.push_back( std::string(szProfile) );
            }
            else break;
        } 
    }    
    return iRC;
}


// --------------------------------------------------------------------------
// CamUSB_ProfileCopy
//! \brief    Copy a profile from on location on disk to another, 
//!             including additional data like shading references
//!             (profile rename is possible by changing destination file name)
//!
//! \param    strPathSrc  file path of the source profile to be copied
//! \param    strPathDst  destination profile file path
//!
//! \retval    camera error code see #retOK
//!
USBAPI i32 CCONV CamUSB_ProfileCopy( const char* szPathSrc, 
                                     const char* szPathDst ); 

// --------------------------------------------------------------------------
// CamUSB_ProfileDelete
//! \brief    Delete the profile from the location on disk, path to this 
//!             function including additional data like shading references
//!
//! \param    strPath      file path of the profile to be deleted
//!
//! \retval    camera error code see #retOK
//!
USBAPI i32 CCONV CamUSB_ProfileDelete(const char* szPath ); 

// --------------------------------------------------------------------------
// CamUSB_ProfileSno
//! \brief    Change Sno of the profile is bound on, it also deletes the
//!             shading reference data if they don't match the Sno
//!
//! \param    strPath          file path of the profile to be modified
//! \param    szSettingsName  name of settings data which should be used
//! \param    dwSno          new profile Sno
//!
//! \retval    camera error code see #retOK
//!
USBAPI i32 CCONV CamUSB_ProfileChangeSno(const char* szPath, 
                                         const char *szSettingsName, 
                                         const u32 dwSno ); 


//!@}

#endif // _CAMUSB_API_PROFILES_H_