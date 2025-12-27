## Various scripts for Temper development

### Installation

You'll need poetry available.

```bash
pipx install poetry  # Preferred
pip install poetry   # Okay, but installed as a global package

poetry install       # Actually install these scripts
```

The `scr` and `scr.bat` stubs run the appropriate (somewhat annoying) poetry command:

```bash
./scr init-workspace  # runs the init-workspace command
./scr help
./scr list               # alias for help
./scr help command-name  # detailed help for command
./scr command-name --help # this actually invokes the command!

# ./scr expands to:
poetry run -C scripts ...
```

We have a script to see available scripts:

    ./scr help
    ./scr help command-name #
    ./scr list  # alias for help
    ./scr list --verbose

We probably don't need to run scripts from deep in the repo, but you could add a function like:

```bash
function temper-script() {
    poetry run -C "$(git rev-parse --show-toplevel)/scripts" "$@"
}
```

## Notes for writing scripts

> **Don't use the `if __name__ == 'main'` idom.**

The help command needs to import modules, and it'll blindly run your script.

Instead, define a `main` function:

``` python
import click

@click.command()
def main():
    your_code_here()
```

And add an entry to `pyproject.toml`:

```toml
[tool.poetry.scripts]
your-script-name = 'your_module_name:function_name'
complicated-script = 'package.your_module:function_name'
```

Then run `poetry install` to add your script to the virtualenv.

[Click][] is pretty comprehensive; see other scripts for examples. If you don't use click to provide help, add a docstring to your
function. The first (non-blank) line will show up in the short listing.

### Set up ipython in this context

``` bash
./scr pip install ipython
./scr ipython
```

Ipython will run with all the installed dependencies, but the `pyproject.toml` file hasn't been mucked with.

### Where is everything?

A script can figure out where stuff in the repository is with:

```python
from util import temper_path

temper_path('scripts', 'README.md')
```

If you look at that function, it simply uses the magic `__file__` constant, which is the path to the script file itself.

If you run the standard `poetry run python -c 'import sys; print(sys.path)'` you'll see the scripts repo in there.

But it might be unclear _how_ that gets added to the search path. Another handy command is `poetry env info`:

```bash
Virtualenv
Python:         3.11.9
Implementation: CPython
Path:           /Users/ben/.local/share/virtualenvs/temper-scripts-iyYBfDW8-py3.11
Executable:     /Users/ben/.local/share/virtualenvs/temper-scripts-iyYBfDW8-py3.11/bin/python
Valid:          True
```

So looking under `temper-scripts-iyYBfDW8-py3.11`, the standard place Python puts stuff is `lib/python-XX/site-packages`. In there we find a text file `temper_scripts.pth` that points back to our actual source.

## Running Integration Tests

You can run the integration tests outside the release-dev Github action.
See that action in .github to understand the pre-requisite steps, or try
the below replacing `/path/to/my/repo` with the path to the root of your
local Temper repository:

```sh
cd /path/to/my/repo
./gradlew cli:shrinkJars
(
  cd scripts;
  poetry install --only integration && \
  poetry run integration-test --temper /path/to/my/repo/cli/build/install/temper/bin/temper
)
```

You should see something like the below:

```
Installing dependencies from lock file

No dependencies to install or update

Installing the current project: temper-scripts (0.1.0)
Using: /path/to/my/repo/cli/build/install/temper/bin/temper
check_build {}
Tops: ['-logs', 'csharp', 'java', 'js', 'lua', 'py']
Subs: ['lib-a', 'lib-b', 'std', 'temper-core', 'test']
/check_build in 9.51 seconds

...
```


[Click]: https://click.palletsprojects.com/en/8.1.x/
