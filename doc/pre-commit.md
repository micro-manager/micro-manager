# Git pre-commit hook for Micro-Manager repository

We use pre-commit hooks to maintain a consistent coding style.

Git pre-commit hooks are scripts that run each time you make a commit. We use
the [pre-commit](https://pre-commit.com/) framework, which makes setting up Git
hooks simpler and more flexible.

You should enable pre-commit hooks before making changes to Micro-Manager code,
especially if you intend to contribute back your changes.

Below are the instructions, and pointers to IDE plugin settings that will help
with code styling.

The same checks are run automatically on all pull requests.

## Enabling the pre-commit hook

Let's assume you have a newly (or previously) cloned copy of the repository, as
if by:

```sh
git clone https://github.com/micro-manager/micro-manager.git
```

(optionally with `--recurse-submodules`).

To enable the pre-commit hook, you need the following:

- Install Python (3.8 or later)
- Install pre-commit
- Enable pre-commit

### Installing Python

You should use any convenient way to install Python (if not already installed).
The following are just suggestions.

- On Windows, the [python.org
  installer](https://www.python.org/downloads/windows/) is recommended.
- On macOS, you can use the [python.org
  installer](https://www.python.org/downloads/macos/) or
  [Homebrew](https://brew.sh/).
- On Linux, you can use the system package manager, such as `apt` or `dnf`.

Other methods, such as Miniconda or Chocolatey, should be no problem. You will
want `python` to be on the command search path (`PATH` environment variable).

### Installing pre-commit

See also: pre-commit's [installation
instructions](https://pre-commit.com/#install).

`pre-commit` is a Python package that includes a command-line interface.

It is possible to install `pre-commit` globally, and this works well on macOS
and Linux. Use `pip` (or `python -m pip`), `brew`, or `conda`/`mamba`.

That method works on Windows, too (at least with cmd.exe or PowerShell), but I
find that installing to a [virtual
environment](https://docs.python.org/3/library/venv.html) works better in Git
Bash:

```sh
cd path/to/micro-manager
python -m venv venv
echo '*' > venv/.gitignore       # Tell Git to ignore 'venv/'
source venv/Scripts/activate     # Activate the venv
python -m pip install pre-commit
```

Make sure that you activate the virtual environment every time you work with
`git` in a new terminal window.

You can also use a Conda virtual environment.

### Enabling pre-commit

```sh
pre-commit install
```

This will enable the pre-commit hook for the repository. Subsequent commits
will invoke the hook to check the changed files before committing.

## Working with the pre-commit hook

See also: pre-commit [documentation](https://pre-commit.com/#usage).

Just run `git commit` as you normally would, after staging your changes.

The first time you run after enableing pre-commit, or when the hook settings
have changed, it might take a few moments to install the checks. After that,
the checks should be fast.

If there are any style issues detected, you will get an error. Fix them, stage
changes, and try again.

(Some checks (not currently enabled for micro-manager) may automatically fix
the issues by reformatting the code. In that case, inspect the changes, stage
them (`git add`), and try again.)

**Bypassing:** If there is some reason why you absolutely need to commit code
that doesn't pass the checks (maybe it is a temporary commit to save your
work), you can use `git commit --no-verify` to bypass the pre-commit hook.

## Disabling and uninstalling

You can disable pre-commit hooks with

```sh
pre-commit uninstall
```

This might be useful when experimenting with old commits from before we set up
pre-commit hooks.

If you want to free up disk space used by pre-commit:

- pre-commit stores packages used for the hooks in a cache directory (usually
  `.cache/pre-commit` in your home directory).

- In addition, the Java runtime and other files used to run Java tools
  (Checkstyle) is stored in the user cache directory (see [cjdk
  docs](https://cachedjdk.github.io/cjdk/latest/cachedir.html)).

## Setting up IDE plugins for Checkstyle

Currently the main check run by the pre-commit hook is
[Checkstyle](https://checkstyle.org/), a tool that finds code that fails to
adhere to a coding style.

Our style rules for Checkstyle are in `buildscripts/codestyle/checkstyle.xml`.

At the moment, checks are only enforced on mmstudio code, but this is expected
to expand in the future.

It is a very good idea to configure your IDE to display Checkstyle errors and
wornings, and to format your code according to our style. This should save you
a lot of manual work.

- For IntelliJ IDEA, install the
  [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea)
  plugin by Jamie Shiell
  ([instructions](https://github.com/jshiell/checkstyle-idea/blob/main/README.md)).
  - Configure the plugin by going to `Settings > Tools > Checkstyle` and add
    `buildscripts/codestyle/checkstyle.xml` as a configuration file.
  - In `Settings > Editor > Inspections`, enable Checkstyle real-time scan
    (this can be done per project). You might want to set Severity to Warning.
  - Additionally, import the Checkstyle rules into code formatting settings: go
    to `Settings > Editor > Code Style > Java`; from the Gear (⚙️) menu, choose
    `Import Scheme > Checkstyle Configuration`.

- Eclipse has a [Checkstyle plugin](https://checkstyle.org/eclipse-cs/)

- VSCode has [support](https://code.visualstudio.com/docs/java/java-linting)
  for Checkstyle
