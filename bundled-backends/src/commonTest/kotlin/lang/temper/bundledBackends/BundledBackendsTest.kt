package lang.temper.bundledBackends

import lang.temper.be.cpp.CppBackend
import lang.temper.be.csharp.CSharpBackend
import lang.temper.be.java.JavaBackend
import lang.temper.be.js.JsBackend
import lang.temper.be.lua.LuaBackend
import lang.temper.be.py.PyBackend
import lang.temper.be.rust.RustBackend
import lang.temper.common.console
import lang.temper.supportedBackends.availableBackends
import lang.temper.supportedBackends.lookupFactory
import lang.temper.supportedBackends.supportedBackends
import kotlin.test.Test
import kotlin.test.assertEquals

class BundledBackendsTest {
    @Test
    fun hasDefaultFactories() {
        val wanted = setOf(
            CppBackend.Cpp11,
            CSharpBackend.Factory,
            JavaBackend.Java8,
            JavaBackend.Java17,
            JsBackend.Factory,
            LuaBackend.Lua51,
            PyBackend.MypyC,
            PyBackend.Python3,
            RustBackend.Factory,
        )
        val got = wanted.filter {
            it == lookupFactory(it.backendId)
        }.toSet()
        assertEquals(wanted, got)
    }

    /**
     * <!-- snippet: backends/supported -->
     * # Supported Backends
     *
     * ⎀ backend/csharp
     *
     * ⎀ backend/java
     *
     * ⎀ backend/java8
     *
     * ⎀ backend/js
     *
     * ⎀ backend/lua
     *
     * ⎀ backend/mypyc
     *
     * ⎀ backend/py
     *
     * ⎀ backend/rust
     */
    @Test
    fun supported() {
        val wanted = setOf(
            CSharpBackend.Factory.backendId,
            JavaBackend.Java8.backendId,
            JavaBackend.Java17.backendId,
            JsBackend.Factory.backendId,
            LuaBackend.Lua51.backendId,
            PyBackend.MypyC.backendId,
            PyBackend.Python3.backendId,
            RustBackend.Factory.backendId,
        )
        val got = wanted.filter {
            it in supportedBackends
        }.toSet()
        assertEquals(wanted, got)
    }

    /**
     * <!-- snippet: default-backends -->
     * Default backends are C#, Java 17, JS, Lua 5.1, Python, and Rust. These are identified
     * by backend names "csharp", "java", "js", "lua", "py", and "rust".
     */
    @Test
    fun defaultSupported() {
        val wanted = setOf(
            CSharpBackend.Factory.backendId,
            // Leave Java 8 out by default. That's only for people who care enough.
            JavaBackend.Java17.backendId,
            JsBackend.Factory.backendId,
            LuaBackend.Lua51.backendId,
            PyBackend.Python3.backendId,
            RustBackend.Factory.backendId,
        )
        val got = wanted.filter {
            it in supportedBackends
        }.toSet()
        assertEquals(wanted, got)
    }

    @Test
    fun backendsLoadOk() {
        val wanted = availableBackends.toSet()
        val got = wanted.filter { id ->
            try {
                val f = lookupFactory(id)
                f?.backendId == id
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                console.error(e)
                false
            }
        }.toSet()
        assertEquals(wanted, got)
    }
}
