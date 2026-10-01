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
end
