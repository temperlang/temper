__all__ = ('host_fails',)


class _connected:
    def host_fails(n: int) -> int:
        raise RuntimeError(f"host bug {n}")


def host_fails(n: int, /) -> int:
    return _connected.host_fails(n)
