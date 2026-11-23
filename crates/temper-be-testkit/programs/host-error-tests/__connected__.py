class _connected:
    def host_fails(n: int) -> int:
        if n == 1:
            raise RuntimeError(f"host bug {n}")
        if n == 2:
            return [][n]
        return int("not a number")

    # unittest's own failure, as its assertions raise.
    def host_fails_test() -> None:
        from unittest import TestCase

        TestCase().fail("host test failure")
