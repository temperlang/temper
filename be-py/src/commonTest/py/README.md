These are run via Kotlin @Test class PyRuntimeTest.

You can run them locally thus:

    cd be-py/src/commonTest/py/
    PYTHONPATH=../../commonMain/resources/lang/temper/be/py/temper-core/ \
        python3 -m unittest test_*
