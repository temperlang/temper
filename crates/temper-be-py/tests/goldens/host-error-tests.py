__all__ = ('host_fails', 'host_fails_test')


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


def host_fails(n: int, /) -> int:
    return _connected.host_fails(n)


def host_fails_test() -> None:
    return _connected.host_fails_test()
