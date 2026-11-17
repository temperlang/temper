from temper_core import (
    LoggingConsole as _LoggingConsole,
    arith_int_mod as _arith_int_mod,
    boolean_to_string as _boolean_to_string,
    cast_none as _cast_none,
    int_add as _int_add,
    int_div as _int_div,
    int_mul as _int_mul,
    string_to_int32 as _string_to_int32,
)

__all__ = ()


def _more(x: int | None, /) -> int:
    raise RuntimeError()


def _something(x: str | None, /) -> str:
    if x is not None:
        return x
    else:
        raise RuntimeError()


def _report(x: str | None, /) -> None:
    try:
        message = _something(x)
    except Exception:
        message = 'null'
    try:
        t_0: int
        try:
            _cast_none(x)
            t_0 = 1
        except Exception:
            t_0 = 0
        suffix = str(_more(t_0))
    except Exception:
        suffix = ''
    _console.log(message + suffix)


def _calc_rows(cols: int, total: int, /) -> int:
    _console.log(f'calcRows({cols}, {total})')
    if _arith_int_mod(total, cols) != 0:
        raise RuntimeError()
    return _int_div(total, cols)


def _count_below(s: str, /) -> int:
    i = 0
    while i < _string_to_int32(s):
        i = _int_add(i, 1)
    return i


def _first_over(n: int, /) -> int:
    i = 0
    while i < 9:
        i = _int_add(i, 1)
        if i > n:
            break
    return i


def _sum_skipping(n: int, /) -> int:
    sum = 0
    i = 0
    while i < 5:
        if i == n:
            i = _int_add(i, 1)
            continue
        sum = _int_add(sum, i)
        i = _int_add(i, 1)
    return sum


def _ten_times(n: int, /) -> int:
    i = 0
    while i < 9:
        if i == n:
            return _int_mul(i, 10)
        i = _int_add(i, 1)
    raise RuntimeError()


def _is_none(r: int | None, /) -> bool:
    return r is None


def _via_or_else(n: int, /) -> str:
    t_0: int
    try:
        t_0 = _first_over(n)
    except Exception:
        t_0 = -1
    t_1: int
    try:
        t_1 = _sum_skipping(n)
    except Exception:
        t_1 = -1
    t_2: int
    try:
        t_2 = _ten_times(n)
    except Exception:
        t_2 = -1
    return f'{t_0} {t_1} {t_2}'


def _branch(a: int, b: int, /) -> None:
    c = ('00' if b == 0 else '01' if b == 1 else '02' if b == 2 else '0x') if a == 0 else '1x' if a == 1 else '2x' if a == 2 else '3x' if a == 3 else 'xx'
    _console.log(f'branch({a}, {b}) -> {c}')


_console: _LoggingConsole = _LoggingConsole(__name__)
_report('hi')
_report(None)
_console.log(str(_calc_rows(3, 6)))
_t_0: int
try:
    _t_0 = _count_below('abc')
except Exception:
    _t_0 = -1
_console.log(str(_t_0))
_t_1: int
try:
    _t_1 = _first_over(1)
except Exception:
    _t_1 = -1
_t_2: int | None
try:
    _t_2 = _first_over(1)
except Exception:
    _t_2 = None
_console.log(f'{_t_1} {_boolean_to_string(_is_none(_t_2))}')
_console.log(_via_or_else(2))
_console.log(_via_or_else(12))
_branch(0, 2)
_branch(2, 0)
