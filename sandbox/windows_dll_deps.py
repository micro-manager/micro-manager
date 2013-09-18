# Python 3.3

# You must first run vsvarsall.bat from Visual Studio to add 'dumpbin' to the
# path.

import os, os.path
import subprocess
import sys

def visit_dir(results, dirname, filenames):
    for filename in filenames:
        if os.path.splitext(filename)[1].lower() == ".dll":
            process_file(results, os.path.join(dirname, filename))


def process_file(results, filename):
    text = subprocess.check_output("dumpbin /dependents " + filename, shell=True, universal_newlines=True)
    result = parse_deps(text)

    if result["is_dll"]:
        results[filename] = result

        print(filename + " requires:")
        for dep in sorted(result.get("deps", list())):
            print("    " + dep)
        for dep in sorted(result.get("delay_deps", list())):
            print("    " + dep + " (delay loaded)")


def parse_deps(text):
    result = dict()
    parser = deps_parser(result)
    next(parser)
    for line in text.splitlines():
        parser.send(line.rstrip("\n"))
    parser.close()
    return result


def deps_parser(resultdict):
    line = yield
    while "Microsoft" in line:
        line = yield
    while not line.strip():
        line = yield

    assert line.startswith("Dump of file")
    line = yield
    while not line.strip():
        line = yield

    assert line.startswith("File Type: ")
    if not line.strip().endswith("DLL"):
        resultdict["is_dll"] = False
        return
    resultdict["is_dll"] = True
    line = yield

    while not line.strip():
        line = yield

    if line.startswith("  Image has the following dependencies:"):
        line = yield from read_deps_list(resultdict, "deps")

    while not line.strip():
        line = yield

    if line.startswith("  Image has the following delay load dependencies:"):
        line = yield from read_deps_list(resultdict, "delay_deps")

    while not line.strip():
        line = yield

    if not line.startswith("  Summary"):
        assert False, "Unknown section(s) in output of dumpbin /dependents"

    while True:
        yield


def read_deps_list(resultdict, dictkey):
    line = ""
    while not line.strip():
        line = yield
    while line.rstrip().startswith("    "):
        dep = line.strip().lower()
        assert dep
        resultdict.setdefault(dictkey, list()).append(dep)
        line = yield
    return line


if __name__ == "__main__":
    if len(sys.argv) > 1:
        path = sys.argv[1]
    else:
        path = os.curdir

    results = dict()
    for root, dirs, files in os.walk(path):
        visit_dir(results, root, files)

    # Invert the mapping
    inverted = dict()
    for our_dll, result in results.items():
        for external_dll in result.get("deps", list()):
            inverted.setdefault(external_dll, list()).append(our_dll)
        for external_dll in result.get("delay_deps", list()):
            inverted.setdefault(external_dll, list()).append(our_dll)
    print()
    for dll in sorted(inverted.keys()):
        print(dll + " required by:")
        for our_dll in sorted(inverted[dll]):
            print("    " + our_dll)
