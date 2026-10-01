defmodule TemperCore.Test do
  @moduledoc """
  Temper's `std/testing`, for a library that is translated without it.

  std/testing is a library of its own, so it is not in a library's
  translation, and its `Test` class and `runTestCases` are connected. This is
  a line-for-line port: soft `assert` records a message and carries on,
  `assertHard` records and bubbles, and the report is the same JUnit XML
  `reportTestResults` writes, which is what the harness reads.
  """
  alias TemperCore.Heap

  def new, do: Heap.new(:temper_test, %{passing: true, messages: [], failed_on_assert: false})

  def assert(t, success, message) do
    if not success do
      Heap.put(t, :passing, false)
      Heap.put(t, :messages, Heap.get(t, :messages) ++ [message.()])
    end

    nil
  end

  def assert_hard(t, success, message) do
    assert(t, success, message)

    if not success do
      Heap.put(t, :failed_on_assert, true)
      bail(t)
    end

    nil
  end

  def soft_fail_to_hard(t) do
    if not (failed_on_assert(t) or passing(t)) do
      Heap.put(t, :failed_on_assert, true)
      bail(t)
    end

    nil
  end

  def bail(_t), do: raise(TemperCore.Bubble, "test bailed")
  def passing(t), do: Heap.get(t, :passing)
  def messages(t), do: Heap.get(t, :messages)
  def failed_on_assert(t), do: Heap.get(t, :failed_on_assert)

  @doc "`processTestCases`: each test's name and its failure messages, empty when it passed."
  def process(cases) do
    Enum.map(TemperCore.List.items(cases), fn %TemperCore.Pair{key: name, value: fun} ->
      t = new()

      had_bubble =
        try do
          fun.(t)
          false
        rescue
          TemperCore.Bubble -> true
        end

      failures =
        cond do
          passing(t) and not had_bubble -> []
          had_bubble and not failed_on_assert(t) -> messages(t) ++ ["Bubble"]
          true -> messages(t)
        end

      {name, failures}
    end)
  end

  @doc """
  One test for `mix test`: run it as `processTestCases` would, and raise an
  ExUnit assertion error carrying its failure messages if it failed. `where`
  is the test's place in the Temper source, `src/diff.temper.md:42`, which
  leads the message: that is the line to fix, not the generated one.
  """
  def check(fun, where \\ nil) do
    case process([TemperCore.Pair.new("test", fun)]) do
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
  def run_cases(cases) do
    results = process(cases)
    fails = Enum.count(results, fn {_, f} -> f != [] end)

    lines =
      ["<testsuites>", "  <testsuite name='suite' tests='#{length(results)}' failures='#{fails}' time='0.0'>"] ++
        Enum.flat_map(results, fn {name, failures} ->
          basics = "name='#{escape(name)}' classname='#{escape(name)}' time='0.0'"

          if failures == [] do
            ["    <testcase #{basics} />"]
          else
            ["    <testcase #{basics}>", "      <failure message='#{escape(Enum.join(failures, ", "))}' />", "    </testcase>"]
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
