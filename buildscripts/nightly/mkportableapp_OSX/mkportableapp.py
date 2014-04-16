#!/usr/bin/env python3

from collections import deque
import argparse
import fnmatch
import os, os.path
import shutil
import subprocess


verbose = False


def get_deps(machofile, arch=None):
   cmd = ["/usr/bin/otool", "-L"]
   if arch:
      cmd.extend(["-arch", arch])
   cmd.append(machofile)
   output = subprocess.check_output(cmd)
   lines = output.splitlines()
   assert machofile in lines[0].decode()
   return list(line.split()[0].decode() for line in lines[1:])


def get_file_archs(file):
   output = subprocess.check_output(["/usr/bin/lipo", "-info", file])
   if (output.startswith(b"Architectures in the fat file") or
       output.startswith(b"Non-fat file")):
       archs = output.decode().split(":")[-1].split()
       return archs
   raise RuntimeError("lipo failed on file: {}".format(file))


def get_deps_and_arch_status(machofile):
   archs = get_file_archs(machofile)
   arch_deps = dict()
   all_deps = set()
   for arch in archs:
      deps = set(get_deps(machofile, arch))
      arch_deps[arch] = deps
      all_deps.update(deps)
   statuses = dict()
   for dep in all_deps:
      if all(dep in arch_deps[arch] for arch in archs):
         statuses[dep] = "all"
      else:
         statuses[dep] = " ".join(arch for arch in archs if dep in arch_deps[arch])
   return list(statuses.items())


def is_macho_file(file):
   try:
      output = subprocess.check_output(["/usr/bin/otool", "-h", file])
   except subprocess.CalledProcessError:
      return False
   if output.strip().endswith(b"not an object file"):
      return False
   try:
      output = subprocess.check_output(["/usr/bin/file", file])
   except subprocess.CalledProcessError:
      return False
   if (b"Mach-O dynamically linked shared library" in output or
       b"Mach-O 64-bit dynamically linked shared library" in output):
      return True
   if b"Mach-O bundle" in output or b"Mach-O 64-bit bundle" in output:
      return True
   return False


def set_id(machofile, new_id):
   print("{}: set id {}".format(machofile, new_id))
   subprocess.check_call(["/usr/bin/install_name_tool", "-id",
                          new_id, machofile])


def update_dep(machofile, old_path, new_path):
   print("{}: change {} => {}".format(machofile, old_path, new_path))
   subprocess.check_call(["/usr/bin/install_name_tool", "-change",
                          old_path, new_path, machofile])


def get_all_macho(dir):
   macho_files = []
   for root, dirs, files in os.walk(dir):
      for name in files:
         file = os.path.join(root, name)
         if is_macho_file(file):
            macho_files.append(os.path.relpath(file, dir))
   return macho_files
   

def process_libs(destdir, staged_seeds, srcdir,
                 path_map, forbidden_dirs):
   srcdir = os.path.abspath(srcdir)

   # scan libs in staged_libs_to_scan but not in scanned_libs
   staged_libs_to_scan = deque(staged_seeds)
   scanned_libs = set()

   # absolute_unstaged_path -> relative_staged_path
   staged_path_map = dict()

   ignored_deps = set()

   while staged_libs_to_scan:
      staged_lib = staged_libs_to_scan.popleft()
      if staged_lib in scanned_libs:
         continue
      scanned_libs.add(staged_lib)
      if verbose:
         print("scanning: {}".format(staged_lib))
      deps = get_deps_and_arch_status(os.path.join(destdir, staged_lib))
      for dep, arch_status in deps:
         if dep.startswith("@"):
            continue

         dep = os.path.realpath(dep)
         dep = os.path.normpath(dep)

         for forbidden_dir in forbidden_dirs:
            if path_is_in_dir(dep, forbidden_dir):
               raise RuntimeError("{} has dependency {} " +
                                  "in forbidden directory {}".
                                  format(staged_lib, dep, forbidden_dir))

         if os.path.basename(dep) == os.path.basename(staged_lib): # self
            update_dep(os.path.join(destdir, staged_lib), dep,
                       loader_relpath(staged_lib, staged_lib))

         elif dep in staged_path_map: # dep is already staged
            if arch_status != "all":
               raise RuntimeError("{} has dependency {} " +
                                  "only for archs: {}".
                                  format(staged_lib, dep, arch_status))
            update_dep(os.path.join(destdir, staged_lib), dep,
                       loader_relpath(staged_path_map[dep], staged_lib))

         elif path_is_in_dir(dep, srcdir): # dep is not yet staged
            if arch_status != "all":
               raise RuntimeError("{} has dependency {} " +
                                  "only for archs: {}".
                                  format(staged_lib, dep, arch_status))
            dest = map_path(os.path.relpath(dep, srcdir), path_map)
            absdest = os.path.join(destdir, dest)
            print("{}: copy from {}".format(dest, dep))
            shutil.copyfile(dep, absdest)
            # Leave out path for portable libraries
            set_id(absdest, os.path.basename(dest))
            staged_libs_to_scan.append(dest)
            staged_path_map[dep] = dest
            update_dep(os.path.join(destdir, staged_lib), dep,
                       loader_relpath(dest, staged_lib))

         else:
            ignored_deps.add(dep)
            if verbose:
               print("{}: ignoring dependency: {}".format(staged_lib, dep))

   for ignored in sorted(ignored_deps):
      print("external dependency: {}".format(ignored))

            
def map_path(file, path_map):
   # path_map is a sequence of pairs (pattern, dest), where pattern is either a
   # shell glob pattern matching files or a non-glob directory path ending in
   # '/'. If pattern is a glob pattern, the matched files are mapped directly
   # in dest. If pattern is a directory, all files under that directory are
   # matched and the the file is mapped to the path constructed by replacing
   # pattern with dest. Patterns are tried in order of appearance in path_map.
   for pattern, dest in path_map:
      if pattern.endswith(os.sep):
         if path_is_in_dir(file, pattern):
            return os.path.join(dest, os.path.relpath(file, pattern))
      else:
         if fnmatch.fnmatch(file, pattern):
            return os.path.join(dest, os.path.basename(file))
   return file


def test_map_path():
   assert map_path("a/b/c", [("a/b/", "d")]) == "d/c"
   assert map_path("a/b/c", [("a/", "d")]) == "d/b/c"
   assert map_path("a/b/c", [("a/b", "d")]) == "a/b/c"
   assert map_path("a/b/c", [("*/b/c", "d")]) == "d/c"
   assert map_path("a/b/c", [("*/b/c/", "d")]) == "a/b/c"
   assert map_path("a/b/foo.c", [("a/b/*.c", "d")]) == "d/foo.c"


def path_is_in_dir(file, dir):
   file = os.path.normpath(file)
   dir = os.path.normpath(dir) + os.sep
   return os.path.commonprefix((file, dir)) == dir


def test_path_is_in_dir():
   assert path_is_in_dir("/a/b/c", "/a/b")
   assert path_is_in_dir("/a/b/c/d", "/a/b")
   assert not path_is_in_dir("/a/b/c", "a/b")
   assert not path_is_in_dir("/a/b", "/a/b")
   assert not path_is_in_dir("/a", "/a/b")
   assert not path_is_in_dir("/a/b/c", "/a/bc")


def loader_relpath(target, loader):
   return os.path.join("@loader_path",
                       os.path.relpath(target, os.path.dirname(loader)))


def test_loader_relpath():
   assert loader_relpath("a/b/c.so", "a/d.so") == "@loader_path/b/c.so"
   assert loader_relpath("a/c.so", "a/b/d.so") == "@loader_path/../c.so"
   assert loader_relpath("a/b/c.so", "a/d/e.so") == "@loader_path/../b/c.so"


def test():
   test_map_path()
   test_path_is_in_dir()
   test_loader_relpath()


def main():
   p = argparse.ArgumentParser()
   p.add_argument("--srcdir", nargs=1,
                  help="directory from which to stage libraries")
   p.add_argument("--destdir", nargs=1,
                  help="destination root directory")
   p.add_argument("--forbid-from", nargs=1, action="append",
                  help="disallow dependencies in directory")
   p.add_argument("--map-path", nargs=1, action="append",
                  metavar="X:Y",
                  help="map file glob or directory X to directory Y")
   p.add_argument("--verbose", action="store_true")
   p.add_argument("--test", action="store_true",
                  help="run tests")
   args = p.parse_args()

   if args.verbose:
      global verbose
      verbose = True

   if args.test:
      test()
      return

   path_map = []
   for pair_str in args.map_path:
      pair = pair_str[0].split(":")
      assert len(pair) == 2
      path_map.append(pair)

   seeds = get_all_macho(args.destdir[0])
   process_libs(args.destdir[0], seeds, args.srcdir[0], path_map, args.forbid_from[0])


if __name__ == "__main__":
   main()
