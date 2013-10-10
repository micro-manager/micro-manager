# Python 3.3

# Fix newlines in place

import argparse
import re

def fix_newlines(text, use_crlf):
    newline = rb"\r\n" if use_crlf else rb"\n"
    return re.sub(rb"\r*\n", newline, text)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Fix newlines in place")
    parser.add_argument("--crlf", dest="write_dos", action="store_true")
    parser.add_argument("--lf", dest="write_dos", action="store_false")
    parser.add_argument("files", metavar="FILE", nargs='+')
    args = parser.parse_args()

    for file in args.files:
        with open(file, "rb") as f:
            text = f.read()

        fixed_text = fix_newlines(text, args.write_dos)

        with open(file, "wb") as f:
            f.write(fixed_text)
