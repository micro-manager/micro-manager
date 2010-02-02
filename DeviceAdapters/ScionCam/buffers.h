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
//	File	buffers.h
//
//	definitions for image buffer classes
//
//
//////////////////////////////////////////////////////////////////////////////////////


#if !defined(BUFFERS_H__INCLUDED_)
#define BUFFERS_H__INCLUDED_

#include "sfwlib.h"
#include "iformat.h"

#ifndef	DLLExport
#ifdef	_DLL
#define	DLLExport	__declspec (dllexport)
#else
#define	DLLExport	__declspec (dllimport)
#endif
#endif


//----------------------------------------------------------------------------
//
// Cimage_core class - base class for images
//
//						contains dimension information and
//						definitions common to all memory images
//
//						constraints and image allocation are
//						handled by sub classes
//
//----------------------------------------------------------------------------
class DLLExport Cimage_core
{
public:
	Cimage_core();

public:
	virtual	~Cimage_core();

protected:
#define	grayscale_image	0
#define	color_image		1
	unsigned int	type;				// 0 = grayscale, 1 = color

	unsigned int	format;				// firewire equivalent format (-1 if none)

	unsigned char	*bp;				// image buffer pointer
	RECT			bounds;				// bounds
	unsigned int	size;				// size of image in bytes (can be smaller than the buffer)
	unsigned int	bsize;				// size of buffer in bytes
	unsigned int	rowbytes;			// no of bytes for image row

	unsigned int	depth;				// component depth (8/10/12/16/32)
	unsigned int	no_components;		// no of pixel components (1,3, or 4)
	unsigned int	component_size;		// component size in bytes

#define rgb_order	0					// argb or rgb
#define bgr_order	1					// bgra or bgr
	unsigned int	component_order;	// component order - 
										//	0 = big endian (argb or rgb)
										//	1 = little endian (bgra or bgr)

	unsigned int	pixel_size;			// pixel size in bytes


public:
	virtual	unsigned int	get_width() const;		// image width (pixels)
	virtual	unsigned int	get_height()const;		// image height (rows)
	virtual	RECT			get_image_rect() const;	// get image rect

	virtual	unsigned int	get_rowbytes() const;	// no of bytes for image row
	virtual	unsigned int	get_size() const;		// image size (can be smaller than buffer)
	virtual	unsigned int	get_bsize() const;		// buffer size

	virtual	unsigned int	get_no_components() const;
	virtual	unsigned int	get_component_size() const;
	virtual	unsigned int	get_component_depth() const;
	virtual	unsigned int	get_component_order() const;
	virtual	unsigned int	get_pixel_size() const;

	virtual	unsigned int	get_image_type() const;	// grayscale or color
	virtual	unsigned int	get_format() const;		// get format (returns -1 for unknown)
	virtual	void *			get_bp() const;			// get buffer pointer (image buffer)

	virtual	void	close_image();
	virtual	void	clear_image();
};


//----------------------------------------------------------------------------
//
// drawable image class
//
//----------------------------------------------------------------------------
class DLLExport Cimage_drawable : public Cimage_core
{
public:
	Cimage_drawable();

public:
	virtual	~Cimage_drawable();

protected:
	BITMAPINFO		*info;				// offscreen bitmap header
	HDC				hdc;				// device context						
	HBITMAP			hbmp;				// bitmap handle

public:
	virtual	HDC				get_hdc() const;		// get drawing context
	virtual	BITMAPINFO *	get_binfo() const;		// get bitmap information object

	// draw bitmap into this image
	virtual	void		draw_bitmap(Cimage_drawable *sp, RECT source, RECT dest);

	virtual	void		draw_bitmap(BITMAPINFO *source_map, 
							char *buffer,
							RECT source, RECT dest);

	// copy (draw) the image in window
	virtual	void		copy_onscreen(HWND image_win, RECT source_rect, RECT dest_rect);
	virtual	void		copy_onscreen(HWND image_win, 
							RECT source_rect, RECT dest_rect, HPALETTE palette);

	// draw xhair on image
	virtual	void		draw_xhair(const RECT &irect, unsigned int scale);
	virtual	void		draw_xhair(const RECT &irect, HBITMAP pattern);
	virtual	void		draw_xhair(const RECT &irect, HBITMAP pattern, unsigned int scale);

	// draw grid on image
	virtual	void		draw_grid(const RECT &irect, unsigned int scale);
	virtual	void		draw_grid(const RECT &irect, HBITMAP pattern);
	virtual	void		draw_grid(const RECT &irect, HBITMAP pattern, unsigned int scale);

};


//----------------------------------------------------------------------------
//
// Cimage class -	image
//
//----------------------------------------------------------------------------
class DLLExport Cimage : public Cimage_core
{
public:
	Cimage();
	virtual	~Cimage();

public:
	// create image as described
	virtual	int			create_image(
							unsigned int image_type,
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order,
							int width, int height);

	// modify image parameters
	virtual	int			modify_image(
							unsigned int os_mode,
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order,
							int width, int height);

	// modify image size (keep same mode, components and order)
	virtual	int			resize_image(int width, int height);

	// create image for desired format
	virtual	int			create_image(
							unsigned int format, int width, int height);

	// modify image parameters to match format
	virtual	int			modify_image(
							unsigned int format, int width, int height);

	// modify image parameters to match format
	virtual	int			modify_image(
							unsigned int format);

	// close contained image
	virtual	void		close_image();

	// clear image
	virtual	void		clear_image();

};


//----------------------------------------------------------------------------
//
// Cfw_image class	-	firewire buffer image
//
//						image with associated bitmap info structure
//						for bitmap copies
//
//----------------------------------------------------------------------------

class DLLExport Cfw_image : public Cimage
{
public:
	Cfw_image();
	virtual	~Cfw_image();

protected:
	bool			info_valid;			// bitmap header valid
	BITMAPINFO		*info;				// offscreen bitmap header

public:
	// create a graysacle or native color displayable image
//	int			create_image(
//						unsigned int image_type,
//						int width, int height);

	// modify image parameters
//	int			resize_image(
//						unsigned int image_type,
//						int width, int height);


	// create image as described
	virtual	int			create_image(
							unsigned int image_type,
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order,
							int width, int height);

	// modify image parameters
	virtual	int			modify_image(
							unsigned int image_type,
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order,
							int width, int height);

	// modify image size (keep same mode, components and order)
	virtual	int			resize_image(int width, int height);

	// create image for desired format
	virtual	int			create_image(
							unsigned int format, int width, int height);

	// modify image parameters to match format
	virtual	int			modify_image(
							unsigned int format, int width, int height);

	// modify image parameters to match format
	virtual int			modify_image(
							unsigned int format);


	// close contained image
	virtual	void		close_image();

	// clear image
	virtual	void		clear_image();

	// get bitmap information object
	virtual	BITMAPINFO *	get_binfo();
};


//----------------------------------------------------------------------------
//
// Cos_image class -	offscreen image (drawable)
//
//----------------------------------------------------------------------------
class DLLExport Cos_image : public Cimage_drawable
{
public:
	Cos_image();


public:
	virtual	~Cos_image();

	// create offscreen grayscale or color native image for display 
	virtual	int			create_image(
							unsigned int os_mode,
							int os_width, int os_height);

	// close contained image
	virtual	void		close_image();

	// clear image
	virtual	void		clear_image();

	// draw drawable image into this image
	virtual	void		draw_image(Cimage_drawable *ip);

	// draw firewire image into this image (draw to offscreen buffer)
	virtual	void		draw_image(Cfw_image *ip);
};


//----------------------------------------------------------------------------
// 
// Cframe_info class - container for sfw_frame_t firewire buffer descriptors
//
//----------------------------------------------------------------------------
class DLLExport Cframe_info
{
public:
	Cframe_info();

public:
	virtual	~Cframe_info();

protected:
	// buffers

	sfw_frame_t		*fp;				// frame list
	unsigned  int	no_frames;			// no of frames in list

public:
	virtual	int				create(unsigned int no_frames);
	virtual	int				create(Cimage &image);
	virtual	int				create(Cfw_image &image);
	virtual	int				create(Cfw_image *image_array, unsigned int no_frames);
	virtual	int				release();

	virtual	int				set(Cimage &image);
	virtual	int				set(Cimage &image, unsigned int index);
	virtual	int				set(Cfw_image &image);
	virtual	int				set(Cfw_image &image, unsigned int index);

	virtual	unsigned int	get_no_frames();
	virtual	sfw_frame_t *	get_frame_info();
	virtual	sfw_frame_t *	get_frame_info(unsigned int index);
	virtual	sfw_frame_t *	get_frame_list();
};


//----------------------------------------------------------------------------
// 
// Cbuffer_pool - container for firewire buffer pool
//
//----------------------------------------------------------------------------
class DLLExport Cbuffer_pool
{
public:
	Cbuffer_pool();


public:
	virtual	~Cbuffer_pool();

protected:
	// buffers
	Cfw_image		*bpool;				// buffer pool
	Cframe_info		frames;				// frame list for buffer pool

	// control
	unsigned int	no_buffers;			// number of buffers in pool
	unsigned int	bpool_assigned;		// pool has been assigned to device

	int				max_width;				// max buffer width (pixels)
	int				max_height;				// max buffer height
	unsigned int	max_no_components;		// max number of components
	unsigned int	max_component_depth;	// max component depth

	int				width;				// current buffer width (pixels)
	int				height;				// current buffer height (pixels)
	unsigned int	image_type;			// image type
	unsigned int	no_components;		// number of components
	unsigned int	component_depth;	// component depth
	unsigned int	component_order;	// component order

	SFW_CAMERA_HANDLE	fwp;			// firewire camera assigned to pool

public:
	virtual int			create_pool(unsigned int image_type, 
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order,
							int max_width, int max_height,
							unsigned int no_buffers);

	virtual	int			modify(unsigned int image_type,
							unsigned int no_components,
							unsigned int component_depth,
							unsigned int component_order,
							int width, int height);

	virtual	int			modify(unsigned int format,
							int width, int height);

	virtual	int			modify(unsigned int format);

	virtual	int				resize(int width, int height);

	virtual	int			assign(SFW_CAMERA_HANDLE fwp);
	virtual	void		release();
	virtual	void		close();

	virtual	unsigned int	get_no_buffers() const;
	virtual	Cfw_image *		get_buffer(unsigned int index);
	virtual	sfw_frame_t *	get_frame_info(unsigned int index);

};

#endif // !defined(BUFFERS_H__INCLUDED_)
