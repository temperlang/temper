import unittest as ut
import temper_core as rt


class TestIntToString(ut.TestCase):
    def test_simple(self):
        self.maxDiff = None
        nums = [-20, -16, -12, -8, -4, 0, 4, 8, 10, 20]
        radices = [2, 3, 5, 8, 10, 11, 16]
        table = [
            "".join("%7s" % (rt.int_to_string(num, radix),) for num in nums)
            for radix in radices
        ]

        self.assertEqual(
            table,
            [
                " -10100 -10000  -1100  -1000   -100      0    100   1000   1010  10100",
                "   -202   -121   -110    -22    -11      0     11     22    101    202",
                "    -40    -31    -22    -13     -4      0      4     13     20     40",
                "    -24    -20    -14    -10     -4      0      4     10     12     24",
                "    -20    -16    -12     -8     -4      0      4      8     10     20",
                "    -19    -15    -11     -8     -4      0      4      8      a     19",
                "    -14    -10     -c     -8     -4      0      4      8      a     14",
            ],
        )


class TestBooleanToString(ut.TestCase):
    def test_true(self):
        self.assertEqual("true", rt.boolean_to_string(True))

    def test_false(self):
        self.assertEqual("false", rt.boolean_to_string(False))
