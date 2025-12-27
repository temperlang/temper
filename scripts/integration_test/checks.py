import collections as c
import contextlib as ctx
import glob
import inspect
import os.path
import pathlib as p
import shutil as sh
import subprocess as sp
import tempfile
import textwrap
import time
import typing as t


# See https://mypy.readthedocs.io/en/stable/protocols.html
class Check(t.Protocol):
    def __call__(self, checks: "Checks", **kwargs) -> None: ...

    __name__: str


# def report_check(fun: Check):  # <-- Mypy doesn't cooperate with usage.
def report_check(fun):
    # So delegate to separate typed function.
    return report_check_typed(fun)


def report_check_typed(check: Check):
    def report_and_run(checks: "Checks", **kwargs) -> None:
        print(f"{check.__name__} {kwargs}")
        start_time = time.time()
        try:
            check(checks, **kwargs)
        finally:
            elapsed = time.time() - start_time
            print(f"/{check.__name__} in {elapsed:.3g} seconds\n")

    return report_and_run


class Checks:
    def __init__(self, root: str, temper: str, verbose: bool = False) -> None:
        print(f"Using: {temper}")
        self.failures: t.Dict[str, t.List[str]] = c.defaultdict(lambda: [])
        self.root = p.Path(root)
        self.temper = temper
        self.verbose = verbose

    def check(
        self,
        key: str,
        condition: t.Callable[[], bool],
        on_failure: t.Callable[[], None] = lambda: None,
    ) -> bool:
        ok = condition()
        if not ok:
            source = inspect.getsource(condition).strip()
            self.failures[key].append(f"failed: {source}")
            on_failure()
        return ok

    def check_all(self, checks: t.Optional[t.List[str]] = None) -> bool:
        if checks is None:
            checks = [
                "build",
                "init",
                "repl",
                "repl_backend_java",
                "repl_backend_js",
                "repl_backend_lua",
                "repl_backend_py",
                # Java has more dependencies than most, but check others also.
                # Including multiple together, since we had a regression with that.
                "test-backend=java",
                "test-backend=csharp,js",
                "watch",
                # Slow and currently broken, so exclude by default.
                # "mypy_produces_binaries",
            ]
        for check in checks:
            check, *args = check.split("-")
            args = dict(arg.split("=") for arg in args)
            getattr(self, f"check_{check}")(**args)
        return self.report()

    @report_check
    def check_build(self) -> None:
        with self.tmp_copy("cli/src/test/resources/build/input") as tmp:
            self.run_temper(args=["build", *self.verbosity()], cwd=tmp)
            self.check_build_output(
                "build", libs=["lib-a", "lib-b", "test"], work_root=tmp, expect_std=True
            )

    def check_build_output(
        self, key: str, libs: t.List[str], work_root: p.Path, expect_std: bool
    ) -> None:
        out_dir = work_root / "temper.out"
        print(f"Libs: {libs}")  # What do we expect?
        # Top outs are backends.
        tops = sorted(kid.name for kid in out_dir.iterdir())
        print(f"Tops: {tops}")
        self.check(
            key, lambda: tops == ["-logs", "csharp", "java", "js", "lua", "py", "rust"]
        )
        # Next level are libraries.
        subs = sorted(kid.name for kid in (out_dir / "java").iterdir())
        print(f"Subs: {subs}")
        required_extras = ["temper-core"]
        if expect_std:
            required_extras += ["std"]
        self.check(key, lambda: sorted(subs) == sorted(libs + required_extras))

    @report_check
    def check_init(self) -> None:
        """Check init then build."""
        with tempfile.TemporaryDirectory() as tmp:
            proj_name = "proj"
            work_root = p.Path(tmp) / proj_name
            work_root.mkdir()
            # Check init and follow with build to verify the init was useful.
            self.run_temper(args=["init"], cwd=work_root)
            self.run_temper(args=["build"], cwd=work_root)
            self.check_build_output(
                "init", libs=[proj_name], work_root=work_root, expect_std=False
            )

    @report_check
    def check_repl(self) -> None:
        process = self.run_temper(args=["repl", *self.verbosity()], input="1 + 1\n")
        if not self.verbose:
            print(f"Temper repl output:\n{process.stderr.strip()}")
            self.check("repl", lambda: "interactive#0: 2" in process.stderr)

    def run_repl_backend(
        self,
        backend: str,
        input: str,
        wanted: list[str],
        work_root: str,
    ) -> None:
        with self.tmp_copy(work_root) as tmp:
            input = input
            process = self.run_temper(
                args=["repl", "-b", backend, *self.verbosity()],
                cwd=tmp,
                encoding="utf-8",
                input=input,
            )
            stdout = process.stdout.rstrip()
            stdout = stdout.replace("\r\n", "\n")
            missing = [x for x in wanted if x not in stdout]
            self.check(
                f"repl -b {backend}",
                lambda: not missing,
                on_failure=lambda: print(
                    "repl -b %(backend)s missing: %(missing)s\nfrom\n%(out)s"
                    % {
                        "backend": backend,
                        "missing": repr(missing),
                        "out": textwrap.indent(stdout, "  "),
                    }
                ),
            )

    @report_check
    def check_repl_backend_java(self) -> None:
        self.run_repl_backend(
            backend="java",
            input="import static test_me.constants.ConstantsGlobal.tau;\ntau\n",
            wanted=[
                # From JShell's blurb.
                "|  Welcome to JShell --",
                # Some of the digits from tau.
                "tau ==> 6.283185307",
            ],
            work_root="cli/src/test/resources/testing/multi",
        )

    @report_check
    def check_repl_backend_js(self) -> None:
        self.run_repl_backend(
            backend="js",
            input="b.bad\n",
            wanted=[
                # These are substring in the header describing how to access
                # pre-loaded libraries.
                "\n║Library Name║JS identifier║\n",
                "\n║a ",
                "\n║b ",
                # In this case, 'bad' is good, as it's something exported we can use.
                # This is the result of evaluating `b.bad` above.
                "'bad'\n",
            ],
            work_root="cli/src/test/resources/testing/multi",
        )

    @report_check
    def check_repl_backend_lua(self) -> None:
        self.run_repl_backend(
            backend="lua",
            input="print('tau is ' .. require('test-me/constants').tau)\n",
            wanted=[
                # Some of the digits from tau.
                "tau is 6.283185307",
            ],
            work_root="cli/src/test/resources/testing/multi",
        )

    @report_check
    def check_repl_backend_py(self) -> None:
        self.run_repl_backend(
            backend="py",
            input="from pally import is_palindrome\nis_palindrome('step on no pets')\n",
            wanted=[
                "\nTrue",
            ],
            work_root="cli/src/test/resources/pally",
        )

    @report_check
    def check_test(self, backend: str) -> None:
        with self.tmp_copy("cli/src/test/resources/testing/multi") as tmp:
            backends = backend.split(",")
            backend_args = [arg for b in backends for arg in ("-b", b)]
            process = self.run_temper(
                args=["test", *backend_args, *self.verbosity()],
                cwd=tmp,
            )
            stdout = textwrap.indent(process.stdout.strip(), "  ")
            stderr = (
                "(above)"
                if self.verbose
                else textwrap.indent(process.stderr.strip(), "  ")
            )
            print(f"Temper test {backend}\nstderr:\n{stderr}\nstdout:\n{stdout}")
            if not self.verbose:
                n = 3 * len(backends)
                self.check("test", lambda: f"Tests passed: {n} of {n}" in stderr)

    @report_check
    def check_watch(self) -> None:
        with self.tmp_copy("cli/src/test/resources/testing/multi") as tmp:
            process = self.run_temper(
                args=["watch", "--limit", "1", *self.verbosity()],
                cwd=tmp,
            )
            output = process.stdout.strip()
            error = process.stderr.strip()
            print(f"Temper watch output:\n{output}\nError:\n{error}")
            if not self.verbose:
                self.check("watch", lambda: "Watcher reached build limit" in error)

    @report_check
    def check_mypy_produces_binaries(self) -> None:
        with self.tmp_copy("cli/src/test/resources/build/input") as tmp:
            try:
                result = self.run_temper(
                    args=["build", "-b", "mypyc"],
                    cwd=tmp,
                    timeout=600,
                )
                if not self.verbose:
                    # See it even if not verbosely streamed earlier.
                    print(result.stderr)
                if result.returncode:
                    raise Exception("mypyc failed")

                # Look for binary files per library
                output_root = os.path.join(tmp, "temper.out", "mypyc")
                have_native = set()
                exts = ("pyd", "so")
                # Printing binaries is interesting for reference purposes.
                # And currently less than a couple dozen lines.
                print("binaries found:")
                for ext in exts:
                    glob_iterator = glob.iglob(
                        f"**/*.{ext}",
                        root_dir=output_root,
                        recursive=True,
                    )
                    for filename in glob_iterator:
                        print(f"- {filename}")
                        have_native.add(filename.split(os.path.sep)[0])

                libs = set(["lib-a", "lib-b", "std", "temper-core"])
                if libs != have_native:
                    raise Exception(
                        f"Expected native libs ({exts}) in {libs} but found "
                        + f"them in {have_native}"
                    )
            except:  # noqa: E722 - raised after logging
                # Dump mypy logs on a problem
                for log_file in glob.iglob(
                    "**/mypy.*.log",
                    root_dir=os.path.join(tmp, "temper.out", "mypyc"),
                    recursive=True,
                ):
                    print(f"{log_file}")
                    print("=" * len(log_file))
                    print(open(os.path.join(output_root, log_file), "r").read())
                    print()
                raise

    def report(self) -> bool:
        if self.failures:
            print("\nFailures:\n")
            for key, messages in self.failures.items():
                print(f"- {key}:")
                for message in messages:
                    print(f"  - {message}")
            print()
            return False
        return True

    def run_temper(
        self, *, args, input="", timeout=240, **kwargs
    ) -> sp.CompletedProcess:
        result = sp.run(
            args=[self.temper] + args,
            input=input,
            stdout=sp.PIPE,
            # Without more effort, can only stream or capture stderr, not both.
            stderr=None if self.verbose else sp.PIPE,
            text=True,
            timeout=timeout,
            **kwargs,
        )
        if result.returncode:
            self.failures[args[0]].append(f"failed run: {args}")
        return result

    @ctx.contextmanager
    def tmp_copy(self, source_dir: str) -> t.Generator[p.Path, None, None]:
        with tempfile.TemporaryDirectory() as tmp:
            sh.copytree(self.root / source_dir, tmp, dirs_exist_ok=True)
            yield p.Path(tmp)

    def verbosity(self) -> t.List[str]:
        return ["--verbose"] if self.verbose else []
