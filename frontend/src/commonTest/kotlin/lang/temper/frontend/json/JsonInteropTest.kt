@file:Suppress("unused", "MaxLineLength")

package lang.temper.frontend.json

import lang.temper.common.ListBackedLogSink
import lang.temper.common.TestDocumentContext
import lang.temper.common.assertStructure
import lang.temper.common.console
import lang.temper.common.putMultiList
import lang.temper.common.stripDoubleHashCommentLinesToPutCommentsInlineBelow
import lang.temper.common.testCodeLocation
import lang.temper.common.testModuleName
import lang.temper.frontend.ModuleSource
import lang.temper.frontend.staging.ModuleAdvancer
import lang.temper.lexer.StandaloneLanguageConfig
import lang.temper.log.unknownPos
import lang.temper.name.ModuleName
import lang.temper.name.ParsedName
import lang.temper.name.ResolvedName
import lang.temper.name.ResolvedNameMaker
import lang.temper.name.SourceName
import lang.temper.name.Symbol
import lang.temper.type.Abstractness
import lang.temper.type.NominalType
import lang.temper.type.StaticType
import lang.temper.type.TypeShape
import lang.temper.type.TypeTestHarness
import lang.temper.value.Document
import lang.temper.value.DocumentContext
import lang.temper.value.ReifiedType
import lang.temper.value.TInt
import lang.temper.value.TString
import lang.temper.value.TType
import lang.temper.value.Value
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class JsonInteropTest {
    @Test
    fun noClasses() = assertJsonChanges(
        "{}", "",
    ) {}

    @Test
    fun oneCustomType() = assertJsonChanges(
        want = """
            |{
            |  methods: {
            |    MyType: {
            |      jsonAdapter: {
            |        static: true,
            |        body: ```
            |          fn: (JsonAdapter<MyType>) {
            |            new MyTypeJsonAdapter()
            |          }
            |          ```,
            |      },
            |    },
            |  },
            |  adapters: {
            |    MyTypeJsonAdapter: {
            |      extends: ["JsonAdapter<MyType>"],
            |      methods: {
            |        encodeToJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (x: MyType, p: JsonProducer): Void {
            |              x.encodeToJson(p)
            |            }
            |            ```
            |        },
            |        decodeFromJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (t: JsonSyntaxTree, ic: InterchangeContext): (MyType | Bubble) {
            |              type (MyType).decodeFromJson(t, ic)
            |            }
            |            ```
            |        },
            |      },
            |    },
            |  }
            |}
        """.trimMargin(),
        typeDecls = """
            |class MyType;
        """.trimMargin(),
    ) {
        type("MyType") {
            json()
            toJson()
            fromJson()
        }
    }

    @Test
    fun oneIntPoint() = assertJsonChanges(
        want = """
            |{
            |  methods: {
            |    Point: {
            |      encodeToJson: {
            |        visibility: "public",
            |        static: false,
            |        body: ```
            |          fn (p: JsonProducer): Void {
            |            p.startObject();
            |            p.objectKey("x");
            |            p.int32Value(getp(x, this(Point)));
            |            p.objectKey("y");
            |            p.int32Value(getp(y, this(Point)));
            |            p.endObject();
            |          }
            |          ```,
            |      },
            |      decodeFromJson: {
            |        visibility: "public",
            |        static: true,
            |        body: ```
            |          fn (t: JsonSyntaxTree, ic: InterchangeContext): (Point | Bubble) {
            |            let obj = as(t, JsonObject), x: Int32, y: Int32;
            |            x = as(obj.propertyValueOrBubble("x"), JsonNumeric).asInt32();
            |            y = as(obj.propertyValueOrBubble("y"), JsonNumeric).asInt32();
            |            new Point(\x, x, \y, y)
            |          }
            |          ```,
            |      },
            |      jsonAdapter: {
            |        static: true,
            |        body: ```
            |          fn: (JsonAdapter<Point>) {
            |            new PointJsonAdapter()
            |          }
            |          ```,
            |      },
            |    }
            |  },
            |  adapters: {
            |    PointJsonAdapter: {
            |      extends: ["JsonAdapter<Point>"],
            |      methods: {
            |        encodeToJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (x: Point, p: JsonProducer): Void {
            |              x.encodeToJson(p)
            |            }
            |            ```
            |        },
            |        decodeFromJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (t: JsonSyntaxTree, ic: InterchangeContext): (Point | Bubble) {
            |              type (Point).decodeFromJson(t, ic)
            |            }
            |            ```
            |        },
            |      },
            |    },
            |  }
            |}
        """.trimMargin(),
        typeDecls = """
            |class Point;
        """.trimMargin(),
    ) {
        type("Point") {
            json()
            property("x", "Int")
            property("y", "Int")
        }
    }

    @Test
    fun classWithOneGenericParameter() = assertJsonChanges(
        want = """
            |{
            |  methods: {
            |    Box: {
            |      encodeToJson: {
            |        visibility: "public",
            |        static: false,
            |        body: ```
            |          fn (p: JsonProducer, adapterForT: JsonAdapter<T>): Void {
            |            p.startObject();
            |            p.objectKey("content");
            |            adapterForT.encodeToJson(getp(content, this(Box<T>)), p);
            |            p.endObject();
            |          }
            |          ```,
            |      },
            |      decodeFromJson: {
            |        visibility: "public",
            |        static: true,
            |        body: ```
            |          fn<T>(t: JsonSyntaxTree, ic: InterchangeContext, adapterForT: JsonAdapter<T>): (Box<T> | Bubble) {
            |            let obj = as(t, JsonObject), content: T;
            |            content = adapterForT.decodeFromJson(obj.propertyValueOrBubble("content"), ic);
            |            new Box<T>(\content, content)
            |          }
            |          ```,
            |      },
            |      jsonAdapter: {
            |        static: true,
            |        body: ```
            |          fn<T>(adapterForT: JsonAdapter<T>): (JsonAdapter<Box<T>>) {
            |            new BoxJsonAdapter<T>(adapterForT)
            |          }
            |          ```,
            |      },
            |    }
            |  },
            |  adapters: {
            |    BoxJsonAdapter: {
            |      typeFormals: ["T"],
            |      extends: ["JsonAdapter<Box<T>>"],
            |      properties: {
            |        adapterForT: {
            |          visibility: "private",
            |          static: false,
            |          type: [
            |            "Nominal",
            |            "std//json/.JsonAdapter",
            |            "T__7"
            |          ]
            |        },
            |      },
            |      methods: {
            |        encodeToJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (x: Box<T>, p: JsonProducer): Void {
            |              x.encodeToJson(p, adapterForT)
            |            }
            |            ```
            |        },
            |        decodeFromJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (t: JsonSyntaxTree, ic: InterchangeContext): (Box<T> | Bubble) {
            |              type (Box).decodeFromJson(t, ic, adapterForT)
            |            }
            |            ```
            |        },
            |      },
            |    },
            |  }
            |}
        """.trimMargin(),
        typeDecls = """
            |class Box<T>;
        """.trimMargin(),
    ) {
        type("Box") {
            json()
            property("content", "T")
        }
    }

    @Test
    fun propertyWithListTypeDelegatesToListAdapter() = assertJsonChanges(
        want = """
            |{
            |  methods: {
            |    Strings: {
            |      encodeToJson: {
            |        visibility: "public",
            |        static: false,
            |        body: ```
            |          fn (p: JsonProducer): Void {
            |            p.startObject();
            |            p.objectKey("strings");
            |## Here we pass String.jsonAdapter() to List.jsonAdapter()
            |            type (List).jsonAdapter(type (String).jsonAdapter()).encodeToJson(getp(strings, this(Strings)), p);
            |            p.endObject();
            |          }
            |          ```,
            |      },
            |      decodeFromJson: {
            |        visibility: "public",
            |        static: true,
            |        body: ```
            |          fn (t: JsonSyntaxTree, ic: InterchangeContext): (Strings | Bubble) {
            |            let obj = as(t, JsonObject), strings: List<String>;
            |            strings = type (List).jsonAdapter(type (String).jsonAdapter()).decodeFromJson(obj.propertyValueOrBubble("strings"), ic);
            |            new Strings(\strings, strings)
            |          }
            |          ```,
            |      },
            |      jsonAdapter: {
            |        static: true,
            |        body: ```
            |          fn: (JsonAdapter<Strings>) {
            |            new StringsJsonAdapter()
            |          }
            |          ```,
            |      },
            |    }
            |  },
            |  adapters: {
            |    StringsJsonAdapter: {
            |      extends: ["JsonAdapter<Strings>"],
            |      methods: {
            |        encodeToJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (x: Strings, p: JsonProducer): Void {
            |              x.encodeToJson(p)
            |            }
            |            ```
            |        },
            |        decodeFromJson: {
            |          visibility: "public",
            |          static: false,
            |          body: ```
            |            fn (t: JsonSyntaxTree, ic: InterchangeContext): (Strings | Bubble) {
            |              type (Strings).decodeFromJson(t, ic)
            |            }
            |            ```
            |        },
            |      },
            |    },
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
        typeDecls = """
            |class Strings;
        """.trimMargin(),
    ) {
        type("Strings") {
            json()
            property("strings", "List<String>")
        }
    }

    @Test
    fun simpleSealedType() = assertJsonChanges(
        typeDecls = """
            |/*sealed*/ interface S;
            |
            |class A extends S;
            |/*sealed*/ interface T extends S;
            |class B extends T;
            |class C extends T;
        """.trimMargin(),
        want = """
            |{
            |  methods: {
            |    S: {
            |      encodeToJson: {
            |        visibility: "public",
            |        static: false,
            |        body:
            |        ```
            |        fn (p: JsonProducer): Void {
            |          let x = this();
            |## The sealed type encoder just switches and delegates to the
            |## JSON adapter for the sub-type.
            |          when (x, fn {
            |              \case_is;
            |              A;
            |              A.jsonAdapter().encodeToJson(x, p);
            |## B and C are not *direct* sub-types of S but are indirect
            |## sub-types via sealed interface T which extends S.
            |## We flatten T into S, for the purposes of encoders and
            |## decoders so that we can build the decode logic below
            |## entirely based on cheap structural checks.
            |              \case_is;
            |              B;
            |              B.jsonAdapter().encodeToJson(x, p);
            |              \case_is;
            |              C;
            |              C.jsonAdapter().encodeToJson(x, p);
            |              \default;
            |              panic()
            |          })
            |        }
            |        ```
            |      },
            |      decodeFromJson: {
            |        visibility: "public",
            |        static: true,
            |        body:
            |        ```
            |        fn (t: JsonSyntaxTree, ic: InterchangeContext): (S | Bubble) {
            |          let obj = as(t, JsonObject), valueForX = obj.propertyValueOrNull("x");
            |          do {
            |## Here's the decision tree.
            |            if(!isNull(valueForX), fn {
            |                let valueForY = obj.propertyValueOrNull("y");
            |                do {
            |                  if(!isNull(valueForY), fn {
            |## If there's an "x" and a "y", then it's an A.
            |                      return A.jsonAdapter().decodeFromJson(obj, ic)
            |                  });
            |                  if(isNull(valueForY), fn {
            |## Each B has an "x" and a "z", but has "x" and does not have "y" is sufficient.
            |                      return B.jsonAdapter().decodeFromJson(obj, ic)
            |                  })
            |                }
            |            });
            |## Each C has a "y" and a "z" but it's sufficient to check absence of "x."
            |            if(isNull(valueForX), fn {
            |                return C.jsonAdapter().decodeFromJson(obj, ic)
            |            })
            |          };
            |          bubble()
            |        }
            |        ```
            |      },
            |      jsonAdapter: {
            |        visibility: "public",
            |        static: true,
            |        body:
            |        ```
            |        fn: (JsonAdapter<S>) {
            |          new SJsonAdapter()
            |        }
            |        ```
            |      }
            |    },
            |## Classes A, B, and C are simple POJOs; the generated methods' details
            |## are not relevant to sealed interface encoding or decoding.
            |    A: "__DO_NOT_CARE__",
            |    B: "__DO_NOT_CARE__",
            |    C: "__DO_NOT_CARE__",
            |  },
            |  adapters: {
            |    AJsonAdapter: "__DO_NOT_CARE__",
            |    BJsonAdapter: "__DO_NOT_CARE__",
            |    CJsonAdapter: "__DO_NOT_CARE__",
            |    SJsonAdapter: {
            |      typeFormals: [],
            |      extends: [
            |        "JsonAdapter<S>"
            |      ],
            |      properties: {},
            |      methods: {
            |        encodeToJson: {
            |          visibility: "public",
            |          static: false,
            |          body:
            |          ```
            |          fn (x: S, p: JsonProducer): Void {
            |            x.encodeToJson(p)
            |          }
            |          ```
            |        },
            |        decodeFromJson: {
            |          visibility: "public",
            |          static: false,
            |          body:
            |          ```
            |          fn (t: JsonSyntaxTree, ic: InterchangeContext): (S | Bubble) {
            |            type (S).decodeFromJson(t, ic)
            |          }
            |          ```
            |        }
            |      }
            |    },
            |  }
            |}
            |
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        type("S") {
            sealed()
            json()
        }
        type("T") {
            sealed()
        }
        type("A") {
            json()
            property("x", "Int")
            property("y", "Int")
        }
        type("B") {
            json()
            property("x", "Int")
            property("z", "Int")
        }
        type("C") {
            json()
            property("y", "Int")
            property("z", "Int")
        }
    }

    @Test
    fun sealedTypeWithExtraPropertyToDisambiguate() = assertJsonChanges(
        typeDecls = """
            |/*sealed*/interface SI;
            |// These types have the same backed properties
            |class A extends SI;
            |class B extends SI;
        """.trimMargin(),
        want = """
            |{
            |  methods: {
            |    SI: {
            |      encodeToJson: {
            |        name: "encodeToJson",
            |        visibility: "public",
            |        static: false,
            |        body:
            |        ```
            |        fn (p: JsonProducer): Void {
            |          let x = this();
            |          when (x, fn {
            |              \case_is;
            |              A;
            |              A.jsonAdapter().encodeToJson(x, p);
            |              \case_is;
            |              B;
            |              B.jsonAdapter().encodeToJson(x, p);
            |              \default;
            |              panic()
            |          })
            |        }
            |        ```
            |      },
            |      decodeFromJson: {
            |        name: "decodeFromJson",
            |        visibility: "public",
            |        static: true,
            |        body:
            |        ```
            |        fn (t: JsonSyntaxTree, ic: InterchangeContext): (SI | Bubble) {
            |## Instances of class A cannot be distinguished from instances of
            |## class B based on their properties, so instead the decoding switches
            |## on the extraProperty("class") because it is known to have distinct
            |          let obj = as(t, JsonObject), valueForClass = obj.propertyValueOrNull("class");
            |          when (valueForClass, fn {
            |              \case_is;
            |              type (JsonString);
            |              when (valueForClass.content, fn {
            |                  \case;
            |## If the class property has a JsonString value, we then match its content
            |## against "A" here and "B" in the case below.
            |                  "A";
            |                  return A.jsonAdapter().decodeFromJson(obj, ic);
            |                  \case;
            |                  "B";
            |                  return B.jsonAdapter().decodeFromJson(obj, ic)
            |              });
            |              \default;
            |              do {}
            |          });
            |          bubble()
            |        }
            |        ```
            |      },
            |      jsonAdapter: "__DO_NOT_CARE__",
            |    },
            |    A: {
            |      encodeToJson: {
            |        name: "encodeToJson",
            |        visibility: "public",
            |        static: false,
            |        body:
            |        ```
            |        fn (p: JsonProducer): Void {
            |          p.startObject();
            |## Here, the encoder for the class with the extra property
            |## includes the known value so that decoding from the sealed
            |## super-type works.
            |          p.objectKey("class");
            |          p.stringValue("A");
            |          p.objectKey("i");
            |          p.int32Value(getp(i, this(A)));
            |          p.endObject();
            |        }
            |        ```
            |      },
            |      decodeFromJson: {
            |        name: "decodeFromJson",
            |        visibility: "public",
            |        static: true,
            |        body:
            |        ```
            |        fn (t: JsonSyntaxTree, ic: InterchangeContext): (A | Bubble) {
            |## We don't actually care about the known property when decoding.
            |## We could though.
            |          let obj = as(t, JsonObject), i: Int32;
            |          i = as(obj.propertyValueOrBubble("i"), JsonNumeric).asInt32();
            |          new A(\i, i)
            |        }
            |        ```
            |      },
            |      jsonAdapter: "__DO_NOT_CARE__",
            |    },
            |    B: {
            |      encodeToJson: {
            |        visibility: "public",
            |        static: false,
            |        body:
            |        ```
            |        fn (p: JsonProducer): Void {
            |          p.startObject();
            |          p.objectKey("class");
            |          p.stringValue("B");
            |          p.objectKey("i");
            |          p.int32Value(getp(i, this(B)));
            |          p.endObject();
            |        }
            |        ```
            |      },
            |      decodeFromJson: {
            |        visibility: "public",
            |        static: true,
            |        body:
            |        ```
            |        fn (t: JsonSyntaxTree, ic: InterchangeContext): (B | Bubble) {
            |          let obj = as(t, JsonObject), i: Int32;
            |          i = as(obj.propertyValueOrBubble("i"), JsonNumeric).asInt32();
            |          new B(\i, i)
            |        }
            |        ```
            |      },
            |      jsonAdapter: "__DO_NOT_CARE__",
            |    }
            |  },
            |  adapters: {
            |    SIJsonAdapter: "__DO_NOT_CARE__",
            |    AJsonAdapter: "__DO_NOT_CARE__",
            |    BJsonAdapter: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin().stripDoubleHashCommentLinesToPutCommentsInlineBelow(),
    ) {
        type("SI") {
            json()
            sealed()
        }
        type("A") {
            json()
            extraProperty("class", "A")
            property("i", "Int")
        }
        type("B") {
            json()
            extraProperty("class", "B")
            property("i", "Int")
        }
    }

    @Test
    fun classWithNullableFields() = assertJsonChanges(
        typeDecls = "class C;",
        want = """
            |{
            |  methods: {
            |    C: {
            |      encodeToJson: {
            |        visibility: "public",
            |        static: false,
            |        body:
            |        ```
            |        fn (p: JsonProducer): Void {
            |          p.startObject();
            |          p.objectKey("i");
            |          new OrNullJsonAdapter<Int32>(type (Int32).jsonAdapter()).encodeToJson(getp(i, this(C)), p);
            |          p.objectKey("c");
            |          new OrNullJsonAdapter<C>(type (C).jsonAdapter()).encodeToJson(getp(c, this(C)), p);
            |          p.endObject();
            |        }
            |        ```
            |      },
            |      decodeFromJson: {
            |        visibility: "public",
            |        static: true,
            |        body:
            |        ```
            |        fn (t: JsonSyntaxTree, ic: InterchangeContext): (C | Bubble) {
            |          let obj = as(t, JsonObject), i: Int32?, c: C?;
            |          i = new OrNullJsonAdapter<Int32>(type (Int32).jsonAdapter()).decodeFromJson(obj.propertyValueOrBubble("i"), ic);
            |          c = new OrNullJsonAdapter<C>(type (C).jsonAdapter()).decodeFromJson(obj.propertyValueOrBubble("c"), ic);
            |          new C(\i, i, \c, c)
            |        }
            |        ```
            |      },
            |      jsonAdapter: "__DO_NOT_CARE__",
            |    }
            |  },
            |  adapters: {
            |    CJsonAdapter: "__DO_NOT_CARE__",
            |  }
            |}
        """.trimMargin(),
    ) {
        type("C") {
            json()
            property("i", "Int?")
            property("c", "C?")
        }
    }

    private fun assertJsonChanges(
        want: String,
        typeDecls: String,
        buildInput: JsonInteropDetailsBuilder.() -> Unit,
    ) {
        val context = TestDocumentContext()
        val document = Document(context)

        val b = JsonInteropDetailsBuilder(context, typeDecls)
        b.buildInput()
        val details = b.toJsonInteropDetails()

        val logSink = ListBackedLogSink()

        val pass = JsonInteropPass(document, logSink, details)
        val changes = pass.computeChanges()

        assertStructure(want, logSink.wrapErrorsAround(changes))
    }
}

private val stdJsonForTest = lazy {
    val logSink = ListBackedLogSink()
    val advancer = ModuleAdvancer(logSink)
    val stdJsonImporter = advancer.createModule(testModuleName, console)
    stdJsonImporter.deliverContent(
        ModuleSource(
            filePath = testCodeLocation,
            fetchedContent = """
                |let { JsonProducer, JsonSyntaxTree, InterchangeContext, JsonObject, JsonAdapter } =
                |  import("std/json");
            """.trimMargin(),
            languageConfig = StandaloneLanguageConfig,
        ),
    )
    advancer.advanceModules()
    val stdJsonModule = advancer.getAllModules().first {
        val loc = it.loc
        (
            loc is ModuleName &&
                loc.libraryRootSegmentCount == 1 &&
                loc.sourceFile.segments.size == 2
            ) && run {
            val (root, file) = loc.sourceFile.segments
            val fileBase = file.baseName
            root.fullName == "std" && fileBase == "json"
        }
    }
    val exports = stdJsonModule.exports!!
    fun typeShapeNamed(exportName: String): TypeShape {
        val export = exports.first { it.name.baseName.nameText == exportName }
        val exportedType = (TType.unpack(export.value!!) as ReifiedType).type
        return (exportedType as NominalType).definition as TypeShape
    }
    JsonInteropDetails.StdJson(
        typeInterchangeContext = typeShapeNamed("InterchangeContext"),
        typeJsonAdapter = typeShapeNamed("JsonAdapter"),
        typeJsonProducer = typeShapeNamed("JsonProducer"),
        typeJsonSyntaxTree = typeShapeNamed("JsonSyntaxTree"),
        typeJsonObject = typeShapeNamed("JsonObject"),
        typeJsonBoolean = typeShapeNamed("JsonBoolean"),
        typeJsonFloat64 = typeShapeNamed("JsonFloat64"),
        typeJsonInt = typeShapeNamed("JsonInt"),
        typeJsonNull = typeShapeNamed("JsonNull"),
        typeJsonNumeric = typeShapeNamed("JsonNumeric"),
        typeJsonString = typeShapeNamed("JsonString"),
        typeOrNullJsonAdapter = typeShapeNamed("OrNullJsonAdapter"),
    )
}

private class JsonInteropDetailsBuilder(
    docContext: DocumentContext,
    typeDecls: String,
) {
    private val nameMaker = ResolvedNameMaker(docContext.namingContext, docContext.genre)
    private val typeHarness = TypeTestHarness(typeDecls)
    private val types = mutableMapOf<String, TypeBuilder>()

    private fun unusedSourceName(base: String) = nameMaker.unusedSourceName(ParsedName(base))

    fun type(
        name: String,
        body: TypeBuilder.() -> Unit,
    ): ResolvedName {
        val typeShape = typeHarness.getDefinition(name) as? TypeShape
            ?: fail("$name is not the name of a declared type")
        val builder = TypeBuilder(typeShape)
        builder.body()
        assertTrue(name !in types)
        types[name] = builder
        return typeShape.name
    }

    fun toJsonInteropDetails(): JsonInteropDetails {
        // Find any sealed type relationships
        val sealed = mutableMapOf<TypeBuilder, MutableList<JsonInteropDetails.SealedSubType>>()
        val typesByDefinition = types.values.associateBy { it.typeShape }
        for (typeBuilder in types.values) {
            val typeShape = typeBuilder.typeShape
            for (superType in typeShape.superTypes) {
                val superTypeBuilder = typesByDefinition[superType.definition]
                if (superTypeBuilder?.isSealed == true) {
                    sealed.putMultiList(
                        superTypeBuilder,
                        JsonInteropDetails.SealedSubType(
                            typeShape.name,
                            superType.bindings.map { it as StaticType },
                        ),
                    )
                }
            }
        }

        return JsonInteropDetails(
            localTypes = types.values
                .map {
                    it.toTypeDecl(sealed[it])
                }
                .associateBy { it.name },
            stdJson = stdJsonForTest.value,
        )
    }

    inner class TypeBuilder(val typeShape: TypeShape) {
        private var hasJsonDecoration = false
        var isSealed = false
            private set
        private val properties = mutableListOf<JsonInteropDetails.PropertyDecl>()
        private var toJsonMethod = JsonInteropDetails.MethodPresence.Absent
        private var fromJsonMethod = JsonInteropDetails.MethodPresence.Absent
        private var howToConstruct = when (typeShape.abstractness) {
            Abstractness.Abstract -> JsonInteropDetails.HowToConstruct.CannotIsAbstract
            Abstractness.Concrete -> JsonInteropDetails.HowToConstruct.CannotNoConstructor
        }

        fun json() {
            hasJsonDecoration = true
        }

        fun sealed() {
            isSealed = true
        }

        fun property(name: String, type: String) {
            val ptype = typeHarness.type(type, extraDefinitions = typeShape.formals)
            properties.add(
                JsonInteropDetails.PropertyDecl(
                    unknownPos, unusedSourceName(name), Symbol(name), Abstractness.Concrete, ptype, null,
                ),
            )
        }

        fun extraProperty(name: String, knownValue: String) =
            extraProperty(name, Value(knownValue, TString))

        fun extraProperty(name: String, knownValue: Int) =
            extraProperty(name, Value(knownValue, TInt))

        fun extraProperty(name: String, knownValue: Value<*>) {
            properties.add(
                JsonInteropDetails.PropertyDecl(
                    unknownPos,
                    unusedSourceName(name),
                    Symbol(name),
                    Abstractness.Abstract,
                    null,
                    knownValue,
                ),
            )
        }

        fun toJson(p: JsonInteropDetails.MethodPresence = JsonInteropDetails.MethodPresence.Present) {
            this.toJsonMethod = p
        }

        fun fromJson(p: JsonInteropDetails.MethodPresence = JsonInteropDetails.MethodPresence.Present) {
            this.fromJsonMethod = p
        }

        fun constructor(vararg propertyNames: String) {
            check(howToConstruct == JsonInteropDetails.HowToConstruct.CannotNoConstructor)
            howToConstruct = JsonInteropDetails.HowToConstruct.ViaConstructor(
                propertyNames.map { propertyName ->
                    val p = properties.first {
                        (it.name as SourceName).baseName.nameText == propertyName
                    }
                    check(p.type != null)
                    p.name
                },
            )
        }

        fun toTypeDecl(sealedSubTypes: List<JsonInteropDetails.SealedSubType>?): JsonInteropDetails.TypeDecl {
            return JsonInteropDetails.TypeDecl(
                pos = unknownPos,
                definition = typeShape,
                properties = properties.toList(),
                howToConstruct = howToConstruct,
                hasJsonDecoration = hasJsonDecoration,
                toJsonMethod = toJsonMethod,
                fromJsonMethod = fromJsonMethod,
                sealedSubTypes = sealedSubTypes,
            )
        }
    }
}
