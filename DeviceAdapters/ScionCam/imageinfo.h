//////////////////////////////////////////////////////////////////////////////////////
//
//
//	sfwcore	- Scion Firewire Core Library
//
//	Version	1.0
//
//	Copyright 2008-2009  Scion Corporation  	(Win 2000/XP/Vista 32/64 Platforms)
//
//
//////////////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////////////////////////
//
//
//	File	imageinfo.h
//
//	definitions for image info classes
//
//
//////////////////////////////////////////////////////////////////////////////////////

#if !defined(IMAGEINFO_H__INCLUDED_)
#define IMAGEINFO_H__INCLUDED_

#include "sfwlib.h"

#ifndef	DLLExport
#ifdef	_DLL
#define	DLLExport	__declspec (dllexport)
#else
#define	DLLExport	__declspec (dllimport)
#endif
#endif

// captured image information											

class DLLExport Cimage_info
{
public:
	Cimage_info();
	virtual ~Cimage_info();

public:	
	unsigned int		type;				// 0 = grayscale, 1 = rgb	
	unsigned int		hd_image_present;	// 1 = image depth > 8-bit

	unsigned int		width;				// width in pixels
	unsigned int		height;				// height in pixels
	unsigned int		rowbytes;			// rowbytes
	unsigned int		depth;				// bit depth
	unsigned int		component_size;		// component size in bytes

	unsigned int		multi_frame;		// 1 = processed image			
	unsigned int		process_opt;		// processing option					
	unsigned int		process_frames;		// no of frames in processed image
};


#endif // !defined(IMAGE_INFO_H__INCLUDED_)
