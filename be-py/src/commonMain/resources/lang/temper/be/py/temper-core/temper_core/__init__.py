"""
Implementation of many of the connected methods in temper.
"""

# `FTS` stands for future type syntax.
# Our current supported version is Python3.8, which does
# not allow generic type declarations.
# Comments starting with FTS record type syntax that will
# hopefully prove useful when we eventually can use that
# in line.

import sys
import logging
from abc import abstractmethod
from array import array
from concurrent.futures import Future, ThreadPoolExecutor
from functools import cmp_to_key, reduce
from logging import getLogger, INFO
from math import copysign, inf, isclose, isinf, isnan, nan
import threading
from traceback import print_exception
from typing import (
    Any,
    Callable,
    Deque,
    Dict,
    Generator,
    Generic,
    Iterable,
    List,
    Mapping,
    MutableSequence,
    NoReturn,
    Optional,
    Protocol,
    Sequence,
    Type,
    TypeVar,
    # TypeVarTuple, # since 3.11
    Union,
    cast,
)
from sys import float_info
from datetime import date as Date, datetime, timezone
from types import MappingProxyType
import urllib.request
import urllib.response

Unset = None
"Abstraction used for tracking unset args."


class TemperComparable(Protocol):
    """Base class for comparable temper objects."""

    __slots__ = ()

    @abstractmethod
    def __le__(self, other: object) -> bool:
        pass

    @abstractmethod
    def __lt__(self, other: object) -> bool:
        pass

    @abstractmethod
    def __ge__(self, other: object) -> bool:
        pass

    @abstractmethod
    def __gt__(self, other: object) -> bool:
        pass


T = TypeVar("T")
U = TypeVar("U")
C = TypeVar("C", bound="TemperComparable")


class TemperObject:
    "All user-defined classes include this marker class."

    __slots__ = ()


class TemperEnum(TemperObject):
    "Enum classes are user-defined classes additionally with this marker."

    __slots__ = ()


def get_static(reified_type: type, symbol: Optional[str] = None) -> NoReturn:
    "There's no way to extract the class from a reified type at this time."
    raise NotImplementedError()


def temper_print(value: T) -> None:
    "Temper semantics for printing."
    if isinstance(value, str):
        print(value)
    else:
        print(repr(value))


def init_simple_logging() -> None:
    "Log all at INFO or higher to stdout without decoration."
    logging.basicConfig(
        handlers=[logging.StreamHandler(stream=sys.stdout)],
        level=INFO,
        format="%(message)s",
    )


class LoggingConsole(TemperObject):
    """One class per file to log data to the console."""

    __slots__ = ("logger",)

    def __init__(self, name: str):
        self.logger = getLogger(name)

    def log(self, message: str) -> None:
        """Log a message to the console."""
        self.logger.log(INFO, message)


def str_cat(*parts: str) -> str:
    "Concatenate parts into a single string."
    return "".join(map(str, parts))


def make_bubble_exception() -> Exception:
    return RuntimeError()


def bubble() -> Any:
    "Raises an exception for Temper bubbling."
    raise make_bubble_exception()


def float_cmp(left: float, right: float) -> int:
    "Three way compares floats, caring about nan and sign of zeroes."
    if isnan(left):
        return 0 if isnan(right) else 1
    if isnan(right):
        return -1
    if left == 0 and right == 0:
        left = copysign(1.0, left)
        right = copysign(1.0, right)
    return (left > right) - (left < right)


def float_eq(left: float, right: float) -> bool:
    "Checks if two floats are exactly equal, caring about nan and sign of zeros."
    return (left == right and copysign(1.0, left) == copysign(1.0, right)) or (
        isnan(left) and isnan(right)
    )


def float_not_eq(left: float, right: float) -> bool:
    "Checks if two floats not are exactly equal, caring about nan and sign of zeros."
    return (left != right or copysign(1.0, left) != copysign(1.0, right)) and not (
        isnan(left) and isnan(right)
    )


def float_lt_eq(left: float, right: float) -> bool:
    "Checks if left <= right, caring about sign of zeros."
    return float_cmp(left, right) <= 0


def float_lt(left: float, right: float) -> bool:
    "Checks if left < right, caring about sign of zeros."
    return float_cmp(left, right) < 0


def float_gt_eq(left: float, right: float) -> bool:
    "Checks if left >= right, caring about sign of zeros."
    return float_cmp(left, right) >= 0


def float_gt(left: float, right: float) -> bool:
    "Checks if left <= right, caring about sign of zeros."
    return float_cmp(left, right) > 0


def generic_cmp(left: C, right: C) -> int:
    "Three way compares objects, caring about the sign of zeroes of floats."
    if isinstance(left, float) and isinstance(right, float):
        return float_cmp(left, right)
    return (left > right) - (left < right)


def generic_eq(left: T, right: T) -> bool:
    "Checks if two objects are exactly equal, caring about the sign of zeros of floats."
    if isinstance(left, float) and isinstance(right, float):
        return float_eq(left, right)
    return left == right


def generic_not_eq(left: T, right: T) -> bool:
    """
    Checks if two objects are not exactly equal, caring about the sign of zeros of
    floats.
    """
    if isinstance(left, float) and isinstance(right, float):
        return float_not_eq(left, right)
    return left != right


def generic_lt_eq(left: C, right: C) -> bool:
    "Checks if two left <= right, caring about the sign of zeros of floats."
    if isinstance(left, float) and isinstance(right, float):
        return float_lt_eq(left, right)
    return left <= right


def generic_lt(left: C, right: C) -> bool:
    "Checks if two left <=right, caring about the sign of zeros of floats."
    if isinstance(left, float) and isinstance(right, float):
        return float_lt(left, right)
    return left < right


def generic_gt_eq(left: C, right: C) -> bool:
    "Checks if two left >= right, caring about the sign of zeros of floats."
    if isinstance(left, float) and isinstance(right, float):
        return float_gt_eq(left, right)
    return left >= right


def generic_gt(left: C, right: C) -> bool:
    "Checks if two left > right, caring about the sign of zeros of floats."
    if isinstance(left, float) and isinstance(right, float):
        return float_gt(left, right)
    return left > right


def arith_dub_div(dividend: float, divisor: float) -> float:
    """
    Performs division on Float64 (python float); dub stands for "double", like
    the c datatype.
    """
    # TODO Inline?
    return dividend / divisor


def arith_int_mod(dividend: int, divisor: int) -> int:
    """
    Performs remainder on ints, raising an error if divisor is zero.
    arith_int_mod(5, 3) == 2
    arith_int_mod(-5, -3) == -2
    arith_int_mod(5, -3) == 2
    arith_int_mod(-5, -3) == -2
    """
    return dividend - divisor * int(dividend / divisor)


def isinstance_int(val: T) -> bool:
    "Python bool is a subclass of int, but Temper treats them as separate types."
    return isinstance(val, int) and not isinstance(val, bool)


def isinstance_char(val: T) -> bool:
    "Temper char are represented as single character strings."
    return isinstance(val, str) and len(val) == 1


def cast_none(val: Union[T, None]) -> T:
    "Checks that value is not None."
    if val is None:
        raise TypeError()
    return val


_cbtT = TypeVar("_cbtT")


def cast_by_type(val: Union[_cbtT, Any], py_type: Type[_cbtT]) -> _cbtT:
    "Cast to a python type by an isinstance check."
    if isinstance(val, py_type):
        return val
    else:
        raise TypeError()


def cast_by_test(val: T, predicate: Callable[[T], bool]) -> Any:
    "This cast validates that a temper function meets some predicate, e.g. callable."
    if not predicate(val):
        raise ValueError()
    return val


class Label(BaseException):
    "A label enables labled breaks with reasonably readable python."

    __slots__ = ()

    def __enter__(self) -> "Label":
        return self

    def continue_(self) -> "Label":
        "Continue to this label."
        raise self

    def break_(self) -> "Label":
        "Break out of this label."
        raise self

    def __exit__(self, _exc_type: type, exc: BaseException, _traceback: Any) -> bool:
        return exc is self


class LabelPair(BaseException):
    "Handles an edge case in the semantics of labeled breaks."

    __slots__ = ("continuing",)
    continuing: "InnerLabel"

    def __init__(self) -> None:
        self.continuing = InnerLabel()

    def __enter__(self) -> "LabelPair":
        return self

    def break_(self) -> "LabelPair":
        "Break out of this label."
        raise self

    def continue_(self) -> "InnerLabel":
        "Continue to this label."
        raise self.continuing

    def __exit__(self, _exc_type: type, exc: BaseException, _exc_tb: Any) -> bool:
        return exc is self


class InnerLabel(BaseException):
    "Continue part of a LabelPair."

    __slots__ = ()

    def __enter__(self) -> None:
        return None

    def __exit__(self, exc_type: type, exc_val: BaseException, exc_tb: Any) -> bool:
        return exc_val is self


class Symbol(object):
    "A Temper Symbol."

    __slots__ = ("_text",)
    _text: str

    def __init__(self, text: str) -> None:
        self._text = text

    @property
    def text(self) -> str:
        "Returns the text of the symbol."
        return self._text

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, Symbol):
            raise NotImplementedError()
        return self._text == other._text

    def __hash__(self) -> int:
        return hash(self._text)

    def __repr__(self) -> str:
        return f"symbol({self.text!r})"

    def __str__(self) -> str:
        return self._text


class DenseBitVector(object):
    "An expandable bitvector backed by a bytearray."

    __slots__ = ("_bytearray",)

    def __init__(self, capacity: int):
        "Capacity is in bits."
        self._bytearray = bytearray((capacity + 7) >> 3)

    def __bool__(self) -> bool:
        "Test if any bit is set."
        return bool(rb"\0" in self._bytearray)

    def __bytes__(self) -> bytes:
        "Convert the bit vector into a read-only bytes value."
        return bytes(self._bytearray.rstrip(b"\0"))

    def get(self, idx: int) -> bool:
        "Read a bit from the vector as a boolean; or false if out of bounds."
        if idx < 0:
            return False
        byte_index = idx >> 3
        if byte_index >= len(self._bytearray):
            return False
        return bool(self._bytearray[byte_index] & (1 << (idx & 7)))

    def set(self, idx: int, bit: bool) -> None:
        "set a bit in the bit vector, expanding the vector as needed."
        if idx < 0:
            raise IndexError()
        byte_array = self._bytearray
        byte_size = len(byte_array)
        byte_index = idx >> 3
        if byte_index >= byte_size:
            byte_array.extend(b"\0" * (byte_index + 1 - byte_size))
        mask = 1 << (idx & 7)
        if bit:
            byte_array[byte_index] |= mask
        else:
            byte_array[byte_index] &= ~mask


# Connected methods


def int_clamp(i: int) -> int:
    if -0x8000_0000 <= i < 0x8000_0000:
        return i
    i &= 0xFFFF_FFFF
    return i if i < 0x8000_0000 else i - 0x1_0000_0000


def int_add(a: int, b: int) -> int:
    return int_clamp(a + b)


def int_div(a: int, b: int) -> int:
    # Special casing seems to go a bit faster in `%timeit` testing.
    # Mostly concerned with b == -1, but maybe evil `a` snuck in from outside?
    if a <= -0x8000_0000 and b < 0:
        return int_clamp(int(a / b))
    return int(a / b)


def int_mul(a: int, b: int) -> int:
    return int_clamp(a * b)


def int_negate(a: int) -> int:
    if a <= -0x8000_0000:
        return int_clamp(-a)
    return -a


def int_sub(a: int, b: int) -> int:
    return int_clamp(a - b)


def int_to_string(num: int, radix: int = 10) -> str:
    "Implements connected method Int32::toString."
    if not 2 <= radix < 36:
        raise ValueError()
    elif radix == 10:
        return str(num)
    elif radix == 16:
        return f"{num:x}"
    elif radix == 8:
        return f"{num:o}"
    else:

        def seq(rem: int) -> Generator[str, None, None]:
            if rem == 0:
                yield "0"

            while rem:
                yield "0123456789abcdefghijklmnopqrstuvwxyz"[rem % radix]
                rem //= radix

            if num < 0:
                yield "-"

        return "".join(reversed(list(seq(abs(num)))))


def int64_clamp(i: int) -> int:
    if -0x8000_0000_0000_0000 <= i < 0x8000_0000_0000_0000:
        return i
    i &= 0xFFFF_FFFF_FFFF_FFFF
    return i if i < 0x8000_0000_0000_0000 else i - 0x1_0000_0000_0000_0000


def int64_add(a: int, b: int) -> int:
    return int64_clamp(a + b)


def int64_div(a: int, b: int) -> int:
    # Special casing seems to go a bit faster in `%timeit` testing.
    # Mostly concerned with b == -1, but maybe evil `a` snuck in from outside?
    if a <= -0x8000_0000_0000_0000 and b < 0:
        return int64_clamp(int(a / b))
    return int(a / b)


def int64_mul(a: int, b: int) -> int:
    return int64_clamp(a * b)


def int64_negate(a: int) -> int:
    if a <= -0x8000_0000_0000_0000:
        return int64_clamp(-a)
    return -a


def int64_sub(a: int, b: int) -> int:
    return int64_clamp(a - b)


def int64_to_float64(value: int) -> float:
    "Implements connected method Int64::toFloat64."
    if -0x20_0000_0000_0000 < value < 0x20_0000_0000_0000:
        return float(value)
    raise OverflowError()


def int64_to_int32(value: int) -> float:
    "Implements connected method Int64::toInt32."
    if -0x8000_0000 <= value <= 0x7FFF_FFFF:
        return int(value)
    raise OverflowError()


def int64_to_int32_unsafe(value: int) -> float:
    "Implements connected method Int64::toInt32Unsafe."
    return int_clamp(int(value))


def float64_max(x: float, y: float) -> float:
    "Implements connected method Float64::max."
    # Already returns nan if x is nan.
    return nan if isnan(y) else max(x, y)


def float64_min(x: float, y: float) -> float:
    "Implements connected method Float64::min."
    # Already returns nan if x is nan.
    return nan if isnan(y) else min(x, y)


def float64_near(
    x: float,
    y: float,
    rel_tol: Optional[float] = Unset,
    abs_tol: Optional[float] = Unset,
) -> float:
    "Implements connected method Float64::near."
    # This exactly matches isclose behavior, but matching our forwarding our
    # optionals to python named args is awkward, so duplicate the logic.
    # TODO We could possibly inline usage of isclose instead of this logic.
    if rel_tol is Unset:
        rel_tol = 1e-9
    if abs_tol is Unset:
        abs_tol = 0.0
    return isclose(x, y, rel_tol=rel_tol, abs_tol=abs_tol)


def float64_sign(x: float) -> float:
    "Implements connected method Float64::sign."
    return x if isnan(x) or x == 0.0 else copysign(1.0, x)


def float64_to_int(value: float) -> int:
    "Implements connected method Float64::toInt32."
    if -0x8000_0001 < value < 0x8000_0000:
        return int(value)
    if isnan(value):
        raise ValueError()
    else:
        raise OverflowError()


def float64_to_int_unsafe(value: float) -> int:
    "Implements connected method Float64::toInt32Unsafe."
    try:
        # Checks limits first to avoid absurdly large ints.
        return float64_to_int(value)
    except OverflowError:
        # TODO If a common case, is this faster to void exceptions?
        return -0x8000_0000 if value < 0 else 0x7FFF_FFFF
    except ValueError:  # NaN
        return 0


def float64_to_int64(value: float) -> int:
    "Implements connected method Float64::toInt64."
    if -0x1F_FFFF_FFFF_FFFF <= value <= 0x1F_FFFF_FFFF_FFFF:
        return int(value)
    if isnan(value):
        raise ValueError()
    else:
        raise OverflowError()


def float64_to_int64_unsafe(value: float) -> int:
    "Implements connected method Float64::toInt64Unsafe."
    # Avoid converting crazy large float to int.
    if -0x8000_0000_0000_0000 <= value <= 0x7FFF_FFFF_FFFF_FFFF:
        return int(value)
    elif isnan(value):
        return 0
    else:
        return -0x8000_0000_0000_0000 if value < 0 else 0x7FFF_FFFF_FFFF_FFFF


def _ensure_dot_frac(text: str) -> str:
    if "." in text:
        return text
    elif "e" in text:
        return text.replace("e", ".0e")
    else:
        return text + ".0"


def float64_to_string(value: float) -> str:
    "Implements connected method Float64::toString."
    if value == inf:
        return "Infinity"
    elif value == -inf:
        return "-Infinity"
    elif isnan(value):
        return "NaN"
    else:
        return _ensure_dot_frac(str(value))


def boolean_to_string(value: bool) -> str:
    "Turns a stirng into a boolean (lowercase like temper)."
    return "true" if value else "false"


def date_to_string(value: Date) -> str:
    "Turns a date into a string, YYYY-MM-DD."
    return f"{value.year:04d}-{value.month:02d}-{value.day:02d}"


# Converts a date from a string in YYYY-MM-DD format
date_from_iso_string = Date.fromisoformat


utc = timezone.utc


def date_today() -> Date:
    "Gets today's date as in UTC+0."
    return datetime.now(utc).date()


def years_between(start: Date, end: Date) -> int:
    year_delta = end.year - start.year
    month_delta = end.month - start.month
    if month_delta < 0 or (month_delta == 0 and end.day < start.day):
        year_delta -= 1
    return year_delta


def string_get(s: str, i: int) -> int:
    if i < 0:
        raise IndexError()
    return ord(s[i])


def string_count_between(s: str, left: int, right: int) -> int:
    n = len(s)
    left = min(left, n)
    right = max(left, min(right, n))
    return right - left


def string_for_each(s: str, f: Callable[[int], None]) -> None:
    for c in s:
        f(ord(c))


def string_has_at_least(s: str, left: int, right: int, min_count: int) -> bool:
    return string_count_between(s, left, right) >= min_count


def string_next(s: str, i: int) -> int:
    return min(len(s), i + 1)


def string_prev(s: str, i: int) -> int:
    return max(0, i - 1)


def string_step(s: str, i: int, by: int) -> int:
    if by >= 0:
        for _ in range(by):
            old_i = i
            i = string_next(s, i)
            if i == old_i:
                break
    else:
        for _ in range(-by):
            old_i = i
            i = string_prev(s, i)
            if i == old_i:
                break
    return i


def string_from_code_point(code_point: int) -> str:
    if code_point >= 0xD800 and code_point <= 0xDFFF:
        raise ValueError(f"invalid Unicode scalar value {code_point:X}")
    return chr(code_point)


def require_string_index(i: int) -> int:
    "Checked cast from i to StringIndex, an alias for a non-negative int"
    if i >= 0:
        return i
    raise AssertionError(f"require_string_index; {i!r} not >= 0 ")


def require_no_string_index(i: int) -> int:
    "Checked cast from i to NoStringIndex, a negative int"
    if i < 0:
        return -1
    raise AssertionError(f"require_string_index; {i!r} not < 0 ")


utf32_type_code: str = ""


def string_from_code_points(code_points: Iterable[int]) -> str:
    global utf32_type_code
    # Find system needs if not yet initialized.
    if utf32_type_code == "":
        # Rely on at least one of these to be 4 bytes.
        utf32_type_code = next((_ for _ in ["I", "L"] if array(_, []).itemsize == 4))
    return array(utf32_type_code, code_points).tobytes().decode("utf-32")


def string_split(string: str, separator: str) -> Sequence[str]:
    "split a string, returning a list of elements."
    return tuple(string.split(separator)) if separator else tuple(string)


def string_to_float64(string: str) -> float:
    "Convert string to float following Temper rules"
    # Presume the common case, which also handles "NaN" and "Infinity".
    result = float(string)
    trimmed = string.strip()
    # But guard against things not in Temper.
    if (
        trimmed.startswith(".")
        or trimmed.endswith(".")
        or (isnan(result) and string != "NaN")
        or (isinf(result) and not string.endswith("Infinity"))
    ):
        raise ValueError(string)
    return result


def string_to_int32(string: str, radix: Optional[int] = None) -> float:
    if radix == 0:
        # Other values we reject are checked already.
        raise ValueError()
    value = int(string, radix or 10)
    if -0x8000_0000 <= value <= 0x7FFF_FFFF:
        return value
    raise OverflowError()


def string_to_int64(string: str, radix: Optional[int] = None) -> float:
    if radix == 0:
        # Other values we reject are checked already.
        raise ValueError()
    value = int(string, radix or 10)
    if -0x8000_0000_0000_0000 <= value <= 0x7FFF_FFFF_FFFF_FFFF:
        return value
    raise OverflowError()


def list_filter(lst: Sequence[T], predicate: Callable[[T], bool]) -> Sequence[T]:
    "Filter a list of elements, aborting on no-result."
    return tuple(i for i in lst if predicate(i))


def list_for_each(lst: Sequence[T], action: Callable[[T], None]) -> None:
    "Execute a function on each element of the sequence in order"
    for el in lst:
        action(el)


def list_get(lst: Sequence[T], index: int) -> T:
    "Get an item from a list by index."
    if index < 0:  # Prohibit python index semantics
        raise IndexError()
    return lst[index]


def list_get_or(lst: Sequence[T], index: int, default: T) -> T:
    "Get an item from a list by index with a default."
    if 0 <= index < len(lst):
        return lst[index]
    else:
        return default


_lbaT = TypeVar("_lbaT")


def list_builder_add(
    lst: MutableSequence[_lbaT], elem: _lbaT, at: Optional[int] = Unset
) -> None:
    "Append a single element to a list."
    lst_len: int = len(lst)
    if at is Unset or at == lst_len:
        lst.append(elem)
    else:
        if at < 0 or at > lst_len:
            raise IndexError()
        lst.insert(at, elem)


def list_builder_add_all(
    lst: MutableSequence[T], elems: Sequence[T], at: Optional[int] = Unset
) -> None:
    "Append multiple elements to a list."
    lst_len: int = len(lst)
    if at is Unset or at == lst_len:
        lst.extend(elems)
    else:
        if at < 0 or at > lst_len:
            raise IndexError()
        lst[at:at] = elems


def list_join(lst: Sequence[T], separator: str, stringifier: Callable[[T], str]) -> str:
    "Join a list of items after converting them to strings."
    return separator.join(stringifier(i) for i in lst)


def list_map(lst: Sequence[T], func: Callable[[T], U]) -> Sequence[U]:
    "Map a list of elements."
    return tuple(func(i) for i in lst)


_lbrT = TypeVar("_lbrT")


def list_builder_reverse(lst: MutableSequence[_lbrT]) -> None:
    "Reverses a list in place."
    lst.reverse()


_lbspT = TypeVar("_lbspT")


def list_builder_splice(
    lst: MutableSequence[_lbspT],
    index: Optional[int] = Unset,
    remove_count: Optional[int] = Unset,
    new_values: Optional[Sequence[_lbspT]] = Unset,
) -> Sequence[_lbspT]:
    "Remove some items and insert others."
    # Work through defaults and bounds.
    if index is Unset or index < 0:
        index = 0
    if remove_count is Unset:
        remove_count = len(lst)
    elif remove_count < 0:
        remove_count = 0
    # Now take care of business.
    end_index = index + remove_count
    result = tuple(lst[index:end_index])
    lst[index:end_index] = () if new_values is Unset else new_values
    return result


_lbsT = TypeVar("_lbsT")


def list_builder_set(lst: MutableSequence[_lbsT], idx: int, val: _lbsT) -> None:
    "set a list element, bubbling if out of bounds, or void on success."
    if idx < 0:
        raise IndexError()
    lst[idx] = val


_lbsoT = TypeVar("_lbsoT")


def list_builder_sort(
    lst: List[_lbsoT], compare: Callable[[_lbsoT, _lbsoT], int]
) -> None:
    return lst.sort(key=cmp_to_key(compare))


_lbmdT = TypeVar("_lbmdT")


def list_map_dropping(
    lst: Sequence[_lbmdT], func: Callable[[_lbmdT], Union[_lbmdT, NoReturn]]
) -> Sequence[_lbmdT]:
    "Map a list of elements, omitting any for which func raises any Exception."
    results = []
    for entry in lst:
        try:
            value = func(entry)
        except Exception:
            continue
        results.append(value)
    return tuple(results)


_lbslT = TypeVar("_lbslT")


def list_slice(
    lst: Sequence[_lbslT], start_inclusive: int, end_exclusive: int
) -> Sequence[_lbslT]:
    "Almost exactly a Python slice, but indices are constrained to be >= 0."
    # TODO(tjp): Cheaper to always say tuple here, or separate out for ListBuilder use?
    return tuple(lst[max(start_inclusive, 0) : max(end_exclusive, 0)])


_lrT = TypeVar("_lrT")


def listed_reduce(
    lst: Sequence[_lrT], accumulate: Callable[[_lrT, _lrT], _lrT]
) -> _lrT:
    return reduce(accumulate, lst)


_lrfT = TypeVar("_lrfT")
_lrfU = TypeVar("_lrfU")


def listed_reduce_from(
    lst: Sequence[_lrfT], initial: _lrfU, accumulate: Callable[[_lrfU, _lrfT], _lrfU]
) -> _lrfU:
    return reduce(accumulate, lst, initial)


def listed_sorted(lst: Sequence[T], compare: Callable[[T, T], int]) -> Sequence[T]:
    return tuple(sorted(lst, key=cmp_to_key(compare)))


def listed_to_list(lst: Sequence[T]) -> Sequence[T]:
    if isinstance(lst, tuple):
        return lst
    else:
        return tuple(lst)


def deque_remove_first(deq: Deque[T]) -> T:
    "Defer to deque.popleft, except bubbling when the deque is empty."
    return deq.popleft()


def dense_bit_vector_set(instance: DenseBitVector, idx: int, bit: bool) -> None:
    "sets a bit within a dense bit vector."
    instance.set(idx, bit)


Key = TypeVar("Key")
Value = TypeVar("Value")


class Pair(Generic[Key, Value]):
    """
    A pair of a key and a value.
    Often used as Map entries
    """

    key: Key
    value: Value
    __slots__ = ("key", "value")

    def __init__(self, key: Key, value: Value) -> None:
        self.key = key
        self.value = value

    def __subscript__(self, index: int) -> Union[Key, Value]:
        if index == 0:
            return self.key
        elif index == 1:
            return self.value
        else:
            raise IndexError()


def map_constructor(
    entries: Sequence[Pair[Key, Value]],
) -> MappingProxyType[Key, Value]:
    "Implements Map's constructor."
    return MappingProxyType({entry.key: entry.value for entry in entries})


def map_builder_set(builder: Dict[Key, Value], key: Key, value: Value) -> None:
    "Sets entry on a MapBuilder."
    builder[key] = value


def mapped_has(mapped: Mapping[Key, Value], key: Key) -> bool:
    "Checks if a Mapped object has a Key key"
    # This can't be inlined well because order (mapped -> key) is swapped for
    # (key -> mapped). Which would be an issue if mapped and key were
    # expressions with SideEffects.
    return key in mapped


def mapped_to_map(mapped: Mapping[Key, Value]) -> MappingProxyType[Key, Value]:
    "Converts a Mapped to a Map."
    return MappingProxyType(dict(mapped))


def mapped_to_list(mapped: Mapping[Key, Value]) -> Sequence[Pair[Key, Value]]:
    "Implements .toList() for Mapping objects."
    return tuple(Pair(key, mapped[key]) for key in mapped.keys())


def mapped_to_list_builder(
    mapped: Mapping[Key, Value],
) -> MutableSequence[Pair[Key, Value]]:
    "Implements .toListBuilder() for Mapping objects."
    return list(Pair(key, mapped[key]) for key in mapped.keys())


def mapped_to_list_with(
    mapped: Mapping[Key, Value], func: Callable[[Key, Value], T]
) -> Sequence[T]:
    "Implements .toListWith{...} for Mapping objects."
    return tuple(func(key, mapped[key]) for key in mapped.keys())


def mapped_to_list_builder_with(
    mapped: Mapping[Key, Value], func: Callable[[Key, Value], T]
) -> MutableSequence[T]:
    "Implements .toListBuilderWith{...} for Mapping objects."
    return list(func(key, mapped[key]) for key in mapped.keys())


def mapped_for_each(
    mapped: Mapping[Key, Value], func: Callable[[Key, Value], None]
) -> None:
    "Implements .forEach {...} for Mapping objects."
    for key, value in mapped.items():
        func(key, value)


# Async support
# The executor used for Temper async{...} calls.
_executor = ThreadPoolExecutor()
# This lock guards _unresolved_count
_unresolved_count_lock = threading.Lock()
_unresolved_count = 0
# Signal this whenever _unresolved_count drops to 0
_all_resolved = threading.Event()
_all_resolved.set()


def _increment_unresolved_count() -> None:
    global _unresolved_count
    with _unresolved_count_lock:
        _unresolved_count += 1
        _all_resolved.clear()


def _decrement_unresolved_count() -> None:
    global _unresolved_count
    with _unresolved_count_lock:
        _unresolved_count -= 1
        if _unresolved_count <= 0:
            _all_resolved.set()


_nbuT = TypeVar("_nbuT")


# Support for Temper `new PromiseBuilder()`.
def new_unbound_promise() -> Future[_nbuT]:  # FTS [T] -> Future[T]
    global _unresolved_count
    with _unresolved_count_lock:
        _unresolved_count += 1
        _all_resolved.clear()
    p: Future[_nbuT] = Future()  # FTS [T]
    p.add_done_callback(lambda _: _decrement_unresolved_count())
    return p


# adapt_generator_factory routes a do_await function
# to Temper coroutine bodies so that they can yield
# one of these to indicate that the coroutine is awaiting
# the wrapped promise.
_AwaitingT = TypeVar("_AwaitingT")


class _Awaiting(Generic[_AwaitingT]):
    def __init__(self, future: Future[_AwaitingT]) -> None:
        self.future = future


_sacT = TypeVar("_sacT")


# Called to start a Temper coroutine when it is launched
# via `async{...}` and when a promise it is awaiting resolves.
def _step_async_coro(
    generator: Generator[None, _sacT, None],
    p: Optional[Future[_sacT]],
) -> None:
    """
    Start/resume a coroutine that might use await.

    When representing a possibly asynchronous coroutine as a generator, we
    need to step that generator.

    This happens in two cases:
    - we launch the coroutine so we need to step it once
    - a promise resolves that it was waiting on, so we need to send back the
      resolution (either a result or an exception) and start it running
    """
    yielded: Optional[Exception] = None
    # The caller must have incremented the unresolved count.
    # This try/finally takes care to decrement or hand responsibility
    # to decrement to another call of the same function.
    try:
        result: Optional[_sacT] = None
        exc = None

        if p is not None:
            exc = p.exception()
            if exc is None:
                result = p.result()
        try:
            if exc is not None:
                yielded = generator.throw(exc)
            else:
                yielded = generator.send(cast(_sacT, result))
        except StopIteration:
            pass
        except Exception as e:
            print_exception(type(e), value=e, tb=e.__traceback__)
            raise e
    finally:
        # If we stopped at another await call, register for another turn.
        if isinstance(yielded, _Awaiting):
            future = yielded.future

            # The next step call takes responsibility for decrementing the unresolved count. # noqa: E501
            def done_callback(p: Optional[Future[_sacT]]) -> None:
                _step_async_coro(generator, p)

            future.add_done_callback(done_callback)
        else:
            # There is no following step call responsible for decrementing
            # the count
            _decrement_unresolved_count()


_agfT = TypeVar("_agfT")
# _agfARGS = TypeVarTuple("_agfARGS") # Future type syntax


def adapt_generator_factory(
    make_generator: Callable[..., Generator[None, _agfT, None]],
    # FTS: Callable[[Callable[[Future[_agfT]], _Awaiting[_agfT]], _agfARGS], Generator[None, _agfT, None]] # noqa: E501
) -> Callable[..., Generator[None, _agfT, None]]:  # FTS: [_agfARGS],
    """
    Take a generator factory and make it produce generators that
    know how to await.  The yielding function underlying the generator
    gets a `do_await` argument, before its regular initialization argument
    list.

    Then it can await by doing `yield do_await(some_future)`.
    The `yield` cedes control, and the `send` use in `_step_async_coro`
    allows for communicating the future's result  back to the generator
    body.
    """

    def make_awaiting_generator(
        *args: Any,  # FTS: _agfARGS
    ) -> Generator[None, _agfT, None]:
        def do_await(p: Future[_agfT]) -> _Awaiting[_agfT]:
            return _Awaiting(p)

        generator: Generator[None, _agfT, None] = make_generator(do_await, *args)
        return generator

    return make_awaiting_generator


def async_launch(generator_factory: Callable[[], Generator[None, T, None]]) -> None:
    """
    Launch a coroutine, stepping it once in another thread so that
    it does some work.  Subsequent stepping is in response to
    promise resolution when it yields via its `do_await`.
    """
    generator = generator_factory()
    # No this increment will be undone by _step_async_coro
    _increment_unresolved_count()
    _executor.submit(_step_async_coro, generator, None)


def await_safe_to_exit() -> None:
    """
    Called by generated main methods to allow async tasks to complete.
    """
    _all_resolved.wait()
    _executor.shutdown()


def complete_promise(p: Future[T], resolution: T) -> None:
    """
    Resolves the promise with the given value, but, like Temper
    PromiseBuilder, second and subsequent resolutions are no-ops.
    """
    try:
        p.set_result(resolution)
        # The illegal state exception is not public in concurrent.futures.
    except Exception:
        pass


def break_promise(p: Future[T], exc: Union[BaseException, None] = None) -> None:
    """
    Resolves the promise exceptionally, but, like Temper
    PromiseBuilder, second and subsequent resolutions are no-ops.
    """
    if exc is None:
        exc = make_bubble_exception()
    try:
        p.set_exception(exc)
        # The illegal state exception is not public in concurrent.futures.
    except Exception:
        pass


class NetResponse:
    def __init__(
        self,
        status: int,
        headers: Dict[str, str],
        body: Future[str],
    ):
        self.status = status
        self.headers = headers
        self._body = body

    @property
    def content_type(self) -> str:
        return self.headers["content-type"]

    @property
    def text(self) -> Future[str]:
        return self._body


# Support for std/net
def std_net_send(
    url: str,
    method: str,
    body_content: Optional[str],
    body_mime_type: Optional[str],
) -> Future[NetResponse]:
    # TODO: use aiohttp for truly asynchronous fetching
    # This is good enough for the infrequent client use case.
    data = None
    if body_content is not None:
        data = body_content.encode("utf-8")
    headers = {}
    if body_mime_type is not None:
        headers["content-type"] = body_mime_type
    request = urllib.request.Request(url, data, headers, method=method)
    net_response_future: Future[NetResponse] = new_unbound_promise()
    body_future: Future[str] = new_unbound_promise()

    def do_fetch():
        if False:
            yield None  # Mark as a generator
        with urllib.request.urlopen(request) as response:
            try:
                # TODO: use response headers to find body encoding.
                body_future.set_result(response.read().decode("utf-8"))
            except IOError as e:
                body_future.set_exception(e)
            net_response_future.set_result(
                NetResponse(
                    status=response.status, headers=response.headers, body=body_future
                )
            )

    async_launch(lambda: do_fetch())
    return net_response_future


# Utility functions


def _utf8_size(code_point: int) -> int:
    if 0 <= code_point < 0o200:
        return 1
    elif 0o200 <= code_point < 0o4000:
        return 2
    elif 0o4000 <= code_point < 0o100000:
        return 3
    else:
        return 4


# never gets hit, so this is just to skip a check in the below function
_utf8_never = (0, 0, 0)

# (and_mask, or_mask, shift) triplets for each byte depending on the high 4 bits of a
# codepoint in UTF-8 encoding.
#
# | First code point | Last code point | Byte 0    | Byte 1    | Byte 2    | Byte 3    |
# | ---------------- | --------------- | --------- | --------- | --------- | --------- |
# | U+0000           | U+007F          | 0xxx_xxxx |           |           |           |
# | U+0080           | U+07FF          | 110x_xxxx | 10xx_xxxx |           |           |
# | U+0800           | U+FFFF          | 1110_xxxx | 10xx_xxxx | 10xx_xxxx |           |
# | U+10000          | U+10FFFF        | 1111_0xxx | 10xx_xxxx | 10xx_xxxx | 10xx_xxxx |
_utf8_byte_infos = (
    (0b0111_1111, 0, 0),
    _utf8_never,
    _utf8_never,
    _utf8_never,
    #
    (0b0001_1111, 0b1100_0000, 6),
    (0b0011_1111, 0b1000_0000, 0),
    _utf8_never,
    _utf8_never,
    #
    (0b0000_1111, 0b1110_0000, 12),
    (0b0011_1111, 0b1000_0000, 6),
    (0b0011_1111, 0b1000_0000, 0),
    _utf8_never,
    #
    (0b0000_0111, 0b1111_0000, 18),
    (0b0011_1111, 0b1000_0000, 12),
    (0b0011_1111, 0b1000_0000, 6),
    (0b0011_1111, 0b1000_0000, 0),
)


def _utf8_byte_of(code_point: int, byte_offset: int, n_bytes: int) -> int:
    and_mask, or_mask, shift = _utf8_byte_infos[(n_bytes - 1) * 4 + byte_offset]
    return ((code_point >> shift) & and_mask) | or_mask


def _utf16_size(char: str) -> int:
    return 1 + (ord(char) >= 0x10000)
