# Fallback

The connected Python code for `answer` deliberately disagrees with its Temper body,
so the test below passes only where the body runs, as under `--no-connected`.

    @connected(["py"])
    let answer(): Int { 42 }

    test("body answers") {
      assert(answer() == 42);
    }
