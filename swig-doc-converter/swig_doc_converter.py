#!/usr/bin/env python3

# /// script
# requires-python = ">=3.12"
# dependencies = [
#     "beautifulsoup4",
# ]
# ///

"""
SWIG Documentation Converter

Transplants Doxygen comments from C++ source documentation (HTML)
to Javadoc comments in SWIG-generated Java source files.

This script was translated by Claude Code from the original Clojure version by
Arthur Edelstein (2012). The ability to read linked javadocs via HTTP was
removed.
"""

import argparse
import re
from pathlib import Path

from bs4 import BeautifulSoup


def trim(s: str | None) -> str | None:
    """Strip whitespace, return None for empty strings."""
    if s is None:
        return None
    trimmed = s.strip()
    return trimmed if trimmed else None


def extract_param(param_row) -> dict | None:
    """Extract parameter name and doc from a table row.

    The row cells are processed in reverse order:
    - Last cell contains the parameter description
    - Second-to-last cell contains <em> with parameter name
    """
    cells = param_row.find_all("td")
    if len(cells) < 2:
        return None

    # Process in reverse order (same as Clojure version)
    reversed_cells = list(reversed(cells))

    doc = trim(reversed_cells[0].get_text()) if reversed_cells else None

    name = None
    if len(reversed_cells) > 1:
        em = reversed_cells[1].find("em")
        if em:
            name = trim(em.get_text())

    if doc and name:
        return {"name": name, "doc": doc}
    return None


def extract_method_properties(method_node) -> dict:
    """Extract name, doc, arg-count, returns, parameters from a method node."""
    # Method name: from td.memname, split on "::", take last part
    memname = method_node.select_one("td.memname")
    full_name = trim(memname.get_text()) if memname else ""
    name = full_name.split("::")[-1] if full_name else ""

    # Method description: first <p> directly under div.memdoc
    memdoc = method_node.select_one("div.memdoc")
    doc = None
    if memdoc:
        p = memdoc.find("p", recursive=False)
        if p:
            doc = trim(p.get_text())

    # Argument count: number of td.paramtype elements
    arg_count = len(method_node.select("td.paramtype"))

    # Return value description: dl.return > dd
    returns = None
    return_dl = method_node.select_one("dl.return")
    if return_dl:
        dd = return_dl.find("dd")
        if dd:
            returns = trim(dd.get_text())

    # Parameters: div.memdoc > dl > dd > table > tr
    parameters = []
    if memdoc:
        for dl in memdoc.find_all("dl", recursive=False):
            for dd in dl.find_all("dd", recursive=False):
                table = dd.find("table")
                if table:
                    for tr in table.find_all("tr"):
                        param = extract_param(tr)
                        if param:
                            parameters.append(param)

    return {
        "name": name,
        "doc": doc,
        "arg_count": arg_count,
        "returns": returns,
        "parameters": parameters,
    }


def scrape_doxygen(html_path: str) -> list[dict]:
    """Parse Doxygen HTML and extract method documentation."""
    with open(html_path, "r", encoding="utf-8") as f:
        soup = BeautifulSoup(f, "html.parser")

    # Find all method documentation containers
    method_nodes = soup.select("div.memitem")

    methods = []
    for node in method_nodes:
        props = extract_method_properties(node)
        # Filter out destructors (names containing ~)
        if props["name"] and "~" not in props["name"]:
            methods.append(props)

    return methods


def method_declaration_pattern(name: str, arg_count: int) -> re.Pattern:
    """Build regex to match Java public method declarations.

    Matches pattern like:
      public void methodName(String arg1, int arg2)
    """
    if arg_count > 0:
        # Match: first param type, then (n-1) params with commas, then final param
        param_pattern = rf"\S+(\s*[^,\s]+,){{{arg_count - 1}}}[^,]*"
    else:
        param_pattern = r"(\s*)"

    pattern = rf"  public .*?{re.escape(name)}\({param_pattern}\)"
    return re.compile(pattern)


def generate_javadoc(method: dict) -> str:
    """Generate Javadoc comment string from method properties."""
    lines = ["  /**"]
    lines.append(f"   * {method['doc']}")

    if method["parameters"]:
        lines.append("   *")
        for param in method["parameters"]:
            lines.append(f"   * @param {param['name']} {param['doc']}")

    if method["returns"]:
        lines.append("   *")
        lines.append(f"   * @return {method['returns']}")

    lines.append("   */")
    return "\n".join(lines) + "\n"


def add_doc(java_content: str, method: dict) -> str:
    """Add Javadoc comment before a single method declaration."""
    pattern = method_declaration_pattern(method["name"], method["arg_count"])

    def replacer(match):
        return generate_javadoc(method) + match.group(0)

    return pattern.sub(replacer, java_content)


def add_docs(java_content: str, methods: list[dict]) -> str:
    """Add Javadoc comments to Java source for all methods."""
    for method in methods:
        java_content = add_doc(java_content, method)
    return java_content


def main():
    parser = argparse.ArgumentParser(
        description="Transplant Doxygen docs from C++ HTML to Java source"
    )
    parser.add_argument("doxygen_html", help="Path to Doxygen HTML file")
    parser.add_argument(
        "--java-file",
        default="mmcorej/CMMCore.java",
        help="Path to Java file to modify (default: mmcorej/CMMCore.java)",
    )
    args = parser.parse_args()

    # Parse Doxygen HTML
    methods = scrape_doxygen(args.doxygen_html)

    # Read Java file
    java_path = Path(args.java_file)
    java_content = java_path.read_text(encoding="utf-8")

    # Add documentation
    modified_content = add_docs(java_content, methods)

    # Write back
    java_path.write_text(modified_content, encoding="utf-8")


if __name__ == "__main__":
    main()
