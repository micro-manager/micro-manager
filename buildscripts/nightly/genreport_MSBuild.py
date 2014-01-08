# Python >= 3.3

# Generate a summary report from MSBuild log files

# Requires number-prefixed output from multiprocessor build (MSBuild /m).


import html
import ntpath
import re
import sys


def tag(t, content, is_html=False, attrs=None):
    if not is_html:
        content = html.escape(content, quote=False)
    attrlist = ("".join(" {}={}".format(k, html.escape(v))
                        for k, v in attrs.items())
                if attrs else "")
    return "<{}{}>{}</{}>".format(t, attrlist, content, t)


#
# Log items
#

class Entry:
    """Event entry prefixed with project ordinal"""

    def __init__(self, ordinal, repeat_count, lines):
        self.ordinal = ordinal
        self.repeat_count = repeat_count
        self.lines = lines


class TargetChainItem:
    """First lines in each block in the summary"""

    def __init__(self, text):
        m = re.match(r'^("([^"]*)" )?\((.*) target\)( \((\d)+\))?$', text)
        assert m
        self.filename = m.group(2)
        self.target = m.group(3)
        self.ordinal = int(m.group(5)) if m.group(5) else None


class Message:
    """Messages in summary blocks"""

    def __init__(self, text):
        m = re.match(r"^((([A-Za-z]:)?[^:]+): (fatal error|error|warning) ([^ :]+): (.+)) \[([^]]+)\]$",
                     text)
        assert m
        self.text = m.group(1)
        self.item = m.group(2).strip()
        self.kind = m.group(4)
        self.code = m.group(5)
        self.message = m.group(6)
        self.project = m.group(7)


#
# Coroutine decorators
#

def coroutine(func):
    """Toplevel (directly instantiated) coroutine"""
    def decorated(*args, **kwargs):
        coro = func(*args, **kwargs)
        next(coro)
        return coro
    return decorated


def subcoroutine(func):
    """Subordinate coroutine (instantiated via 'yield from')"""
    return func


@coroutine
def null_sink():
    while True:
        yield


#
# Parser
#

@subcoroutine
def read_entry(line, target):
    entry_lines = list()
    m = re.match(r"^ *([:0-9]+)>(.*)$", line)
    assert m
    index = m.group(1)
    ordinal, repeat = index.split(":") if ":" in index else (index, "1")
    ordinal, repeat = int(ordinal), int(repeat)
    entry_lines.append(m.group(2))
    while True:
        line = yield
        m = re.match(r"^ {7}(.*)$", line)
        if m:
            entry_lines.append(m.group(1))
            continue
        break
    target.send(Entry(ordinal, repeat, entry_lines))
    return line


@subcoroutine
def read_entries(line, target):
    while True:
        # The ordinal prefix is 6 columns (excluding the '>'). However, when a
        # colon-separated suffix (repeat count) is appended, the primary
        # ordinal is given 3 columns; when the repeat count reaches 100, the
        # whole prefix can occupy 7 columns.
        if not (re.match(r"^[ :0-9]{6}>", line) or
                re.match(r"^[ 0-9]{3}:[0-9]{3}>", line)):
            return line
        line = yield from read_entry(line, target)


@subcoroutine
def read_summary_block(line, target):
    while not line.strip():
        line = yield

    target_chain = list()
    messages = list()

    while True:
        m = re.match(r"^ {7}(.*) -> *$", line)
        if m:
            target_chain.append(TargetChainItem(m.group(1)))
            line = yield
            continue
        break
    if not target_chain:
        return line, False

    while True:
        m = re.match(r"^ {9}(.*)$", line)
        if m:
            messages.append(Message(m.group(1)))
            line = yield
            continue
        break
    target.send((target_chain, messages))
    return line, True


@subcoroutine
def read_summary(line, target):
    while True:
        line, found_entry = yield from read_summary_block(line, target)
        if not found_entry:
            return line


@coroutine
def read_log(entry_target, summary_target):
    line = yield
    assert line.startswith("Build started ")

    line = yield
    line = yield from read_entries(line, entry_target)

    while not line.strip():
        line = yield

    assert line.startswith("Build ")
    line = yield

    while not line.strip():
        line = yield

    line = yield from read_summary(line, summary_target)

    m = re.match(r"^ {4}(\d)+ Warning\(s\)$", line)
    assert m
    warning_count = int(m.group(1))
    line = yield

    m = re.match(r"^ {4}(\d)+ Error\(s\)$", line)
    assert m
    error_count = int(m.group(1))
    line = yield

    while True:
        yield


#
# Generate summary from parsed items
#

def message_filter(message):
    # "PDB 'filename' was not found with 'object/library' or at 'path'; linking
    # object as if no debug info" - this warning cannot be suppressed with a
    # linker flag, but is usually due to third-party libraries that we cannot
    # fix. Since it is not expected to affect us, suppress it.
    if message.code == "LNK4099":
        return False
    return True


@coroutine
def process_summary(section_sink):
    fatal_errors = dict()
    errors = dict()
    warnings = dict()
    warn_stats = dict()
    suppressed_warn_stats = dict()

    while True:
        try:
            target_chain, messages = yield
        except GeneratorExit:
            break

        for message in messages:
            if message.kind == "warning":
                if message_filter(message):
                    warn_stats[message.code] = warn_stats.get(message.code, 0) + 1
                else:
                    suppressed_warn_stats[message.code] = \
                            suppressed_warn_stats.get(message.code, 0) + 1

        # Sort messages by kind and project

        assert target_chain[0].filename
        assert target_chain[0].ordinal == 1

        solution_path = target_chain[0].filename
        for chain_item in reversed(target_chain):
            if chain_item.filename:
                project_path = chain_item.filename
                break

        project_relpath = ntpath.relpath(project_path,
                                         ntpath.dirname(solution_path))
        for message in filter(message_filter, messages):
            if message.kind == "fatal error":
                fatal_errors.setdefault(project_relpath, list()).append(message)
            elif message.kind == "error":
                errors.setdefault(project_relpath, list()).append(message)
            elif message.kind == "warning":
                warnings.setdefault(project_relpath, list()).append(message)
            else:
                assert False, "Don't know what a {} is".format(message.kind)

    if fatal_errors:
        section_sink.send((True, "Fatal Errors", True, summarize_messages(fatal_errors)))
    if errors:
        section_sink.send((True, "Errors", True, summarize_messages(errors)))
    if warnings:
        section_sink.send((False, "Warnings", True, summarize_messages(warnings)))
    if warn_stats:
        section_sink.send((False, "Warning Statistics", False, summarize_stats(warn_stats)))
    if suppressed_warn_stats:
        section_sink.send((False, "Suppressed Warnings", False, summarize_stats(suppressed_warn_stats)))


def summarize_messages(project_messages):
    items = list()
    for project, messages in sorted(project_messages.items()):
        project_summary = tag("p", tag("b", project), True) + "\n"
        number_to_show = 1
        project_summary += "\n".join(tag("p", tag("tt", m.text), True) for m
                                     in messages[:number_to_show])
        if len(messages) > number_to_show:
            code_stats = dict()
            for message in messages[number_to_show:]:
                code_stats[message.code] = code_stats.get(message.code, 0) + 1
            table = "\n".join("{:5}  {}".format(count, code)
                              for code, count in sorted(code_stats.items()))
            project_summary += tag("p", "and") + "\n"
            project_summary += tag("pre", table)
        items.append(project_summary)
    return "\n\n".join(items)


def summarize_stats(code_stats):
    return "\n".join("{:5}  {}".format(count, code)
                     for code, count in sorted(code_stats.items()))


#
# Main
#

def read_log_file(infile, entry_target, summary_target):
    reader = read_log(entry_target, summary_target)
    for line in infile:
        reader.send(line.rstrip("\n"))


def report(infilename, section_prefix, section_sink):
    @coroutine
    def prefix_sections(target, prefix):
        while True:
            is_critical, title, is_html, text = yield
            target.send((is_critical, prefix + title, is_html, text))

    processor = process_summary(prefix_sections(section_sink,
                                                section_prefix))
    with open(infilename) as infile:
        read_log_file(infile, null_sink(), processor)
    processor.close()
