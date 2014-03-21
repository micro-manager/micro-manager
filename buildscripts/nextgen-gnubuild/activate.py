#!/usr/bin/env python3

# Script for activation of next-generation GNU autotools build scripts.
#
# The new versions will be backward incompatible, so keep the existing scripts
# intact until the new version is stable. Users of existing build scripts are
# not affected.
#
# All modifications and replacements are in *.nextgen, which are copied into
# their final location by this script. MD5 sums of legacy scripts are included
# in *.nextgen, so that chagnes made to legacy scripts do not get missed.
#
#
# Usage:
#
# activate.py --activate
# -> scan all *.nextgen files and copy them to final location, removing the
#    corresponding legacy files
#
# activate.py --sum FILE1 FILE2
# -> compute MD5 sums of files and print in *.nextgen header format
#
#
# Header format for *.nextgen:
#
# %nextgen_build_filename FINAL_FILENAME
# %nextgen_build_replaces LEGACY_FILENAME MD5SUM
#
# Both directives are optional.
#
# Author: Mark Tsuchida, March 2014

import argparse
import collections
import hashlib
import os, os.path
import re
import shutil
import sys


def activate():
    all_deletes, all_renames = [], []
    for nextgen_filename in iterate_nextgen_files():
        print("reading " + nextgen_filename);
        hdr = get_nextgen_header(nextgen_filename)
        deletes, renames = process_nextgen(hdr)
        all_deletes.extend(deletes)
        all_renames.extend(renames)

    dupnames = list(x for x, y in collections.Counter(all_deletes).items() if y > 1)
    if dupnames:
        print("duplicate %nextgen_build_replaces")
        print("duplicated old filenames = ", ", ".join(dupnames))
        sys.exit(1)

    print("finished reading info; now execute")

    if (all_deletes):
        print("delete files: " + " ".join(all_deletes))
        for filename in all_deletes:
            os.unlink(filename)

    for src, dst in all_renames:
        if dst is not None:
            print("copy " + src + " -> " + dst)
            shutil.copyfile(src, dst)
        else:
            print("no new file from " + src)


def iterate_nextgen_files():
    for root, dirs, files in os.walk("."):
        for name in files:
            if name.endswith(".nextgen"):
                yield os.path.join(root, name)


class Header:

    def __init__(self, filename):
        self.filename = filename
        self.replaced = {}
        self.rename_to = None

    def set_rename_to(self, filename):
        if self.rename_to is not None:
            print("duplicate %nextgen_filename: " + filename)
            sys.exit(1)
        self.rename_to = filename

    def add_replaced(self, old_filename, md5):
        if old_filename in self.replaced:
            print("duplicate %nextgen_build_replaces: " + old_filename)
            sys.exit(1)
        self.replaced[old_filename] = md5


def get_nextgen_header(filename):
    hdr = Header(filename)
    with open(filename) as f:
        for line in f:
            m = re.match(r"#\s+%nextgen_build_([a-z]+)\s*=\s*(\S.*)$", line.strip())
            if m:
                if m.group(1) == "filename":
                    hdr.set_rename_to(m.group(2).strip())
                    rename_to = m.group(2).strip()
                elif m.group(1) == "replaces":
                    old_filename, md5 = m.group(2).split()
                    hdr.add_replaced(old_filename, md5)
    return hdr


def process_nextgen(hdr):
    dirname = os.path.dirname(hdr.filename)
    deletes = []
    for old_filename, old_md5 in hdr.replaced.items():
        old_path = os.path.join(dirname, old_filename)
        if get_md5(old_path) != old_md5:
            print("MD5 does not match: " + old_path +
                " (to be replaced by: " + hdr.filename + ")")
            sys.exit(1)
        deletes.append(old_path)
    new_path = os.path.join(dirname, hdr.rename_to) if hdr.rename_to else None
    return deletes, [(hdr.filename, new_path)]


def get_md5(filename):
    m = hashlib.md5()
    with open(filename, "rb") as f:
        m.update(f.read())
    return m.hexdigest()


def print_sums(filenames):
    for filename in filenames:
        print("# %nextgen_build_replaces = " + filename + " " +
              get_md5(filename))


def run():
    p = argparse.ArgumentParser()
    p.add_argument("--activate", action="store_true")
    p.add_argument("--sum", nargs="+")
    args = p.parse_args()
    if args.activate and args.sum:
        print("multiple verbs not allowed")
        sys.exit(1)
    if args.activate:
        activate()
    elif args.sum:
        print_sums(args.sum)
    else:
        print("nothing to do")


if __name__ == "__main__":
    run()
