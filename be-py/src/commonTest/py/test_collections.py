import unittest as ut
import temper_core as rt
from collections import deque


class TestLists(ut.TestCase):
    def test_get_okay(self):
        self.assertEqual(5, rt.list_get([4, 5, 6], 1))

    def test_get_fail_negative(self):
        self.assertRaises(
            IndexError,
            lambda: rt.list_get([4, 5, 6], -1)
        )

    def test_get_fail_oob(self):
        self.assertRaises(
            IndexError,
            lambda: rt.list_get([4, 5, 6], 3)
        )

    def test_get_or_okay(self):
        self.assertEqual(5, rt.list_get_or([4, 5, 6], 1, 99))

    def test_get_or_fail_negative(self):
        self.assertEqual(99, rt.list_get_or([4, 5, 6], -1, 99))

    def test_get_or_fail_oob(self):
        self.assertEqual(99, rt.list_get_or([4, 5, 6], 3, 99))

    @staticmethod
    def simple_stringify(x):
        val = "&" + str(x)
        return val

    def test_join_okay(self):
        self.assertEqual(
            "&foo--&bar--&qux",
            rt.list_join(["foo", "bar", "qux"], "--", self.simple_stringify),
        )

    def test_join_okay2(self):
        self.assertEqual(
            "&4--&5--&6", rt.list_join([4, 5, 6], "--", self.simple_stringify)
        )

    @staticmethod
    def stop_stringify(x):
        val = "&" + str(x)
        if x == 5:
            raise ValueError()
        return val

    def test_join_stopping(self):
        self.assertRaises(
            ValueError,
            lambda: rt.list_join([4, 5, 6], "--", self.stop_stringify)
        )

    @staticmethod
    def map10(x):
        val = x * 10
        return val

    def test_map_okay(self):
        self.assertEqual((40, 50, 60), rt.list_map([*range(4, 7)], self.map10))

    @staticmethod
    def stop_map10(x):
        if x == 5:
            raise ValueError()
        return 10 * x

    def test_map_stopping(self):
        self.assertRaises(
            ValueError,
            lambda: rt.list_map(tuple(range(4, 7)), self.stop_map10)
        )

    def test_string_split(self):
        self.assertEqual(
            ("foo", "bar", "qux"),
            rt.string_split("foo--bar--qux", "--"),
        )

    @staticmethod
    def when_odd(x):
        val = bool(x & 1)
        return val

    def test_filter_okay(self):
        self.assertEqual((1, 3, 5, 7, 9), rt.list_filter([*range(10)], self.when_odd))

    @staticmethod
    def stop5_odd(x):
        if x == 5:
            raise ValueError()
        return bool(x & 1)

    def test_filter_stopping(self):
        self.assertRaises(
            ValueError,
            lambda: rt.list_filter(tuple(range(10)), self.stop5_odd)
        )


class TestDeques(ut.TestCase):
    def deque456(self):
        deq = deque()
        deq.append(4)
        deq.append(5)
        deq.append(6)
        return deq

    def test_add(self):
        # Notional as these methods are implemented inline
        deq = self.deque456()
        self.assertEqual([4, 5, 6], list(deq))

    def test_remove_first(self):
        deq = self.deque456()
        pop = rt.deque_remove_first(deq)
        self.assertEqual(4, pop)
        self.assertEqual([5, 6], list(deq))

    def test_remove_first_empty(self):
        deq = deque()
        self.assertRaises(
            IndexError,
            lambda: rt.deque_remove_first(deq)
        )
        self.assertEqual([], list(deq))


class TestDenseBitVectors(ut.TestCase):
    def test_construct_0(self):
        dbv = rt.DenseBitVector(0)
        self.assertFalse(dbv)
        self.assertEqual(dbv._bytearray, b"")

    def test_construct_100(self):
        dbv = rt.DenseBitVector(100)
        self.assertFalse(dbv)
        self.assertEqual(dbv._bytearray, b"\0" * 13)

    def test_get_set(self):
        dbv = rt.DenseBitVector(19)
        for idx in (2, 3, 7, 3, 11, 5, 7, 13):
            dbv.set(idx, True)
        self.assertEqual(b"\xac\x28", bytes(dbv))
        self.assertEqual(
            [2, 3, 5, 7, 11, 13], [idx for idx in range(-50, 50) if dbv.get(idx)]
        )
        for idx in (2, 4, 11, 12):
            dbv.set(idx, False)
        self.assertEqual(b"\xa8\x20", bytes(dbv))
        for idx in (11, 7, 17, 29, 13, 23, 47):
            dbv.set(idx, True)
        self.assertEqual(b"\xa8\x28\x82\x20\x00\x80", bytes(dbv))
        self.assertEqual(
            [3, 5, 7, 11, 13, 17, 23, 29, 47],
            [idx for idx in range(-50, 50) if dbv.get(idx)],
        )


if __name__ == "__main__":
    ut.main()
