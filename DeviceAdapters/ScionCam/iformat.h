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
//	File	iformat.h
//
//	definitions for image format classes
//
//
//////////////////////////////////////////////////////////////////////////////////////


#if !defined(IFORMAT_H__INCLUDED_)
#define IFORMAT_H__INCLUDED_

#include "sfwlib.h"

#ifndef	DLLExport
#ifdef	_DLL
#define	DLLExport	__declspec (dllexport)
#else
#define	DLLExport	__declspec (dllimport)
#endif
#endif


//----------------------------------------------------------------------------
//
// Ciformat class - firewire image format
//
//
//----------------------------------------------------------------------------
class DLLExport Ciformat
{
public:
	Ciformat();

public:
	virtual	~Ciformat();

protected:
	unsigned int	format;				// firewire equivalent format (-1 if none)

#define	grayscale_image	0
#define	color_image		1
	unsigned int	type;				// 0 = grayscale, 1 = color

#define	gray_order	0					// grayscale
#define rgb_order	0					// argb or rgb
#define bgr_order	1					// bgra or bgr
	unsigned int	component_order;	// component order - 
										//	0 = big endian (argb or rgb or grayscale)
										//	1 = little endian (bgra or bgr)

	unsigned int	depth;				// component depth (8/10/12/16/32)
	unsigned int	no_components;		// no of pixel components (1,3, or 4)
	unsigned int	component_size;		// component size in byte

	unsigned int	pixel_size;			// pixel size in bytes

public:
	virtual unsigned int	get_format();			// get format

	virtual unsigned int	get_image_mode();		// grayscale or color
	virtual unsigned int	get_no_components();	// number of components (1 3 or 4)
	virtual unsigned int	get_component_size();	// size of components in bytes
	virtual unsigned int	get_component_depth();	// size of component in bits
	virtual unsigned int	get_component_order();
	virtual unsigned int	get_pixel_size();		// size of pixel in bytes

	virtual bool	get_details(
							unsigned int &image_type,
							unsigned int &no_components,
							unsigned int &component_depth,
							unsigned int &component_size,
							unsigned int &component_order,
							unsigned int &pixel_size);

	virtual bool	set_format(const unsigned int format);

	virtual bool	set_format(unsigned int image_type,
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order);

public:
	static unsigned int	classify_image(unsigned int image_type,
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order);

	static bool	decompose_format(const unsigned int format,
							unsigned int &image_type,
							unsigned int &no_components,
							unsigned int &component_depth,
							unsigned int &component_size,
							unsigned int &component_order,
							unsigned int &pixel_size);
};


#endif // !defined(IFORMAT_H__INCLUDED_)
