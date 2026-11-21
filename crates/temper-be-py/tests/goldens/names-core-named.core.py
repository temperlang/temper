from temper_core import int_div as _int_div

__all__ = ('greet', 'share')


def greet(who: str, /) -> str:
    return f'hello from {who}'


def share(n: int, d: int, /) -> int:
    try:
        return _int_div(n, d)
    except Exception:
        return 0
