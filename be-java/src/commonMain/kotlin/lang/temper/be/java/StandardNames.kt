package lang.temper.be.java

// special names
const val SAM_PACKAGE_NAME = "function" // Put functional interfaces here

/** Temper convention: the field that represents the module export. */
const val MODULE_EXPORT_NAME = "export"

/** Fallback if nothing can be derived from [lang.temper.log.CodeLocation]. */
const val FALLABCK_MODULE_NAME = "AdHoc"

/** Given a module at the path `foo.bar-qux`, the class name will be `BarQuxGlobal`. */
const val MODULE_GLOBAL_SUFFIX = "Global"

/** Given a module at the path `foo.bar-qux`, the class name will be `BarQuxName`. */
const val MODULE_ENTRY_SUFFIX = "Main"

/** Given a module at the path `foo.bar-qux`, the class name will be `BarQuxTest`. */
const val MODULE_TEST_SUFFIX = "Test"

// This should always be overridden
val defaultSamPackage = QualifiedName.knownSafe(SAM_PACKAGE_NAME)

// package names
internal val javaLang = QualifiedName.knownSafe("java", "lang")
internal val javaMath = javaLang.qualifyKnownSafe("Math")
internal val javaTime = QualifiedName.knownSafe("java", "time")
internal val javaUtil = QualifiedName.knownSafe("java", "util")
internal val javaUtilConcurrent = javaUtil.qualifyKnownSafe("concurrent")
internal val javaUtilFunction = javaUtil.qualifyKnownSafe("function")
internal val temperPkg = QualifiedName.knownSafe("temper", "core")
internal val temperCore = temperPkg.qualifyKnownSafe("Core")
internal val temperStub = temperPkg.qualifyKnownSafe("Stub")
internal val temperStd = QualifiedName.knownSafe("temper", "std")

// standard Java names
val javaLangBoolean = javaLang.qualifyKnownSafe("Boolean")
val javaLangBooleanCompare = javaLangBoolean.qualifyKnownSafe("compare")
val javaLangBooleanToString = javaLangBoolean.qualifyKnownSafe("toString")
val javaLangByte = javaLang.qualifyKnownSafe("Boolean")
val javaLangCharacter = javaLang.qualifyKnownSafe("Character")
val javaLangClass = javaLang.qualifyKnownSafe("Class")
val javaLangClassForName = javaLangClass.qualifyKnownSafe("forName")
val javaLangClassNotFoundException = javaLang.qualifyKnownSafe("ClassNotFoundException")
val javaLangDeprecated = javaLang.qualifyKnownSafe("Deprecated")
val javaLangDouble = javaLang.qualifyKnownSafe("Double")
val javaLangDoubleCompare = javaLangDouble.qualifyKnownSafe("compare")
val javaLangDoubleToLongBits = javaLangDouble.qualifyKnownSafe("doubleToLongBits")
val javaLangDoublePositiveInfinity = javaLangDouble.qualifyKnownSafe("POSITIVE_INFINITY")
val javaLangDoubleNegativeInfinity = javaLangDouble.qualifyKnownSafe("NEGATIVE_INFINITY")
val javaLangDoubleNaN = javaLangDouble.qualifyKnownSafe("NaN")
val javaLangFloat = javaLang.qualifyKnownSafe("Float")
val javaLangIllegalStateException = javaLang.qualifyKnownSafe("IllegalStateException")
val javaLangInteger = javaLang.qualifyKnownSafe("Integer")
val javaLangIntegerCompare = javaLangInteger.qualifyKnownSafe("compare")
val javaLangIntegerToString = javaLangInteger.qualifyKnownSafe("toString")
val javaLangInterruptedException = javaLang.qualifyKnownSafe("InterruptedException")
val javaLangLong = javaLang.qualifyKnownSafe("Long")
val javaLangLongToString = javaLangLong.qualifyKnownSafe("toString")
val javaLangObject = javaLang.qualifyKnownSafe("Object")
val javaLangRuntimeException = javaLang.qualifyKnownSafe("RuntimeException")
val javaLangShort = javaLang.qualifyKnownSafe("Short")
val javaLangString = javaLang.qualifyKnownSafe("String")
val javaLangStringBuilder = javaLang.qualifyKnownSafe("StringBuilder")
val javaLangSystem = javaLang.qualifyKnownSafe("System")
val javaLangVoid = javaLang.qualifyKnownSafe("Void")

// java util classes
val javaUtilAbstractMap = javaUtil.qualifyKnownSafe("AbstractMap")
val javaUtilArrayDeque = javaUtil.qualifyKnownSafe("ArrayDeque")
val javaUtilArrayList = javaUtil.qualifyKnownSafe("ArrayList")
val javaUtilArrays = javaUtil.qualifyKnownSafe("Arrays")
val javaUtilArraysAsList = javaUtilArrays.qualifyKnownSafe("asList")
val javaUtilBitSet = javaUtil.qualifyKnownSafe("BitSet")
val javaUtilCollections = javaUtil.qualifyKnownSafe("Collections")
val javaUtilCollectionsReverse = javaUtilCollections.qualifyKnownSafe("reverse")
val javaUtilDeque = javaUtil.qualifyKnownSafe("Deque")
val javaUtilList = javaUtil.qualifyKnownSafe("List")
val javaUtilListCopyOf = javaUtilList.qualifyKnownSafe("copyOf") // 9+
val javaUtilListOf = javaUtilList.qualifyKnownSafe("of") // 9+
val javaUtilLinkedHashMap = javaUtil.qualifyKnownSafe("LinkedHashMap")
val javaUtilOptional = javaUtil.qualifyKnownSafe("Optional")
val javaUtilOptionalEmpty = javaUtilOptional.qualifyKnownSafe("empty")

// java util time classes
val javaTimeLocalDate = javaTime.qualifyKnownSafe("LocalDate")
val javaTimeLocalDateOf = javaTimeLocalDate.qualifyKnownSafe("of")
val javaTimeLocalDateNow = javaTimeLocalDate.qualifyKnownSafe("now")
val javaTimeLocalDateParse = javaTimeLocalDate.qualifyKnownSafe("parse")
val javaTimeTemporal = javaTime.qualifyKnownSafe("temporal")
val javaTimeTemporalChronoUnit = javaTimeTemporal.qualifyKnownSafe("ChronoUnit")
val javaTimeTemporalChronoUnitYears = javaTimeTemporalChronoUnit.qualifyKnownSafe("YEARS")
val javaTimeZoneId = javaTime.qualifyKnownSafe("ZoneId")
val javaTimeZoneIdOfOffset = javaTimeZoneId.qualifyKnownSafe("ofOffset")
val javaTimeZoneOffset = javaTime.qualifyKnownSafe("ZoneOffset")
val javaTimeZoneOffsetUtc = javaTimeZoneOffset.qualifyKnownSafe("UTC")

// java util concurrent classes
val javaUtilConcurrentCompletableFuture = javaUtilConcurrent.qualifyKnownSafe("CompletableFuture")
val javaUtilConcurrentExecutionException = javaUtilConcurrent.qualifyKnownSafe("ExecutionException")

// LinkedList is used as a Deque that handles null elements
val javaUtilLinkedList = javaUtil.qualifyKnownSafe("LinkedList")
val javaUtilMap = javaUtil.qualifyKnownSafe("Map")
val javaUtilMapEntry = javaUtilMap.qualifyKnownSafe("Entry")
val javaUtilObjects = javaUtil.qualifyKnownSafe("Objects")
val javaUtilObjectsEquals = javaUtilObjects.qualifyKnownSafe("equals")
val javaUtilSimpleImmutableEntry = javaUtilAbstractMap.qualifyKnownSafe("SimpleImmutableEntry")

// Logging
val javaUtilLogging = javaUtil.qualifyKnownSafe("logging")
val javaUtilLoggingLogger = javaUtilLogging.qualifyKnownSafe("Logger")
val javaUtilLoggingLoggerGetLogger = javaUtilLoggingLogger.qualifyKnownSafe("getLogger")

// Math
val javaMathE = javaMath.qualifyKnownSafe("E")
val javaMathPi = javaMath.qualifyKnownSafe("PI")
val javaMathAbs = javaMath.qualifyKnownSafe("abs")
val javaMathAcos = javaMath.qualifyKnownSafe("acos")
val javaMathAsin = javaMath.qualifyKnownSafe("asin")
val javaMathAtan = javaMath.qualifyKnownSafe("atan")
val javaMathAtan2 = javaMath.qualifyKnownSafe("atan2")
val javaMathCeil = javaMath.qualifyKnownSafe("ceil")
val javaMathCos = javaMath.qualifyKnownSafe("cos")
val javaMathCosh = javaMath.qualifyKnownSafe("cosh")
val javaMathExp = javaMath.qualifyKnownSafe("exp")
val javaMathExpm1 = javaMath.qualifyKnownSafe("expm1")
val javaMathFloor = javaMath.qualifyKnownSafe("floor")
val javaMathLog = javaMath.qualifyKnownSafe("log")
val javaMathLog10 = javaMath.qualifyKnownSafe("log10")
val javaMathLog1p = javaMath.qualifyKnownSafe("log1p")
val javaMathMax = javaMath.qualifyKnownSafe("max")
val javaMathMin = javaMath.qualifyKnownSafe("min")
val javaMathPow = javaMath.qualifyKnownSafe("pow")
val javaMathRound = javaMath.qualifyKnownSafe("round")
val javaMathSignum = javaMath.qualifyKnownSafe("signum")
val javaMathSin = javaMath.qualifyKnownSafe("sin")
val javaMathSinh = javaMath.qualifyKnownSafe("sinh")
val javaMathSqrt = javaMath.qualifyKnownSafe("sqrt")
val javaMathTan = javaMath.qualifyKnownSafe("tan")
val javaMathTanh = javaMath.qualifyKnownSafe("tanh")

// nullability annotations; TODO make these configurable
val annNullable = temperPkg.qualifyKnownSafe("Nullable")
val annNotNull = temperPkg.qualifyKnownSafe("NonNull")

// Temper Core names
val temperAdaptGeneratorFn = temperCore.qualifyKnownSafe("adaptGeneratorFn")
val temperSafeAdaptGeneratorFn = temperCore.qualifyKnownSafe("safeAdaptGeneratorFn")
val temperBoxedEq = temperCore.qualifyKnownSafe("boxedEq")
val temperBoxedEqRev = temperCore.qualifyKnownSafe("boxedEqRev")
val temperBubble = javaLangRuntimeException
val temperBubbleMethod = temperCore.qualifyKnownSafe("bubble")
val temperCast = temperCore.qualifyKnownSafe("cast")
val temperCastToNonNull = temperCore.qualifyKnownSafe("castToNonNull")
val temperConsoleClass = temperCore.qualifyKnownSafe("Console")
val temperGetConsoleMethod = temperCore.qualifyKnownSafe("getConsole")
val temperDequeRemoveFirst = temperCore.qualifyKnownSafe("dequeRemoveFirst")
val temperDivIntInt = temperCore.qualifyKnownSafe("divIntInt")
val temperModIntInt = temperCore.qualifyKnownSafe("modIntInt")
val temperDoNothing = temperCore.qualifyKnownSafe("doNothing")
val temperThrowBubble = temperCore.qualifyKnownSafe("throwBubble")
val temperFloat64Near = temperCore.qualifyKnownSafe("float64Near")
val temperFloat64ToInt = temperCore.qualifyKnownSafe("float64ToInt")
val temperFloat64ToInt64 = temperCore.qualifyKnownSafe("float64ToInt64")
val temperFloat64ToString = temperCore.qualifyKnownSafe("float64ToString")
val temperGenericCompare = temperCore.qualifyKnownSafe("genericCmp")
val temperGenerator = temperPkg.qualifyKnownSafe("Generator")
val temperGeneratorResult = temperGenerator.qualifyKnownSafe("Result")
val temperGeneratorDoneResult = temperGenerator.qualifyKnownSafe("DoneResult")
val temperGeneratorDoneResultGet = temperGeneratorDoneResult.qualifyKnownSafe("get")
val temperGeneratorValueResult = temperGenerator.qualifyKnownSafe("ValueResult")
val temperGlobalConsole = temperCore.qualifyKnownSafe("GlobalConsole")
val temperInitSimpleLogging = temperCore.qualifyKnownSafe("initSimpleLogging")
val temperInt64ToFloat64 = temperCore.qualifyKnownSafe("int64ToFloat64")
val temperInt64ToInt = temperCore.qualifyKnownSafe("int64ToInt")
val temperListAdd = temperCore.qualifyKnownSafe("listAdd")
val temperListAddAll = temperCore.qualifyKnownSafe("listAddAll")
val temperListCopyOf = temperCore.qualifyKnownSafe("listCopyOf")
val temperListFilter = temperCore.qualifyKnownSafe("listFilter")
val temperListGet = temperCore.qualifyKnownSafe("listGet")
val temperListGetOr = temperCore.qualifyKnownSafe("listGetOr")
val temperListJoin = temperCore.qualifyKnownSafe("listJoin")
val temperListMap = temperCore.qualifyKnownSafe("listMap")
val temperListMapDropping = temperCore.qualifyKnownSafe("listMapDropping")
val temperListedReduce = temperCore.qualifyKnownSafe("listedReduce")
val temperListOf = temperCore.qualifyKnownSafe("listOf")
val temperListRemoveLast = temperCore.qualifyKnownSafe("listRemoveLast")
val temperListSlice = temperCore.qualifyKnownSafe("listSlice")
val temperListSort = temperCore.qualifyKnownSafe("listSort")
val temperListSorted = temperCore.qualifyKnownSafe("listSorted")
val temperListSplice = temperCore.qualifyKnownSafe("listSplice")
val temperListedToList = temperCore.qualifyKnownSafe("listedToList")
val temperMappedGet = temperCore.qualifyKnownSafe("mappedGet")
val temperMappedToMap = temperCore.qualifyKnownSafe("mappedToMap")
val temperMappedToList = temperCore.qualifyKnownSafe("mappedToList")
val temperMappedToListBuilder = temperCore.qualifyKnownSafe("mappedToListBuilder")
val temperMappedToListWith = temperCore.qualifyKnownSafe("mappedToListWith")
val temperMappedToListBuilderWith = temperCore.qualifyKnownSafe("mappedToListBuilderWith")
val temperMappedForEach = temperCore.qualifyKnownSafe("mappedForEach")
val temperMapConstructor = temperCore.qualifyKnownSafe("mapConstructor")
val temperMapBuilderRemove = temperCore.qualifyKnownSafe("mapBuilderRemove")
val temperPrint = temperCore.qualifyKnownSafe("print")
val temperRequireNoStringIndex = temperCore.qualifyKnownSafe("requireNoStringIndex")
val temperRequireStringIndex = temperCore.qualifyKnownSafe("requireStringIndex")
val temperRunAsync = temperCore.qualifyKnownSafe("runAsync")
val temperStringFromCodePoint = temperCore.qualifyKnownSafe("stringFromCodePoint")
val temperStringFromCodePoints = temperCore.qualifyKnownSafe("stringFromCodePoints")
val temperStringSplit = temperCore.qualifyKnownSafe("stringSplit")
val temperStringToFloat64 = temperCore.qualifyKnownSafe("stringToFloat64")
val temperStringToInt = temperCore.qualifyKnownSafe("stringToInt")
val temperStringToInt64 = temperCore.qualifyKnownSafe("stringToInt64")
val temperStringCountBetween = temperCore.qualifyKnownSafe("stringCountBetween")
val temperStringForEach = temperCore.qualifyKnownSafe("stringForEach")
val temperStringHasAtLeast = temperCore.qualifyKnownSafe("stringHasAtLeast")
val temperStringHasIndex = temperCore.qualifyKnownSafe("stringHasIndex")
val temperStringNext = temperCore.qualifyKnownSafe("stringNext")
val temperStringPrev = temperCore.qualifyKnownSafe("stringPrev")
val temperStringStep = temperCore.qualifyKnownSafe("stringStep")
val temperStringSlice = temperCore.qualifyKnownSafe("stringSlice")
val temperStringBuilderAppendBetween = temperCore.qualifyKnownSafe("stringBuilderAppendBetween")
val temperStringBuilderAppendCodePoint = temperCore.qualifyKnownSafe("stringBuilderAppendCodePoint")
val temperThrowAssertionError = temperCore.qualifyKnownSafe("throwAssertionError")
val temperWaitUntilTasksComplete = temperCore.qualifyKnownSafe("waitUntilTasksComplete")

// Module internal names
val temperRegexPkg = temperStd.qualifyKnownSafe("regex")
val temperRegexCore = temperRegexPkg.qualifyKnownSafe("Core")
val temperTemporalPkg = temperStd.qualifyKnownSafe("temporal")
val temperTemporalCore = temperTemporalPkg.qualifyKnownSafe("Core")

// Regex names
val temperRegexFormat = temperRegexCore.qualifyKnownSafe("regexFormat")
val temperRegexCompiledFormatted = temperRegexCore.qualifyKnownSafe("regexCompiledFormatted")
val temperRegexCompiledFound = temperRegexCore.qualifyKnownSafe("regexCompiledFound")
val temperRegexCompiledFind = temperRegexCore.qualifyKnownSafe("regexCompiledFind")
val temperRegexCompiledReplace = temperRegexCore.qualifyKnownSafe("regexCompiledReplace")
val temperRegexCompiledSplit = temperRegexCore.qualifyKnownSafe("regexCompiledSplit")
val temperRegexFormatterPushCodeTo = temperRegexCore.qualifyKnownSafe("regexFormatterPushCodeTo")

// std/net
val temperNetPkg = temperPkg.qualifyKnownSafe("net")
val temperNetCore = temperNetPkg.qualifyKnownSafe("Core")
val temperNetResponse = temperNetPkg.qualifyKnownSafe("NetResponse")
val temperNetCoreStdNetSend = temperNetCore.qualifyKnownSafe("stdNetSend")

// coroutine conversion
val coroutineConversionSpecial = temperPkg.qualifyKnownSafe("coro")
val coroPromiseResultAsync = coroutineConversionSpecial.qualifyKnownSafe("getPromiseResultAsync")
val coroAwakeUpon = coroutineConversionSpecial.qualifyKnownSafe("awakeUpon")

const val STRICT_TYPES = false

// Stub names; these are mostly for diagnostics.
val temperPureVirtual = temperStub.qualifyKnownSafe("pureVirtual")
val temperPureVirtualStr = temperPureVirtual.fullyQualified
val temperInvalidStrict = temperStub.qualifyKnownSafe("InvalidType")
val temperMistranslation = temperStub.qualifyKnownSafe("cantTranslate")

/** If [STRICT_TYPES] is `false`, this may resolve to an Object. */
val temperInvalidLoose = if (STRICT_TYPES) temperInvalidStrict else javaLangObject

// JUnit names
val junitPkg = QualifiedName.knownSafe("org", "junit", "jupiter", "api")
val junitTest = junitPkg.qualifyKnownSafe("Test")
