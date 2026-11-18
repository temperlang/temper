# The playground

The [playground](https://temperlang.github.io/tempest/playground/)
runs the whole compiler in your browser, as WebAssembly, with no
server: type Temper on the left, and see each language's translation,
the errors, and what your code and tests log, as you type. Links share
the code, which travels in the URL itself. It's published with the
[docs site](https://temperlang.github.io/tempest/), which links back to it.

Building it, and how it fits together, is in
[site/playground](../../site/playground/README.md), and
[how it works](../internals/08-playground.md) in the internals.

## The REPL tab

**REPL**, beside **Editor** in the top bar, swaps the editor for a
REPL, as [`temper repl`](repl.md) runs in a terminal: type an input,
and see its result as `interactive#N: value`, with what it logged, its
errors, and its warnings. The editor's code is input 0, so inputs can
call what it declares; edit it, and the REPL starts again from it, with
your inputs run again. The editor's tests run once, as input 0's, and
the REPL shows their summary, `Tests passed: 3 of 3`, with any failures;
`runTests()` runs them again against your newest definitions, as in
[`temper repl`](repl.md#running-input-0s-tests-again). Tests typed into
the REPL don't run.

- **Enter** runs the input once it's whole, or else starts a new line,
  so a function can be typed over several lines. **Shift+Enter** always
  starts a new line, and **Ctrl+Enter** (Cmd+Enter on a Mac) or **Run**
  always runs. On a touch screen, Enter starts a new line, and Run runs.
- **Up** and **Down**, on the first or last line, go through earlier
  inputs.
- **Completion, hover, and signature help** work in the input cell as
  in the editor, and know what earlier inputs declared, and the REPL's
  own functions. The completion menu opens as you type a name or after
  `.`, with the first option picked: **Enter** or **Tab** accepts it,
  **Down** or **Up** picks another, **Ctrl+Space** opens it, and
  **Escape** closes it. With no menu open, or the name already typed
  in full, Enter runs. **Ctrl+I**
  (Cmd+I on a Mac) shows the type and docs of the name at the cursor.
  They read the cell without running it, and wait while an input runs.
- **The transcript** folds long output after 40 lines, with **Show
  all**, and keeps the latest 300 inputs. Click an `interactive#N` to
  go to that input and put it in the cell.
- `help()` lists what the REPL offers, `translate(n, "py")` shows
  input `n` in Python (or `cpp`, `csharp`, `java`, `js`, `lua`, `rust`),
  and `runTests()` runs the editor's tests again.
- **Limits:** each input may take 50 million steps; one that runs out
  offers **Keep going**, which runs it again, as the next input, with
  ten times as many. **Stop** (or Escape) ends an input that's running;
  the REPL then runs the inputs before it again, quietly, to restore
  the session, which takes as long as they took. Memory is capped at
  1 GiB. **Restart** starts over from the editor's code.
- Nothing leaves your browser, and links don't share the REPL's inputs.

> **Note:** these docs link only this playground, but Temper's own
> README still links an older one, `play.temperlang.dev`, and the old
> site's [terms of use](../legal/playground-terms.md) name
> `playground.temper.systems`
> ([inconsistencies](../inconsistencies.md#i11-playground-addresses)).
