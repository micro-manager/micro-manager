"""
Library for reading multiresolution micro-magellan
"""
import os
import mmap
import numpy as np
import sys
import json
import platform


class MagellanMultipageTiffReader:
    # Class corresponsing to a single multipage tiff file in a Micro-Magellan dataset. Pass the full path of the TIFF to
    # instantiate and call close() when finished
    # TIFF constants
    WIDTH = 256
    HEIGHT = 257
    BITS_PER_SAMPLE = 258
    COMPRESSION = 259
    PHOTOMETRIC_INTERPRETATION = 262
    IMAGE_DESCRIPTION = 270
    STRIP_OFFSETS = 273
    SAMPLES_PER_PIXEL = 277
    ROWS_PER_STRIP = 278
    STRIP_BYTE_COUNTS = 279
    X_RESOLUTION = 282
    Y_RESOLUTION = 283
    RESOLUTION_UNIT = 296
    MM_METADATA = 51123

    # file format constants
    INDEX_MAP_OFFSET_HEADER = 54773648
    INDEX_MAP_HEADER = 3453623
    SUMMARY_MD_HEADER = 2355492

    def __init__(self, tiff_path):
        self.file = open(tiff_path, 'rb')
        # memory map the entire file
        if platform.system() == 'Windows':
            self.mmap_file = mmap.mmap(self.file.fileno(), 0, access=mmap.ACCESS_READ)
        else:
            self.mmap_file = mmap.mmap(self.file.fileno(), 0, prot=mmap.PROT_READ)
        self.summary_md, self.index_tree, self.first_ifd_offset = self._read_header()
        # get important metadata fields
        self.width = self.summary_md['Width']
        self.height = self.summary_md['Height']

    def close(self):
        self.mmap_file.close()
        self.file.close()

    def _read_header(self):
        """
        :param file:
        :return: dictionary with summary metadata, nested dictionary of byte offsets of TIFF Image File Directories with
        keys [channel_index][z_index][frame_index][position_index], int byte offset of first image IFD
        """
        # read standard tiff header
        if self.mmap_file[:2] == b'\x4d\x4d':
            # Big endian
            if sys.byteorder != 'big':
                raise Exception("Potential issue with mismatched endian-ness")
        elif self.mmap_file[:2] == b'\x49\x49':
            # little endian
            if sys.byteorder != 'little':
                raise Exception("Potential issue with mismatched endian-ness")
        else:
            raise Exception('Endian type not specified correctly')
        if np.frombuffer(self.mmap_file[2:4], dtype=np.uint16)[0] != 42:
            raise Exception('Tiff magic 42 missing')
        first_ifd_offset = np.frombuffer(self.mmap_file[4:8], dtype=np.uint32)[0]

        # read custom stuff: summary md, index map
        index_map_offset_header, index_map_offset = np.frombuffer(self.mmap_file[8:16], dtype=np.uint32)
        if index_map_offset_header != self.INDEX_MAP_OFFSET_HEADER:
            raise Exception('Index map offset header wrong')
        summary_md_header, summary_md_length = np.frombuffer(self.mmap_file[32:40], dtype=np.uint32)
        if summary_md_header != self.SUMMARY_MD_HEADER:
            raise Exception('Index map offset header wrong')
        summary_md = json.loads(self.mmap_file[40:40 + summary_md_length])
        index_map_header, index_map_length = np.frombuffer(
            self.mmap_file[40 + summary_md_length:48 + summary_md_length],
            dtype=np.uint32)
        if index_map_header != self.INDEX_MAP_HEADER:
            raise Exception('Index map header incorrect')
        # get index map as nested list of ints
        index_map_keys = np.array([[int(cztp) for i, cztp in enumerate(entry) if i < 4]
                                   for entry in np.reshape(np.frombuffer(self.mmap_file[48 + summary_md_length:48 +
                                    summary_md_length + index_map_length * 20], dtype=np.int32), [-1, 5])])
        index_map_byte_offsets = np.array([[int(offset) for i, offset in enumerate(entry) if i == 4] for entry in
            np.reshape(np.frombuffer(self.mmap_file[48 + summary_md_length:48 + summary_md_length + index_map_length *
                            20], dtype=np.uint32), [-1, 5])])
        index_map = np.concatenate((index_map_keys, index_map_byte_offsets), axis=1)
        string_key_index_map = {'_'.join([str(ind) for ind in entry[:4]]): entry[4] for entry in index_map}
        # unpack into a tree (i.e. nested dicts)
        index_tree = {}
        for c_index in set([line[0] for line in index_map]):
            for z_index in set([line[1] for line in index_map]):
                for t_index in set([line[2] for line in index_map]):
                    for p_index in set([line[3] for line in index_map]):
                        if '_'.join([str(c_index), str(z_index), str(t_index),
                                     str(p_index)]) in string_key_index_map.keys():
                            # fill out tree as needed
                            if c_index not in index_tree.keys():
                                index_tree[c_index] = {}
                            if z_index not in index_tree[c_index].keys():
                                index_tree[c_index][z_index] = {}
                            if t_index not in index_tree[c_index][z_index].keys():
                                index_tree[c_index][z_index][t_index] = {}
                            index_tree[c_index][z_index][t_index][p_index] = string_key_index_map[
                                '_'.join([str(c_index), str(z_index), str(t_index), str(p_index)])]
        return summary_md, index_tree, first_ifd_offset

    def _read(self, start, end):
        """
        Convert to python ints
        """
        return self.mmap_file[int(start):int(end)]

    def _read_ifd(self, byte_offset):
        """
        Read image file directory. First two bytes are number of entries (n), next n*12 bytes are individual IFDs, final 4
        bytes are next IFD offset location
        :return: dictionary with fields needed for reading
        """
        num_entries = np.frombuffer(self._read(byte_offset, byte_offset + 2), dtype=np.uint16)[0]
        info = {}
        for i in range(num_entries):
            tag, type = np.frombuffer(self._read(byte_offset + 2 + i * 12, byte_offset + 2 + i * 12 + 4),
                                      dtype=np.uint16)
            count = \
            np.frombuffer(self._read(byte_offset + 2 + i * 12 + 4, byte_offset + 2 + i * 12 + 8), dtype=np.uint32)[0]
            if type == 3 and count == 1:
                value = \
                np.frombuffer(self._read(byte_offset + 2 + i * 12 + 8, byte_offset + 2 + i * 12 + 10), dtype=np.uint16)[
                    0]
            else:
                value = \
                np.frombuffer(self._read(byte_offset + 2 + i * 12 + 8, byte_offset + 2 + i * 12 + 12), dtype=np.uint32)[
                    0]
            # save important tags for reading images
            if tag == self.MM_METADATA:
                info['md_offset'] = value
                info['md_length'] = count
            elif tag == self.STRIP_OFFSETS:
                info['pixel_offset'] = value
            elif tag == self.STRIP_BYTE_COUNTS:
                info['bytes_per_image'] = value
        info['next_ifd_offset'] = np.frombuffer(self._read(byte_offset + num_entries * 12 + 2,
                                                           byte_offset + num_entries * 12 + 6), dtype=np.uint32)[0]
        if 'bytes_per_image' not in info or 'pixel_offset' not in info:
            raise Exception('Missing tags in IFD entry, file may be corrupted')
        return info

    def _read_pixels(self, offset, length):
        if self.width * self.height * 2 == length:
            pixels = np.frombuffer(self._read(offset, offset + length), dtype=np.uint16)
        elif self.width * self.height == length:
            pixels = np.frombuffer(self._read(offset, offset + length), dtype=np.uint8)
        else:
            raise Exception('Unknown pixel type')
        return np.reshape(pixels, [self.height, self.width])

    def read_metadata(self, channel_index, z_index, t_index, pos_index):
        ifd_offset = self.index_tree[channel_index][z_index][t_index][pos_index]
        ifd_data = self._read_ifd(ifd_offset)
        metadata = json.loads(self._read(ifd_data['md_offset'], ifd_data['md_offset'] + ifd_data['md_length']))
        return metadata

    def read_image(self, channel_index, z_index, t_index, pos_index, read_metadata=False):
        ifd_offset = self.index_tree[channel_index][z_index][t_index][pos_index]
        ifd_data = self._read_ifd(ifd_offset)
        image = self._read_pixels(ifd_data['pixel_offset'], ifd_data['bytes_per_image'])
        if read_metadata:
            metadata = json.loads(self._read(ifd_data['md_offset'], ifd_data['md_offset'] + ifd_data['md_length']))
            return image, metadata
        return image


class MagellanResolutionLevel:

    def __init__(self, path):
        """
        open all tiff files in directory, keep them in a list, and a tree based on image indices
        :param path:
        """
        tiff_names = [os.path.join(path, tiff) for tiff in os.listdir(path) if tiff.endswith('.tif')]
        self.reader_list = []
        self.reader_tree = {}
        #populate list of readers and tree mapping indices to readers
        for tiff in tiff_names:
            reader = MagellanMultipageTiffReader(tiff)
            self.reader_list.append(reader)
            it = reader.index_tree
            for c in it.keys():
                if c not in self.reader_tree.keys():
                    self.reader_tree[c] = {}
                for z in it[c].keys():
                    if z not in self.reader_tree[c].keys():
                        self.reader_tree[c][z] = {}
                    for t in it[c][z].keys():
                        if t not in self.reader_tree[c][z].keys():
                            self.reader_tree[c][z][t] = {}
                        for p in it[c][z][t].keys():
                            self.reader_tree[c][z][t][p] = reader

    def read_image(self, channel_index=0, z_index=0, t_index=0, pos_index=0, read_metadata=False):
        # determine which reader contains the image
        reader = self.reader_tree[channel_index][z_index][t_index][pos_index]
        return reader.read_image(channel_index, z_index, t_index, pos_index, read_metadata)

    def read_metadata(self, channel_index=0, z_index=0, t_index=0, pos_index=0):
        # determine which reader contains the image
        reader = self.reader_tree[channel_index][z_index][t_index][pos_index]
        return reader.read_metadata(channel_index, z_index, t_index, pos_index)


    def close(self):
        for reader in self.reader_list:
            reader.close()


class MagellanDataset:
    """
    Class that opens a Micro-Magellan dataset. Only works for regular acquisitions (i.e. not explore acquisitions)
    """

    def __init__(self, dataset_path, full_res_only=True):
        res_dirs = [dI for dI in os.listdir(dataset_path) if os.path.isdir(os.path.join(dataset_path, dI))]
        # map from downsample factor to datset
        self.res_levels = {}
        if 'Full resolution' not in res_dirs:
            raise Exception('Couldn\'t find full resolution directory. Is this the correct path to a Magellan dataset?')
        for res_dir in res_dirs:
            if full_res_only and res_dir != 'Full resolution':
                continue
            res_dir_path = os.path.join(dataset_path, res_dir)
            res_level = MagellanResolutionLevel(res_dir_path)
            if res_dir == 'Full resolution':
                self.res_levels[1] = res_level
                # get summary metadata and index tree from full resolution image
                self.summary_metadata = res_level.reader_list[0].summary_md
                # store some fields explicitly for easy access
                self.pixel_size_xy_um = self.summary_metadata['PixelSize_um']
                self.pixel_size_z_um = self.summary_metadata['z-step_um']
                self.image_width = res_level.reader_list[0].width
                self.image_height = res_level.reader_list[0].height
                self.channel_names = self.summary_metadata['ChNames']
                self.c_z_t_p_tree = res_level.reader_tree
                # index tree is in c - z - t - p hierarchy, get all used indices to calcualte other orderings
                channels = set(self.c_z_t_p_tree.keys())
                slices = set()
                frames = set()
                positions = set()
                for c in self.c_z_t_p_tree.keys():
                    for z in self.c_z_t_p_tree[c]:
                        slices.add(z)
                        for t in self.c_z_t_p_tree[c][z]:
                            frames.add(t)
                            for p in self.c_z_t_p_tree[c][z][t]:
                                positions.add(p)
                # populate tree in a different ordering
                self.p_t_z_c_tree = {}
                for p in positions:
                    for t in frames:
                        for z in slices:
                            for c in channels:
                                if z in self.c_z_t_p_tree[c] and t in self.c_z_t_p_tree[c][z] and p in \
                                        self.c_z_t_p_tree[c][z][t]:
                                    if p not in self.p_t_z_c_tree:
                                        self.p_t_z_c_tree[p] = {}
                                    if t not in self.p_t_z_c_tree[p]:
                                        self.p_t_z_c_tree[p][t] = {}
                                    if z not in self.p_t_z_c_tree[p][t]:
                                        self.p_t_z_c_tree[p][t][z] = {}
                                    self.p_t_z_c_tree[p][t][z][c] = self.c_z_t_p_tree[c][z][t][p]
                #get row, col as a function of position index
                self.row_col_tuples = [(pos['GridRowIndex'], pos['GridColumnIndex']) for pos in
                                  self.summary_metadata['InitialPositionList']]
            else:
                self.res_levels[int(res_dir.split('x')[1])] = res_level

    def _channel_name_to_index(self, channel_name):
        if channel_name not in self.channel_names:
            raise Exception('Invalid channel name')
        return self.channel_names.index(channel_name)

    def has_image(self, channel_name=None, channel_index=0, z_index=0, t_index=0, pos_index=0, downsample_factor=1):
        """
        Check if this image is present in the dataset
        :param channel_name: Overrides channel index if supplied
        :param channel_index:
        :param z_index:
        :param t_index:
        :param pos_index:
        :param downsample_factor:
        :return:
        """
        if channel_name is not None:
            channel_index = self._channel_name_to_index(channel_name)
        if channel_index in self.c_z_t_p_tree and z_index in self.c_z_t_p_tree[channel_index] and t_index in \
                self.c_z_t_p_tree[
                    channel_index][z_index] and pos_index in self.c_z_t_p_tree[channel_index][z_index][t_index]:
            return True
        return False

    def read_image(self, channel_name=None, channel_index=0, z_index=0, t_index=0, pos_index=0, read_metadata=False,
                   downsample_factor=1):
        """
        Read image data as numpy array
        :param channel_name: Overrides channel index if supplied
        :param channel_index:
        :param z_index:
        :param t_index:
        :param pos_index:
        :param read_metadata: if True, return a tuple with dict of image metadata as second element
        :param downsample_factor: 1 is full resolution, lower resolutions are powers of 2 if available
        :return: image as 2D numpy array, or tuple with image and image metadata as dict
        """
        if channel_name is not None:
            channel_index = self._channel_name_to_index(channel_name)
        res_level = self.res_levels[downsample_factor]
        return res_level.read_image(channel_index, z_index, t_index, pos_index, read_metadata)

    def read_metadata(self, channel_name=None, channel_index=0, z_index=0, t_index=0, pos_index=0, read_metadata=False,
                   downsample_factor=1):
        """
        Read metadata only. Faster than using read_image to retireve metadata
        :param channel_name: Overrides channel index if supplied
        :param channel_index:
        :param z_index:
        :param t_index:
        :param pos_index:
        :param downsample_factor: 1 is full resolution, lower resolutions are powers of 2 if available
        :return: metadata as dict
        """
        if channel_name is not None:
            channel_index = self._channel_name_to_index(channel_name)
        res_level = self.res_levels[downsample_factor]
        return res_level.read_metadata(channel_index, z_index, t_index, pos_index)

    def close(self):
        for res_level in self.res_levels:
            res_level.close()

    def get_z_slices_at(self, position_index, time_index=0):
        """
        return list of z slice indices (i.e. focal planes) at the given XY position
        :param position_index:
        :return:
        """
        return list(self.p_t_z_c_tree[position_index][time_index].keys())

    def get_min_max_z_index(self):
        """
        get min and max z indices over all positions
        """
        min_z = 1e100
        max_z = -1e000
        for p_index in self.p_t_z_c_tree.keys():
            for t_index in self.p_t_z_c_tree[p_index].keys():
                new_zs = list(self.p_t_z_c_tree[p_index][t_index].keys())
                min_z = min(min_z, *new_zs)
                max_z = max(max_z, *new_zs)
        return min_z, max_z

    def get_num_xy_positions(self):
        """
        :return: total number of xy positons in data set
        """
        return len(list(self.p_t_z_c_tree.keys()))


    def get_num_rows_and_cols(self):
        """
        Note doesn't  work with explore acquisitions because initial position list isn't populated here
        :return: tuple with total number of rows, total number of cols in dataset
        """
        row_col_tuples = [(pos['GridRowIndex'], pos['GridColumnIndex']) for pos in
                          self.summary_metadata['InitialPositionList']]
        row_indices = list(set(row for row, col in row_col_tuples))
        col_indices = list(set(col for row, col in row_col_tuples))
        num_rows = max(row_indices) + 1
        num_cols = max(col_indices) + 1
        return num_rows, num_cols

    def get_num_frames(self):
        frames = set()
        for t_tree in self.p_t_z_c_tree.values():
            frames.update(t_tree.keys())
        return max(frames) + 1