package lang.temper.kcodegen.outgrammar

import lang.temper.common.Log
import lang.temper.log.LeveledMessageTemplate

internal enum class OutputGrammarCodeGeneratorMessageTemplate(
    override val formatString: String,
) : LeveledMessageTemplate {
    IllegalPropertyName("`$BACKED_PROPERTY_NAME_PREFIX` not allowed in property names"),
    UnmergeableKotlinCode("Need `}` at same indentation as `companion object` or `init`"),
    SuperTypeCycle("Cycle in inheritance graph involving %s"),
    InconsistentPropertyType("Conflicting types for property %s.%s from %s"),
    InconsistentPropertyCount("Conflicting counts for property %s.%s from %s"),
    MissingPropertyType("Missing type for property %s.%s"),
    InconsistentGetter("Conflicting getters for property %s.%s from %s"),
    InconsistentDefault("Conflicting default expression for property %s.%s from %s"),
    InconsistentDerivation("Conflicting derived from relationship: %s already derives from %s at %s"),
    InconsistentOperatorDefinition("Conflicting operator definition for %s from %s"),
    InconsistentRenderTo("Conflicting renderTo definition for %s from %s"),
    DataTypeCannotMixWithAstType("Data nodes cannot extend AST nodes or vice versa: %s extends %s"),
    MayNotContainProperty("Data nodes may not have AST node properties: %s has kind %s"),
    CannotAutoDerive("Cannot derive %s from %s for %s because name is already used at %s"),
    BackticksAroundNodeTypeName("Type for %s declared with backticks but is a node type name: %s"),
    AlreadyDeclared("%s was already declared at %s"),
    ImpliedNotDeclared("Node type %s is implied but never declared"),
    MissingPropertyInfo("Missing info needed to generate code for property %s.%s"),
    ;

    override val suggestedLevel: Log.Level
        get() = Log.Fatal
}
