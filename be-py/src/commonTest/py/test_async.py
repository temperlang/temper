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

    def test_resume_from_other_thread_runs_on_scheduler(self):
        import threading
        from concurrent.futures import Future
        p = Future()
        q = Future()
        thread_names = []

        def yielder(do_await):
            thread_names.append(threading.current_thread().name)
            x = yield do_await(p)
            thread_names.append(threading.current_thread().name)
            q.set_result(x)

        rt.async_launch(rt.adapt_generator_factory(yielder))
        completer = threading.Thread(target=lambda: p.set_result('late'))
        completer.start()
        completer.join()

        self.assertEqual('late', q.result(timeout=10))
        self.assertEqual(['temper-async', 'temper-async'], thread_names)
