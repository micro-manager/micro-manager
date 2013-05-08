#!/usr/bin/env python3
# vim: se ts=4 sw=4 et sta :

import sys
import xml.etree.ElementTree as ET


def read_vcproj_uuid(filename):
    tree = ET.parse(filename)
    e_proj = tree.getroot()

    assert e_proj.attrib["ProjectType"] == "Visual C++"
    if e_proj.attrib["Version"] != "9.00":
        print("{}: Version is {}".format(filename, e_proj.attrib["Version"]))

    uuid = e_proj.attrib["ProjectGUID"]
    assert uuid.startswith("{") and uuid.endswith("}")
    return uuid[1:-1]


def main():
    projs = sys.argv[1:]
    print("checking {} files".format(len(projs)))

    uuids = dict()
    for proj in projs:
        uuid = read_vcproj_uuid(proj)
        uuids.setdefault(uuid, []).append(proj)
    for uuid, projs in uuids.items():
        if len(projs) > 1:
            print("Conflicting UUIDs:")
            for proj in projs:
                print(uuid, proj)


if __name__ == "__main__":
    main()
