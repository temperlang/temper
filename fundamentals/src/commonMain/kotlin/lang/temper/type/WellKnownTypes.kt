package lang.temper.type

import lang.temper.common.AtomicCounter
import lang.temper.env.BindingNamingContext
import lang.temper.env.DeclarationBinding
import lang.temper.lexer.Genre
import lang.temper.log.Position
import lang.temper.name.BuiltinName
import lang.temper.name.ImplicitsCodeLocation
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.Symbol
import lang.temper.name.TemperName
import lang.temper.type.Abstractness.Abstract
import lang.temper.type.Abstractness.Concrete
import lang.temper.type2.DefinedNonNullType
import lang.temper.type2.DefinedType
import lang.temper.type2.MkType2
import lang.temper.value.TBoolean
import lang.temper.value.TClosureRecord
import lang.temper.value.TFloat64
import lang.temper.value.TFunction
import lang.temper.value.TInt
import lang.temper.value.TInt64
import lang.temper.value.TList
import lang.temper.value.TListBuilder
import lang.temper.value.TNull
import lang.temper.value.TProblem
import lang.temper.value.TStageRange
import lang.temper.value.TString
import lang.temper.value.TSymbol
import lang.temper.value.TType
import lang.temper.value.TVoid
import lang.temper.value.listedTypeBuiltinName
import kotlin.jvm.Synchronized

/**
 * We need to have reifications of well-known types.
 * We need the interpreter machinery to actually define the members, but it's convenient to define
 * builtins separately.
 *
 * These type shapes are filled in by the *Implicits* module.
 */
object WellKnownTypes {
    private val bySymbol: Map<Symbol, TypeShape>
    private val byName: Map<ResolvedName, TypeShape>

    fun withName(name: ResolvedName): TypeShape? {
        var shape = byName[name]
        if (shape == null && name is BuiltinName) {
            shape = bySymbol[name.toSymbol()]
        }
        return shape
    }

    fun isWellKnown(typeShape: TypeShape): Boolean {
        val word = typeShape.word
        return word != null && typeShape === bySymbol[word]
    }

    val allWellKnown: Iterable<TypeShape> get() = byName.values

    val anyValueTypeDefinition: TypeShape
    val booleanTypeDefinition: TypeShape
    val bubbleTypeDefinition: TypeShape
    val closureRecordTypeDefinition: TypeShape
    val denseBitVectorTypeDefinition: TypeShape
    val dequeTypeDefinition: TypeShape
    val doneResultTypeDefinition: TypeShape
    val emptyTypeDefinition: TypeShape
    val equatableTypeDefinition: TypeShape
    val float64TypeDefinition: TypeShape
    val functionTypeDefinition: TypeShape
    val generatorTypeDefinition: TypeShape
    val generatorFnTypeDefinition: TypeShape
    val generatorFnWrapperTypeDefinition: TypeShape
    val generatorResultTypeDefinition: TypeShape
    val intTypeDefinition: TypeShape
    val int64TypeDefinition: TypeShape
    val invalidTypeDefinition: TypeShape
    val listedTypeDefinition: TypeShape
    val listTypeDefinition: TypeShape
    val listBuilderTypeDefinition: TypeShape
    val mapTypeDefinition: TypeShape
    val mappedTypeDefinition: TypeShape
    val mapBuilderTypeDefinition: TypeShape
    val pairTypeDefinition: TypeShape
    val mapKeyTypeDefinition: TypeShape
    val neverTypeDefinition: TypeShape
    val noStringIndexTypeDefinition: TypeShape
    val nullTypeDefinition: TypeShape
    val problemTypeDefinition: TypeShape
    val promiseTypeDefinition: TypeShape
    val promiseBuilderTypeDefinition: TypeShape
    val valueResultTypeDefinition: TypeShape
    val resultTypeDefinition: TypeShape
    val safeGeneratorTypeDefinition: TypeShape
    val safeGeneratorFnWrapperTypeDefinition: TypeShape
    val stageRangeTypeDefinition: TypeShape
    val stringTypeDefinition: TypeShape
    val stringIndexTypeDefinition: TypeShape
    val stringIndexOptionTypeDefinition: TypeShape
    val symbolTypeDefinition: TypeShape
    val typeTypeDefinition: TypeShape
    val voidTypeDefinition: TypeShape

    val anyValueType: NominalType
    val anyValueOrNullType2: DefinedType
    val anyValueType2: DefinedNonNullType
    val booleanType: NominalType
    val booleanType2: DefinedNonNullType
    val bubbleType2: DefinedNonNullType
    val emptyType: NominalType
    val emptyType2: DefinedNonNullType
    val equatableType: NominalType
    val float64Type: NominalType
    val float64Type2: DefinedNonNullType
    val functionType: NominalType
    val intType: NominalType
    val intType2: DefinedNonNullType
    val int64Type: NominalType
    val int64Type2: DefinedNonNullType
    val invalidType2: DefinedNonNullType
    val mapKeyType: NominalType
    val mapKeyType2: DefinedNonNullType
    val promiseBuilderType: NominalType
    val stringType: NominalType
    val stringType2: DefinedNonNullType
    val stringIndexOptionType: NominalType
    val symbolType: NominalType
    val typeType: NominalType
    val typeType2: DefinedNonNullType
    val voidType: NominalType
    val voidType2: DefinedNonNullType

    init {
        val namingContext = object : BindingNamingContext(AtomicCounter()) {
            override val loc = ImplicitsCodeLocation

            override fun getTopLevelBinding(name: TemperName): DeclarationBinding? =
                getBindingFromImplicits(name)

            override val topLevelBindingNames: Iterable<TemperName>
                get() = getBindingNames()
        }
        val nameMaker = ResolvedNameMaker(namingContext, Genre.Library)
        val mutationCounter = AtomicCounter()

        var superType: NominalType? = null
        // This is a default position.  TypeDisambiguateMacro updates it
        // when fleshing out well-known type shapes.
        val implicitsPos = Position(ImplicitsCodeLocation, 0, 0)

        val byName = mutableMapOf<ResolvedName, TypeShape>()
        fun wellKnownTypeShape(
            name: ResolvedName,
            abstractness: Abstractness,
            anyValueSuper: Boolean = true,
        ): TypeShape {
            val symbol = name.toSymbol()!!
            val typeShape = TypeShapeImpl(
                implicitsPos,
                symbol,
                nameMaker,
                abstractness,
                mutationCounter,
            )
            if (anyValueSuper) {
                superType!!.let { superType ->
                    typeShape extends superType
                }
            }
            check(name !in byName)
            byName[name] = typeShape
            return typeShape
        }

        anyValueTypeDefinition =
            wellKnownTypeShape(BuiltinName(ANY_VALUE_TYPE_NAME_TEXT), Abstract, anyValueSuper = false)
        anyValueType = MkType.nominal(anyValueTypeDefinition)
        anyValueOrNullType2 = MkType2(anyValueTypeDefinition).canBeNull().get()
        anyValueType2 = MkType2(anyValueTypeDefinition).get() as DefinedNonNullType
        superType = anyValueType

        fun TypeShape.addTypeParameter(text: String = "T", variance: Variance = Variance.Invariant) {
            (this as MutableTypeShape).typeParameters.add(
                TypeParameterShape(
                    this,
                    TypeFormal(
                        implicitsPos,
                        nameMaker.unusedSourceName(ParsedName(text)),
                        Symbol(text),
                        variance,
                        mutationCounter,
                        listOf(MkType.nominal(anyValueTypeDefinition)),
                    ),
                    Symbol(text),
                    null,
                ),
            )
        }

        booleanTypeDefinition = wellKnownTypeShape(TBoolean.name, Concrete)
        bubbleTypeDefinition = wellKnownTypeShape(BuiltinName("Bubble"), Concrete)
        closureRecordTypeDefinition = wellKnownTypeShape(TClosureRecord.name, Abstract)
        denseBitVectorTypeDefinition = wellKnownTypeShape(BuiltinName("DenseBitVector"), Concrete)
        dequeTypeDefinition = wellKnownTypeShape(BuiltinName("Deque"), Concrete)
        dequeTypeDefinition.addTypeParameter()
        doneResultTypeDefinition = wellKnownTypeShape(BuiltinName("DoneResult"), Concrete)
        doneResultTypeDefinition.addTypeParameter("YIELD", variance = Variance.Covariant)
        emptyTypeDefinition = wellKnownTypeShape(BuiltinName("Empty"), Concrete)
        equatableTypeDefinition = wellKnownTypeShape(BuiltinName("Equatable"), Abstract)
        float64TypeDefinition = wellKnownTypeShape(TFloat64.name, Concrete)
        functionTypeDefinition = wellKnownTypeShape(TFunction.name, Abstract)
        generatorTypeDefinition = wellKnownTypeShape(BuiltinName("Generator"), Abstract)
        generatorTypeDefinition.addTypeParameter("YIELD", variance = Variance.Covariant)
        generatorFnTypeDefinition = wellKnownTypeShape(BuiltinName("GeneratorFn"), Abstract)
        generatorFnWrapperTypeDefinition = wellKnownTypeShape(BuiltinName("GeneratorFnWrapper"), Concrete)
        generatorFnWrapperTypeDefinition.addTypeParameter("YIELD", variance = Variance.Covariant)
        generatorResultTypeDefinition = wellKnownTypeShape(BuiltinName("GeneratorResult"), Abstract)
        generatorResultTypeDefinition.addTypeParameter("YIELD", variance = Variance.Covariant)
        intTypeDefinition = wellKnownTypeShape(TInt.name, Concrete)
        int64TypeDefinition = wellKnownTypeShape(TInt64.name, Concrete)
        invalidTypeDefinition = wellKnownTypeShape(BuiltinName("Invalid"), Abstract, anyValueSuper = false)
        listedTypeDefinition = wellKnownTypeShape(listedTypeBuiltinName, Abstract)
        listedTypeDefinition.addTypeParameter(variance = Variance.Invariant)
        listTypeDefinition = wellKnownTypeShape(TList.name, Concrete)
        listTypeDefinition.addTypeParameter(variance = Variance.Covariant)
        listBuilderTypeDefinition = wellKnownTypeShape(TListBuilder.name, Concrete)
        listBuilderTypeDefinition.addTypeParameter()
        mapTypeDefinition = wellKnownTypeShape(BuiltinName("Map"), Concrete)
        mapTypeDefinition.addTypeParameter(text = "K", variance = Variance.Contravariant)
        mapTypeDefinition.addTypeParameter(text = "V", variance = Variance.Covariant)
        mappedTypeDefinition = wellKnownTypeShape(BuiltinName("Mapped"), Abstract)
        mappedTypeDefinition.addTypeParameter(text = "K", variance = Variance.Contravariant)
        mappedTypeDefinition.addTypeParameter(text = "V", variance = Variance.Invariant)
        mapBuilderTypeDefinition = wellKnownTypeShape(BuiltinName("MapBuilder"), Concrete)
        mapBuilderTypeDefinition.addTypeParameter(text = "K")
        mapBuilderTypeDefinition.addTypeParameter(text = "V")
        neverTypeDefinition = wellKnownTypeShape(BuiltinName("Never"), Abstract)
        // Never has `out` variance because it is a sensible return type but not a sensible input type.
        //
        // Given an interface that uses a Never type to specialize a return type, it makes sense to
        // narrow that return type on yet another sub-interface.
        //
        //      interface Base {
        //        f(): Foo;
        //      }
        //
        //      interface FIsASubFoo extends Base {
        //        f(): SubFoo; // Narrows return type
        //      }
        //
        //      interface FNeverCompletes extends Base {
        //        f(): Never<Foo>;
        //      }
        //
        //      interface SubOfBoth extends FIsASubFoo & FNeverCompletes {
        //        // Valid overload bc the return type is a subtype of SubFoo and Never<Foo>.
        //        f(): Never<SubFoo>;
        //      }
        neverTypeDefinition.addTypeParameter(variance = Variance.Covariant)
        pairTypeDefinition = wellKnownTypeShape(BuiltinName("Pair"), Concrete)
        pairTypeDefinition.addTypeParameter(text = "K", variance = Variance.Covariant)
        pairTypeDefinition.addTypeParameter(text = "V", variance = Variance.Covariant)
        mapKeyTypeDefinition = wellKnownTypeShape(BuiltinName("MapKey"), Abstract)
        promiseTypeDefinition = wellKnownTypeShape(BuiltinName("Promise"), Concrete)
        promiseTypeDefinition.addTypeParameter(text = "R", variance = Variance.Covariant)
        promiseBuilderTypeDefinition = wellKnownTypeShape(BuiltinName("PromiseBuilder"), Concrete)
        promiseBuilderTypeDefinition.addTypeParameter(text = "R", variance = Variance.Invariant)
        valueResultTypeDefinition = wellKnownTypeShape(BuiltinName("ValueResult"), Concrete)
        valueResultTypeDefinition.addTypeParameter("YIELD", variance = Variance.Covariant)
        noStringIndexTypeDefinition = wellKnownTypeShape(BuiltinName("NoStringIndex"), Concrete)
        nullTypeDefinition = wellKnownTypeShape(TNull.name, Concrete)
        problemTypeDefinition = wellKnownTypeShape(TProblem.name, Concrete)
        resultTypeDefinition = wellKnownTypeShape(BuiltinName("Result"), Concrete, anyValueSuper = false)
        resultTypeDefinition.addTypeParameter("P")
        safeGeneratorTypeDefinition = wellKnownTypeShape(BuiltinName("SafeGenerator"), Abstract)
        safeGeneratorTypeDefinition.addTypeParameter("YIELD", variance = Variance.Covariant)
        safeGeneratorFnWrapperTypeDefinition = wellKnownTypeShape(BuiltinName("SafeGeneratorFnWrapper"), Concrete)
        safeGeneratorFnWrapperTypeDefinition.addTypeParameter("YIELD", variance = Variance.Covariant)
        stageRangeTypeDefinition = wellKnownTypeShape(TStageRange.name, Concrete)
        stringTypeDefinition = wellKnownTypeShape(TString.name, Concrete)
        stringIndexTypeDefinition = wellKnownTypeShape(BuiltinName("StringIndex"), Concrete)
        stringIndexOptionTypeDefinition = wellKnownTypeShape(BuiltinName("StringIndexOption"), Abstract)
        symbolTypeDefinition = wellKnownTypeShape(TSymbol.name, Concrete)
        typeTypeDefinition = wellKnownTypeShape(TType.name, Concrete)
        voidTypeDefinition = wellKnownTypeShape(TVoid.name, Concrete, anyValueSuper = false)
        // Adding entries here warrant adding an entry to TmpLTranslators
        // definition -> type tag map.

        this.byName = byName.toMap()
        this.bySymbol = byName.mapKeys { it.key.toSymbol()!! }

        booleanType = MkType.nominal(booleanTypeDefinition)
        booleanType2 = MkType2(booleanTypeDefinition).get() as DefinedNonNullType
        bubbleType2 = MkType2(bubbleTypeDefinition).get() as DefinedNonNullType
        emptyType = MkType.nominal(emptyTypeDefinition)
        emptyType2 = MkType2(emptyTypeDefinition).get() as DefinedNonNullType
        equatableType = MkType.nominal(equatableTypeDefinition)
        float64Type = MkType.nominal(float64TypeDefinition)
        float64Type2 = MkType2(float64TypeDefinition).get() as DefinedNonNullType
        functionType = MkType.nominal(functionTypeDefinition)
        intType = MkType.nominal(intTypeDefinition)
        intType2 = MkType2(intTypeDefinition).get() as DefinedNonNullType
        int64Type = MkType.nominal(int64TypeDefinition)
        int64Type2 = MkType2(int64TypeDefinition).get() as DefinedNonNullType
        invalidType2 = MkType2(invalidTypeDefinition).get() as DefinedNonNullType
        mapKeyType = MkType.nominal(mapKeyTypeDefinition)
        mapKeyType2 = MkType2(mapKeyTypeDefinition).get() as DefinedNonNullType
        promiseBuilderType = MkType.nominal(
            promiseBuilderTypeDefinition,
            listOf(MkType.nominal(promiseBuilderTypeDefinition.typeParameters.first().definition)),
        )
        stringType = MkType.nominal(stringTypeDefinition)
        stringType2 = MkType2(stringTypeDefinition).get() as DefinedNonNullType
        stringIndexOptionType = MkType.nominal(stringIndexOptionTypeDefinition)
        symbolType = MkType.nominal(symbolTypeDefinition)
        typeType = MkType.nominal(typeTypeDefinition)
        typeType2 = MkType2(typeTypeDefinition).get() as DefinedNonNullType
        voidType = MkType.nominal(voidTypeDefinition)
        voidType2 = MkType2(voidTypeDefinition).get() as DefinedNonNullType

        booleanTypeDefinition extends equatableType
        doneResultTypeDefinition extends MkType.nominal(
            generatorResultTypeDefinition,
            listOf(MkType.nominal(doneResultTypeDefinition.formals.first())),
        )
        emptyTypeDefinition extends equatableType
        float64TypeDefinition extends equatableType
        generatorFnTypeDefinition extends functionType
        generatorFnWrapperTypeDefinition.let {
            val tYield = MkType.nominal(it.formals[0])
            it extends MkType.nominal(generatorTypeDefinition, listOf(tYield))
        }
        intTypeDefinition extends mapKeyType
        listTypeDefinition extends MkType.nominal(
            listedTypeDefinition,
            listOf(MkType.nominal(listTypeDefinition.formals[0])),
        )
        listBuilderTypeDefinition extends MkType.nominal(
            listedTypeDefinition,
            listOf(MkType.nominal(listBuilderTypeDefinition.formals[0])),
        )
        mapTypeDefinition extends MkType.nominal(
            mappedTypeDefinition,
            mapTypeDefinition.typeParameters.map {
                MkType.nominal(it.definition)
            },
        )
        mapTypeDefinition.formals[0] extends mapKeyType
        mappedTypeDefinition.formals[0] extends mapKeyType
        mapBuilderTypeDefinition extends MkType.nominal(
            mappedTypeDefinition,
            mapBuilderTypeDefinition.typeParameters.map {
                MkType.nominal(it.definition)
            },
        )
        mapBuilderTypeDefinition.formals[0] extends mapKeyType
        mapKeyTypeDefinition extends equatableType
        noStringIndexTypeDefinition extends stringIndexOptionType
        nullTypeDefinition extends equatableType
        safeGeneratorTypeDefinition extends MkType.nominal(
            generatorTypeDefinition,
            listOf(MkType.nominal(safeGeneratorTypeDefinition.formals.first())),
        )
        safeGeneratorFnWrapperTypeDefinition.let {
            val tYield = MkType.nominal(it.formals[0])
            it extends MkType.nominal(safeGeneratorTypeDefinition, listOf(tYield))
        }
        stringTypeDefinition extends mapKeyType
        stringIndexTypeDefinition extends stringIndexOptionType
        stringIndexOptionTypeDefinition extends equatableType
        valueResultTypeDefinition extends MkType.nominal(
            generatorResultTypeDefinition,
            listOf(MkType.nominal(valueResultTypeDefinition.formals.first())),
        )
    }

    val allNames: Set<ResolvedName> get() = this.byName.keys
}

var implicitsBindings: Map<TemperName, DeclarationBinding> = emptyMap()
var implicitsBindingNames: Set<TemperName> = emptySet()

/** Called on initialization of *ImplicitsModule* to initialize support of well-known types */
@Synchronized
fun initializeBindingsFromImplicits(bindings: Map<TemperName, DeclarationBinding>) {
    implicitsBindings = bindings
    implicitsBindingNames = bindings.keys.toSet()
}

@Synchronized
private fun getBindingFromImplicits(name: TemperName): DeclarationBinding? = implicitsBindings[name]

@Synchronized
private fun getBindingNames() = implicitsBindingNames

private infix fun TypeShape.extends(superType: NominalType) {
    check(this is MutableTypeShape)
    val superTypes = this.superTypes
    if (superType !in superTypes) {
        superTypes.add(superType)
    }
}

private infix fun TypeFormal.extends(superType: NominalType) {
    check(this is MutableTypeFormal)
    val upperBounds = this.upperBounds
    if (superType !in upperBounds) {
        upperBounds.add(superType)
    }
}
