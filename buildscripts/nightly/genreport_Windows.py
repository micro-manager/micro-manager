# Python >= 3.3

# Generate a summary report from a build log file.

# This program is necessarily written in a rather ad-hoc style, since the XML
# log written by Ant is not very structured.


import html
import os.path
import re
import sys
import traceback
from xml.etree import ElementTree

import genreport_MSBuild


def tag(t, content, is_html=False, attrs=None):
    if not is_html:
        content = html.escape(content, quote=False)
    attrlist = ("".join(" {}={}".format(k, html.escape(v))
                        for k, v in attrs.items())
                if attrs else "")
    return "<{}{}>{}</{}>".format(t, attrlist, content, t)


def build_status_report(section_sink, log_filename, build):
    title = "Build Status"
    text = "Log file: {}\n".format(os.path.abspath(log_filename))
    error = build.findall(".")[0].attrib.get("error")
    if error:
        is_error = True
        text += "Build terminated with error:\n"
        text += error
    else:
        is_error = False
        text += "Build completed without fatal error"
    section_sink.send((is_error, title, False, text))


def cpp_build_report(section_sink, architecture, src_root):
    log = ("{}/build/Release/{}/msbuild-micromanager.log".
           format(src_root, architecture))
    genreport_MSBuild.report(log, "C++ " + architecture + " ",
                             section_sink)


def java_build_report(section_sink, stage_target, architecture):
    mm_javac_tasks = stage_target.findall(".//task[@name='mm-javac']")
    for task in mm_javac_tasks:
        java_warn_messages = task.findall("./message[@priority='warn']")
        java_warn_lines = (e.findtext(".") for e in java_warn_messages)
        n_errors, n_warnings = 0, 0
        filtered_lines = list()
        for line in java_warn_lines:
            m = re.match(r"(\d+) errors", line)
            if m:
                n_errors = int(m.group(1))
                filtered_lines.append(line)
                continue
            elif re.match("1 error", line):
                n_errors = 1
                filterd_lines.append(line)
                continue
            m = re.match(r"(\d+) warnings$", line)
            if m:
                n_warnings = int(m.group(1))
                filtered_lines.append(line)
                continue
            elif re.match("1 warning$", line):
                n_warnings = 1
                filtered_lines.append(line)
                continue
            filtered_lines.append(line)

        text = "\n".join(filtered_lines).strip()
        if text:
            if n_errors:
                is_error = True
                title = "Java errors (during {} build)".format(architecture)
            elif n_warnings:
                is_error = False
                title = "Java warnings (during {} build)".format(architecture)
            else:
                is_error = False
                title = "Java messages (during {} build)".format(architecture)
            section_sink.send((is_error, title, False, text))


def clojure_build_report(section_sink, stage_target, architecture):
    # For now, cheat under the assumption that all 'java' tasks are the Clojure
    # compiler.
    mm_cljc_tasks = stage_target.findall(".//target[@name='compile']/task[@name='java']")
    for task in mm_cljc_tasks:
        clj_info_messages = task.findall("./message[@priority='info']")
        clj_info_lines = (e.findtext(".") for e in clj_info_messages)
        lines_before_stacktrace = list()
        is_error = False
        for line in clj_info_lines:
            m = re.match(r"Exception in thread ", line)
            if m:
                is_error = True
                lines_before_stacktrace.append(line)
                break
            lines_before_stacktrace.append(line)

        text = "\n".join(lines_before_stacktrace).strip()
        if is_error and text:
            title = "Clojure errors (during {} build)".format(architecture)
            section_sink.send((True, title, False, text))


def generate_report(section_sink, log_filename, build, src_root):
    build_status_report(section_sink, log_filename, build)

    # At the top level, there are task[@name='ant'] elements for unstage-all,
    # clean-all, package/upload (x64), and package/upload(Win32). Each of these
    # tasks contain an e.g. target[@name='unstage-all'] elements.

    sequential_tasks = build.findall("./task[@name='ant']")
    # Sorry, this is rather fragile...
    seen_cpp_x64 = False
    seen_cpp_Win32 = False

    for task in sequential_tasks:
        targets = task.findall("./target")
        target_names = list(target.attrib["name"] for target in targets)
        if "build-cpp" in target_names:
            assert not seen_cpp_Win32
            if seen_cpp_x64:
                arch = "Win32"
                seen_cpp_Win32 = True
            else:
                arch = "x64"
                seen_cpp_x64 = True

            cpp_build_report(section_sink, arch, src_root)
        if "stage" in target_names:
            if seen_cpp_Win32:
                arch = "Win32"
            else:
                assert seen_cpp_x64
                arch = "x64"

            java_build_report(section_sink,
                              task.findall("./target[@name='build-java']")[0],
                              arch)
            clojure_build_report(section_sink,
                                 task.findall("./target[@name='build-java']")[0],
                                 arch)


def section_writer(file):
    while True:
        is_critical, title, is_html, text = yield
        title = (tag("span", title, attrs=dict(style="color:red"))
                 if is_critical else tag("span", title))
        print(tag("h3", title, True), file=file)
        print(text if is_html else tag("pre", text), file=file)


def main(src_root, log_filename, output_filename):
    with open(output_filename, "w") as f:
        section_sink = section_writer(f)
        next(section_sink)  # Prime the coroutine

        generate_report(section_sink,
                        log_filename,
                        ElementTree.parse(log_filename),
                        src_root)


if __name__ == "__main__":
    try:
        src_root, log_filename, output_filename = sys.argv[1:]
    except:
        print("Usage: python {} src_root log_filename output_filename".
              format(sys.argv[0]))
        sys.exit(2)

    main(src_root, log_filename, output_filename)
