import unittest as ut
import temper_core as rt

class TestAsync(ut.TestCase):
    def test_async(self):
        from concurrent.futures import Future
        p = Future()
        q = Future()

        def yielder(do_await):
            x = yield do_await(p) # translation of `x = await p`
            q.set_result(x)

        adapted_yielder = rt.adapt_generator_factory(yielder)

        rt.async_launch(adapted_yielder)
        p.set_result('result')

        self.assertEqual('result', q.result())
