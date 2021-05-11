#pragma once

#ifdef __cplusplus
extern "C" {
#endif

typedef enum dp_status {
    dp_success,        ///< Function completed without errors.
    dp_memory_error,   ///< Memory allocation failed.
    dp_unknown_error,  ///< Should usually not happen.

    dp_license_error,  ///< The license key could not be validated.

    dp_file_read_error,   ///< General error when file cannot be opened or read.
    dp_file_write_error,  ///< Geeneral error when file cannot be opened or
                          ///< written
    dp_file_corrupt,      ///< File does not contain expected data

    dp_unknown_identifier,  ///< No parameters exist for given identifier
    dp_image_too_small,     ///< Image has too few pixels for reliable preparation


    dp_tiff_file_cannot_open,  ///< Tiff file cannot be opened (e.g. file
                               ///< does not exist or is locked against
                               ///< writing).
    dp_tiff_not_initialized,  ///< Trying to append to a tiff file for which the
                              ///< #dp_open_tiff was not called, or the file was
                              ///< already closed.
    dp_tiff_handle_in_use,    ///< Trying to open a tiff file with a `dp_tiff`
                              ///< pointer that is still in use.
    dp_tiff_file_update_error,  ///< Could not update tiff file (libtiff error).

} dp_status;

const char* dp_status_description(dp_status status);

#ifdef __cplusplus
}
#endif