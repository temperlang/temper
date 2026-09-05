package lang.temper.interp

import lang.temper.env.BindingNamingContext
import lang.temper.env.InterpMode
import lang.temper.log.MessageTemplate
import lang.temper.stage.Stage
import lang.temper.type.Abstractness
import lang.temper.type.NominalType
import lang.temper.type.TypeShape
import lang.temper.type2.Signature2
import lang.temper.value.CallableValue
import lang.temper.value.CoverFunction
import lang.temper.value.DeclTree
import lang.temper.value.Fail
import lang.temper.value.InstancePropertyRecord
import lang.temper.value.MacroEnvironment
import lang.temper.value.NamedBuiltinFun
import lang.temper.value.NotYet
import lang.temper.value.PartialResult
import lang.temper.value.SpecialFunction
import lang.temper.value.TClass
import lang.temper.value.TFunction
import lang.temper.value.TType
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.cherryPicker
import lang.temper.value.constructorSymbol
import lang.temper.value.imuSymbol

/**
 * <!-- snippet: builtin/new -->
 * # `new`
 * The `new` operator allows constructing new instances of types.
 */
object New : NamedBuiltinFun, SpecialFunction {
    override val callMayFailPerSe: Boolean get() = true
    override val name: String get() = "new"
    override val sigs: List<Signature2>? get() = null

    override fun invoke(
        macroEnv: MacroEnvironment,
        interpMode: InterpMode,
    ): PartialResult {
        val args = macroEnv.args
        if (args.size == 0) {
            macroEnv.explain(MessageTemplate.ArityMismatch, values = listOf(1))
            return Fail
        }
        if (macroEnv.stage < Stage.Type) {
            // I haven't seen it run properly before type stage, anyway, so this reduces compute.
            return NotYet
        }
        val typeResult = args.evaluate(0, interpMode)
        val reifiedType = when (typeResult) {
            is Fail, NotYet -> return typeResult
            is Value<*> ->
                TType.unpackOrNull(typeResult)
                    ?: run {
                        macroEnv.explain(
                            MessageTemplate.NotConstructible,
                            values = listOf(typeResult),
                        )
                        return@invoke Fail
                    }
        }
        val type = reifiedType.type
        val typeShape = (type as? NominalType)?.definition as? TypeShape
        if (typeShape == null) {
            macroEnv.explain(
                MessageTemplate.ExpectedValueOfType,
                values = listOf("NominalType", type),
            )
            return Fail
        }
        if (typeShape.abstractness != Abstractness.Concrete) {
            macroEnv.explain(MessageTemplate.NotConstructible, values = listOf(typeResult))
            return Fail
        }
        // TODO Also need to check for imu parameterizations of partialImu types.
        // TODO Maybe can factor some logic in ImuChecker.
        if (interpMode == InterpMode.Partial && !typeShape.isImu()) {
            return NotYet
        }

        // Group the constructors into an umbrella.
        val constructors = typeShape.methods.filter {
            it.symbol == constructorSymbol
        }
        val constructorValues = mutableListOf<CallableValue>()
        for (constructor in constructors) {
            val callableValue = TFunction.unpackOrNull(
                (constructor.enclosingType.name.origin as? BindingNamingContext)
                    ?.getTopLevelBinding(constructor.name)?.value
                    // If the value is being constructed in Core based on an
                    // exported type, look it up locally.
                    // For example, let doneResultSingleton = new DoneResult().
                    ?: macroEnv.environment[constructor.name, macroEnv] as? Value<*>,
            ) as? CallableValue
            if (callableValue != null) {
                constructorValues.add(callableValue)
            } else {
                macroEnv.explain(
                    MessageTemplate.MemberUnavailable,
                    values = listOf(constructor.name),
                )
            }
        }
        if (constructorValues.isEmpty()) {
            return macroEnv.fail(MessageTemplate.NotConstructible, values = listOf(type))
        }

        // Compile a list of arguments to the constructor, putting thisValue first
        val instancePropertyRecord = InstancePropertyRecord(mutableMapOf())
        val thisValue = Value(instancePropertyRecord, TClass(typeShape))
        val thisPos = macroEnv.callee.pos.rightEdge

        val variantArgs = args.cherryPicker.let { cherryPicker ->
            cherryPicker.new(null to ValueLeaf(macroEnv.document, thisPos, thisValue))
            cherryPicker.add(1 until args.size)
            cherryPicker.build()
        }
        val (chosenCtor, _) = CoverFunction.uncover(
            variantArgs,
            macroEnv,
            interpMode,
            constructorValues,
            null,
        ) ?: return Fail
        chosenCtor is Value<*> || return Fail

        val constructorArgTrees = macroEnv.treeFarm.growAll(macroEnv.pos) {
            V(thisPos, thisValue)
            for (i in 1 until args.size) {
                val keyTree = args.keyTree(i)
                if (keyTree != null) {
                    Replant(keyTree)
                }
                Replant(args.valueTree(i))
            }
        }

        // Invoke the constructor
        val constructorResult = macroEnv.dispatchCallTo(
            ValueLeaf(
                macroEnv.document,
                args.pos(0),
                chosenCtor,
            ),
            chosenCtor,
            constructorArgTrees,
            InterpMode.Full,
        )
        return when (constructorResult) {
            NotYet, is Fail -> constructorResult
            is Value<*> -> thisValue
        }
    }

    override fun toString(): String = "builtin New"
}

private fun TypeShape.isImu(): Boolean {
    return (stayLeaf?.incoming?.source as? DeclTree)?.parts?.let { imuSymbol in it.metadataSymbolMap } == true
}
