package lang.temper.builtin

import lang.temper.common.Log
import lang.temper.common.subListToEnd
import lang.temper.env.InterpMode
import lang.temper.log.LogEntry
import lang.temper.log.MessageTemplate
import lang.temper.name.ParsedName
import lang.temper.value.BlockTree
import lang.temper.value.CallTree
import lang.temper.value.Fail
import lang.temper.value.FunTree
import lang.temper.value.InternalFeatureKeys
import lang.temper.value.MacroEnvironment
import lang.temper.value.NameLeaf
import lang.temper.value.PartialResult
import lang.temper.value.RightNameLeaf
import lang.temper.value.TInt
import lang.temper.value.TProblem
import lang.temper.value.TString
import lang.temper.value.Tree
import lang.temper.value.Value
import lang.temper.value.ValueLeaf
import lang.temper.value.atBuiltinName
import lang.temper.value.classBuiltinName
import lang.temper.value.constructorPropertySymbol
import lang.temper.value.errorFn
import lang.temper.value.freeTree
import lang.temper.value.functionContained
import lang.temper.value.newBuiltinName
import lang.temper.value.staticParsedName
import lang.temper.value.vEnumMemberSymbol
import lang.temper.value.vEnumTypeSymbol
import lang.temper.value.vInitSymbol
import lang.temper.value.vPublicSymbol
import lang.temper.value.vTypeSymbol
import lang.temper.value.vVisibilitySymbol
import lang.temper.value.void
import lang.temper.value.wordSymbol

/**
 * <!-- snippet: builtin/enum : `enum` type definition -->
 * # `enum`
 * TODO(issue #859): Fix translation of enum definitions before documenting.
 *
 * `enum` declarations are like class declarations
 *
 * ```temper inert
 * enum C { A, B, C; rest }
 * ```
 *
 * desugars to something like
 *
 * ```temper inert
 * class C(public let ordinal: Int, public name: String) {
 *   public static @enumMember let A = new C(0, "A");
 *   public static @enumMember let B = new C(1, "B");
 *   public static @enumMember let C = new C(2, "C");
 *
 *   private constructor(ordinal: Int, name: String) {
 *     this.ordinal = ordinal;
 *     this.name = name;
 *   }
 *
 *   rest
 * }
 * ```
 *
 * Backends should make a best effort to translate classes with
 * enum members to their language's idiomatic equivalent.
 *
 * TODO: Document how Temper enum types relate to types per backend.
 */
internal object DesugarEnumMacro : BuiltinMacro(
    "enum",
    signature = null,
    nameIsKeyword = true,
) {
    override fun invoke(macroEnv: MacroEnvironment, interpMode: InterpMode): PartialResult {
        val args = macroEnv.args

        val bodyFn = args.rawTreeList.lastOrNull() as? FunTree
            ?: return macroEnv.fail(MessageTemplate.MalformedType)
        val enumTypeName = run {
            var word: NameLeaf? = null
            for (i in args.indices) {
                if (args.key(i) == wordSymbol) {
                    word = args.valueTree(i) as? NameLeaf
                    break
                }
            }
            word ?: return@invoke macroEnv.fail(MessageTemplate.MissingName)
        }
        val bodyEdge = bodyFn.edge(bodyFn.size - 1)
        if (bodyEdge.target !is BlockTree) {
            val notBlock = bodyEdge.target
            bodyEdge.replace {
                Block(notBlock.pos) {
                    Replant(freeTree(notBlock))
                }
            }
        }
        val body = bodyEdge.target as BlockTree

        // Look for a comma operation as the first statement in the body.
        // We want to convert
        //     enum C { A, B, C; rest }
        // to
        //     class C(public let ordinal: Int, public name: String) {
        //         public static @enumMember let A = new C(0, "A");
        //         public static @enumMember let B = new C(1, "B");
        //         public static @enumMember let C = new C(2, "C");
        //         rest
        //     }
        // TODO: maybe a super-type for enums
        val commaListEdge = body.edgeOrNull(0)
        val commaList = commaListEdge?.target as? CallTree
        val commaListCallee = commaList?.childOrNull(0)
        if (commaListCallee?.functionContained != BuiltinFuns.commaFn) {
            return macroEnv.fail(MessageTemplate.MalformedType)
        }
        val memberTrees = commaList.children.subListToEnd(1)

        val members = mutableListOf<Tree>()
        memberTrees.forEachIndexed { memberIndex, memberTree ->
            if (memberTree is RightNameLeaf) {
                val memberPos = memberTree.pos
                val leftEdge = memberPos.leftEdge
                val rightEdge = memberPos.rightEdge
                val memberName = memberTree.content.toSymbol()?.text
                members.add(
                    memberTree.treeFarm.grow(memberTree.pos) {
                        Call(rightEdge) {
                            Rn(leftEdge, atBuiltinName)
                            Rn(leftEdge, staticParsedName)
                            Decl(memberPos) {
                                Ln(memberPos, memberTree.content)
                                V(leftEdge, vVisibilitySymbol)
                                V(leftEdge, vPublicSymbol)
                                V(leftEdge, vEnumMemberSymbol)
                                V(leftEdge, void)
                                V(rightEdge, vInitSymbol)
                                Call(rightEdge) {
                                    Rn(rightEdge, newBuiltinName)
                                    Rn(rightEdge, enumTypeName.content)
                                    V(rightEdge, Value(memberIndex, TInt))
                                    if (memberName != null) {
                                        V(rightEdge, Value(memberName, TString))
                                    } else {
                                        Call(rightEdge, errorFn) {
                                            V(
                                                Value(
                                                    LogEntry(
                                                        Log.Error,
                                                        MessageTemplate.MissingName,
                                                        memberPos,
                                                        emptyList(),
                                                    ),
                                                    TProblem,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            } else {
                // TODO(mikesamuel): handle other forms:
                // - TODO(mikesamuel) `A(ordinal = 123)` overrides an ordinal
                // - TODO(mikesamuel) `A(args)` specifies extra constructor arguments.
                // https://temperlang.slack.com/archives/C01NN6EF1RV/p1648137581710739 captures
                // some discussion.
                return@invoke macroEnv.fail(MessageTemplate.MalformedType, memberTree.pos)
            }
        }
        val commaListEdgeIndex = commaListEdge.edgeIndex
        body.replace(commaListEdgeIndex..commaListEdgeIndex) {
            Decl(body.pos.leftEdge) {
                Ln(ParsedName("ordinal"))
                V(constructorPropertySymbol)
                V(void)
                V(vVisibilitySymbol)
                V(vPublicSymbol)
                V(vTypeSymbol)
                V(Types.vInt)
            }
            Decl(body.pos.leftEdge) {
                Ln(ParsedName("name"))
                V(constructorPropertySymbol)
                V(void)
                V(vVisibilitySymbol)
                V(vPublicSymbol)
                V(vTypeSymbol)
                V(Types.vString)
            }
            for (member in members) {
                Replant(member)
            }
        }

        val rawTreeList = args.rawTreeList
        val classMacro = macroEnv.getFeatureImplementation(InternalFeatureKeys.Class.featureKey)
        when (classMacro) {
            is Fail -> return classMacro
            is Value<*> -> Unit
        }

        val calleePos = macroEnv.callee.pos

        // Mark the type definition as an enum type
        val bodyIndex = bodyEdge.edgeIndex
        bodyFn.replace(bodyIndex until bodyIndex) {
            val leftPos = calleePos.leftEdge // left of word `enum`
            V(leftPos, vEnumTypeSymbol)
            V(leftPos, void)
        }

        // Convert the call to a call to the `class` macro
        val classMacroValue = ValueLeaf(macroEnv.document, calleePos, classMacro)
        macroEnv.callee.incoming?.replace {
            Rn(calleePos, classBuiltinName)
        }

        // Process as a class
        return macroEnv.dispatchCallTo(
            calleeTree = classMacroValue,
            classMacro,
            rawTreeList,
            interpMode,
        )
    }
}
