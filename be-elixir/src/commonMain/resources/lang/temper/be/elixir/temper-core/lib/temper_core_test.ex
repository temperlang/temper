defmodule TemperCore.Test do
  @moduledoc """
  Temper's `std/testing`, for a library that is translated without it.

  std/testing is a library of its own, so it is not in a library's
  translation, and its `Test` class and `runTestCases` are connected. This is
  a line-for-line port: soft `assert` records a message and carries on,
  `assertHard` records and bubbles, and the report is the same JUnit XML
  `reportTestResults` writes, which is what the harness reads.
  """
  alias TemperCore.{Heap, Pair, Vec}

  @typedoc "A Temper `Test`: a heap object."
  @type t :: TemperCore.Ref.t()

  @typedoc "A `TestCase`: a test's name and its function."
  @type test_case :: Pair.t(String.t(), (t() -> term()))

  @spec new() :: t()
  def new, do: Heap.new(:temper_test, %{passing: true, messages: [], failed_on_assert: false})

  @spec assert(t(), boolean(), (-> String.t())) :: nil
  def assert(t, success, message) do
    if not success do
      Heap.put(t, :passing, false)
      Heap.put(t, :messages, Heap.get(t, :messages) ++ [message.()])
    end

    nil
  end

  @spec assert_hard(t(), boolean(), (-> String.t())) :: nil
  def assert_hard(t, success, message) do
    assert(t, success, message)

    if not success do
      Heap.put(t, :failed_on_assert, true)
      bail(t)
    end

    nil
  end

  @spec soft_fail_to_hard(t()) :: nil
  def soft_fail_to_hard(t) do
    if not (failed_on_assert(t) or passing(t)) do
      Heap.put(t, :failed_on_assert, true)
      bail(t)
    end

    nil
  end

  @spec bail(t()) :: no_return()
  def bail(_t), do: raise(TemperCore.Bubble, "test bailed")
  @spec passing(t()) :: boolean()
  def passing(t), do: Heap.get(t, :passing)
  @doc "`messages()`: a Temper `List`, so a Vec, though the test keeps them as an Elixir list."
  @spec messages(t()) :: Vec.t(String.t())
  def messages(t), do: Vec.new(Heap.get(t, :messages))
  @spec failed_on_assert(t()) :: boolean()
  def failed_on_assert(t), do: Heap.get(t, :failed_on_assert)

  @doc "Each test's name and its failure messages, empty when it passed, as an Elixir list of tuples."
  @spec process(TemperCore.List.listed(test_case())) :: [{String.t(), [String.t()]}]
  def process(cases) do
    Enum.map(TemperCore.List.items(cases), fn %Pair{key: name, value: fun} ->
      t = new()

      # anything else a test raises, throws or exits with fails that test
      # alone, as on js: it used to end the run, and every test after it was
      # reported "not run"
      outcome =
        try do
          fun.(t)
          :ok
        rescue
          TemperCore.Bubble -> :bubble
          e -> {:crash, Exception.format_banner(:error, e, __STACKTRACE__)}
        catch
          kind, value -> {:crash, Exception.format_banner(kind, value, __STACKTRACE__)}
        end

      failures =
        case outcome do
          :ok ->
            if passing(t), do: [], else: Heap.get(t, :messages)

          :bubble ->
            if failed_on_assert(t),
              do: Heap.get(t, :messages),
              else: Heap.get(t, :messages) ++ ["Bubble"]

          {:crash, banner} ->
            Heap.get(t, :messages) ++ [banner]
        end

      {name, failures}
    end)
  end

  @doc """
  `std/testing.processTestCases()`: each case's name and its failure
  messages, as a Temper `List` of `Pair`s, which is what the translated
  `reportTestResults` reads. `process/1` is the same as an Elixir list.
  """
  @spec process_cases(TemperCore.List.listed(test_case())) ::
          Vec.t(Pair.t(String.t(), Vec.t(String.t())))
  def process_cases(cases) do
    cases
    |> process()
    |> Enum.map(fn {name, failures} -> Pair.new(name, Vec.new(failures)) end)
    |> Vec.new()
  end

  @doc """
  One test for `mix test`: run it as `processTestCases` would, and raise an
  ExUnit assertion error carrying its failure messages if it failed. `where`
  is the test's place in the Temper source, `src/diff.temper.md:42`, which
  leads the message: that is the line to fix, not the generated one.
  """
  @spec check((t() -> term())) :: :ok
  @spec check((t() -> term()), String.t() | nil) :: :ok
  def check(fun, where \\ nil) do
    case process([Pair.new("test", fun)]) do
      [{_, []}] ->
        :ok

      [{_, failures}] ->
        message = Enum.join(failures, "\n")
        message = if where, do: "#{where}: #{message}", else: message
        # ExUnit is there under `mix test`; naming it at run time keeps
        # temper-core from depending on it
        raise apply(ExUnit.AssertionError, :exception, [[message: message]])
    end
  end

  @doc "`runTestCases`: the JUnit XML `reportTestResults` writes, as one string."
  @spec run_cases(TemperCore.List.listed(test_case())) :: String.t()
  def run_cases(cases) do
    results = process(cases)
    fails = Enum.count(results, fn {_, f} -> f != [] end)

    lines =
      [
        "<testsuites>",
        "  <testsuite name='suite' tests='#{length(results)}' failures='#{fails}' time='0.0'>"
      ] ++
        Enum.flat_map(results, fn {name, failures} ->
          basics = "name='#{escape(name)}' classname='#{escape(name)}' time='0.0'"

          if failures == [] do
            ["    <testcase #{basics} />"]
          else
            [
              "    <testcase #{basics}>",
              "      <failure message='#{escape(Enum.join(failures, ", "))}' />",
              "    </testcase>"
            ]
          end
        end) ++ ["  </testsuite>", "</testsuites>"]

    Enum.map_join(lines, "", &(&1 <> "\n"))
  end

  defp escape(s) do
    s
    |> String.replace("&", "&amp;")
    |> String.replace("<", "&lt;")
    |> String.replace(">", "&gt;")
    |> String.replace("\"", "&quot;")
    |> String.replace("'", "&#39;")
  end
end
