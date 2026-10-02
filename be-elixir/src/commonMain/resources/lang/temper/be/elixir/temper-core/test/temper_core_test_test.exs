defmodule TemperCoreTestTest do
  use ExUnit.Case, async: true
  alias TemperCore.Test, as: T
  alias TemperCore.Pair

  test "soft asserts collect, hard ones stop, a stray bubble is reported as Bubble" do
    cases = [
      Pair.new("passes", fn t -> T.assert(t, true, fn -> "no" end) end),
      Pair.new("soft", fn t ->
        T.assert(t, false, fn -> "one" end)
        T.assert(t, false, fn -> "two" end)
      end),
      Pair.new("hard", fn t ->
        T.assert_hard(t, false, fn -> "stop" end)
        T.assert(t, false, fn -> "never reached" end)
      end),
      Pair.new("stray", fn _t -> raise TemperCore.Bubble end)
    ]

    assert T.process(cases) == [{"passes", []}, {"soft", ["one", "two"]}, {"hard", ["stop"]}, {"stray", ["Bubble"]}]
    xml = T.run_cases(cases)
    assert xml =~ "<testsuite name='suite' tests='4' failures='3' time='0.0'>"
    assert xml =~ "<testcase name='passes' classname='passes' time='0.0' />"
    assert xml =~ "<failure message='one, two' />"
  end

  test "a test that panics or crashes fails alone, and the ones after it still run" do
    # a Panic used to escape process/1 and end the run: `temper test`
    # reported every test, before and after it, as not run
    cases = [
      Pair.new("panics", fn _t -> raise TemperCore.Panic, "broken code: nope" end),
      Pair.new("crashes", fn _t -> Map.fetch!(%{}, :x) end),
      Pair.new("throws", fn _t -> throw(:stray) end),
      Pair.new("after", fn t -> T.assert(t, true, fn -> "no" end) end)
    ]

    assert [{"panics", [panic]}, {"crashes", [crash]}, {"throws", [thrown]}, {"after", []}] = T.process(cases)
    assert panic == "** (TemperCore.Panic) broken code: nope"
    assert crash =~ "** (KeyError) key :x not found"
    assert thrown == "** (throw) :stray"
  end

  test "names and messages are escaped for XML" do
    xml = T.run_cases([Pair.new("a<b", fn t -> T.assert(t, false, fn -> "it's \"x\" & y" end) end)])
    assert xml =~ "name='a&lt;b'"
    assert xml =~ "message='it&#39;s &quot;x&quot; &amp; y'"
  end

  test "check passes quietly, and a failure leads with the Temper line" do
    assert T.check(fn t -> T.assert(t, true, fn -> "no" end) end, "src/x.temper.md:3") == :ok

    error =
      assert_raise ExUnit.AssertionError, fn ->
        T.check(fn t -> T.assert(t, false, fn -> "got 4" end) end, "src/x.temper.md:7")
      end

    assert error.message == "src/x.temper.md:7: got 4"
    error = assert_raise ExUnit.AssertionError, fn -> T.check(fn t -> T.assert(t, false, fn -> "bare" end) end) end
    assert error.message == "bare"
  end

  test "processTestCases gives a Temper List of Pairs, as std's reportTestResults reads it" do
    # process/1's {name, failures} tuples went straight to the translated
    # reportTestResults, which reads each result's .key and .value.
    ok = fn _t -> nil end
    bad = fn t -> TemperCore.Test.assert(t, false, fn -> "nope" end) end
    results = TemperCore.Test.process_cases(TemperCore.Vec.new([TemperCore.Pair.new("ok", ok), TemperCore.Pair.new("bad", bad)]))
    assert %TemperCore.Vec{} = results
    assert [%TemperCore.Pair{key: "ok", value: %TemperCore.Vec{t: {}}}, %TemperCore.Pair{key: "bad", value: failures}] = Enum.to_list(results)
    assert Enum.to_list(failures) == ["nope"]
  end
end
