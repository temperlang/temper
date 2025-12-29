import unittest as ut
import temper_core as rt


str = "κόσμε\U0001D20E--"
#          κ      ό       σ      μ      ε      \ud834\ude0e  -     -
str_cps = [0x3BA, 0x1F79, 0x3C3, 0x3BC, 0x3B5, 0x1D20E,      0x2D, 0x2D]

valid_string_indices = [0, 1, 2, 3, 4, 5, 6, 7, 8]
valid_and_invalid_string_indices = [*valid_string_indices, 9]


class TestStringHelpers(ut.TestCase):
    def test_string_count_between(self):
        for left in valid_and_invalid_string_indices:
            for right in valid_and_invalid_string_indices:
                valid_left = min(len(str), left)
                valid_right = max(left, min(len(str), right))

                actual_count = 0
                for c in str[valid_left:valid_right]:
                    actual_count += 1

                self.assertEqual(
                    rt.string_count_between(str, left, right),
                    actual_count,
                    msg = f"left={left}, right={right}"
                )
    def test_has_at_least(self):
        for left in valid_and_invalid_string_indices:
            for right in valid_and_invalid_string_indices:
                valid_left = min(len(str), left)
                valid_right = max(left, min(len(str), right))

                actual_count = 0
                for c in str[valid_left:valid_right]:
                    actual_count += 1

                actual_results = {}
                desired_results = {}
                for min_count in range(-1, 13):
                    desired_results[min_count] = min_count <= actual_count
                    actual_results[min_count] = \
                        rt.string_has_at_least(str, left, right, min_count)
                self.assertEqual(
                    repr(actual_results),
                    repr(desired_results),
                    msg = f"left={left}, right={right}"
                )
    def test_for_each(self):
        desired = []
        for c in str:
            desired.append(ord(c))
        actual = []
        rt.string_for_each(str, lambda cp: actual.append(cp))
        self.assertEqual(actual, desired)

    def test_prev_and_next(self):
        # From each valid index, compute the previous as long as we're monotonic,
        # and then compare.
        nexts = []
        i = 0
        while True:
            nexts.append(i)
            i_next = rt.string_next(str, i)
            if i == i_next: break
            if not (i_next > i):
                raise Exception(f"string_next: {i_next} not monotinic wrt {i}")
            i = i_next

        prevs = []
        i = len(str)
        while True:
            prevs.append(i)
            i_prev = rt.string_prev(str, i)
            if i == i_prev: break
            if not (i_prev < i):
                raise Exception(f"string_prev: {i_prev} not monotinic wrt {i}")
            i = i_prev

        valid_string_indices_rev = valid_string_indices[:]
        valid_string_indices_rev.reverse()

        self.assertEqual(
            {
                'nexts': valid_string_indices,
                'prevs': valid_string_indices_rev,
            },
            {
                'nexts': nexts,
                'prevs': prevs,
            }
        )
