# Package lang.temper.be

<!-- The h1 name is specially interpreted by dokka -->

Support code for backends.  Backends are usually located in sub-JVM-packages
of this one, but in separate Gradle subprojects so the JS backend is in
`be-js:.../lang/temper/be/js`.

Each backend probably needs to create:

- a `myLang.out-grammar` file that specifies its output AST
- an implementation of *Backend* which converts *TmpL* trees to the target language *AST*.
- an implementation of *SupportNetwork* which specifies how to connect to code written in the target
  language.

## Connected methods

The *SupportNetwork* will need to specify how each *BuiltinOperatorId* translates and how
*\@connected* methods translate.

Not all *\@connected* methods need to be implemented by each backend. For
example, some backends implement `core.type ListBuilder.sort()` to customize behavior to
match Temper semantics, but some backends already have a method called `sort`
with matching semantics on the backend type used for `ListBuilder`.

### Core module connected methods

The set of *\@connected* methods from Temper's *CoreModule* are:

<!-- The below is kept up-to-date automatically -->
<!-- start CoreModule-connected -->
- `core.doneResult()`
- `core.empty()`
- `core.getConsole()`
- `core.ignore()`
- `core.type Boolean.toString()`
- `core.type Console.log()`
- `core.type DenseBitVector.constructor()`
- `core.type DenseBitVector.get()`
- `core.type DenseBitVector.set()`
- `core.type Deque.add()`
- `core.type Deque.constructor()`
- `core.type Deque.get isEmpty()`
- `core.type Deque.removeFirst()`
- `core.type DoneResult`
- `core.type Empty`
- `core.type Float64.abs()`
- `core.type Float64.acos()`
- `core.type Float64.asin()`
- `core.type Float64.atan()`
- `core.type Float64.atan2()`
- `core.type Float64.ceil()`
- `core.type Float64.cos()`
- `core.type Float64.cosh()`
- `core.type Float64.e`
- `core.type Float64.exp()`
- `core.type Float64.expm1()`
- `core.type Float64.floor()`
- `core.type Float64.log()`
- `core.type Float64.log10()`
- `core.type Float64.log1p()`
- `core.type Float64.max()`
- `core.type Float64.min()`
- `core.type Float64.near()`
- `core.type Float64.pi`
- `core.type Float64.pred()`
- `core.type Float64.round()`
- `core.type Float64.sign()`
- `core.type Float64.sin()`
- `core.type Float64.sinh()`
- `core.type Float64.sqrt()`
- `core.type Float64.succ()`
- `core.type Float64.tan()`
- `core.type Float64.tanh()`
- `core.type Float64.toInt32()`
- `core.type Float64.toInt32Unsafe()`
- `core.type Float64.toInt64()`
- `core.type Float64.toInt64Unsafe()`
- `core.type Float64.toString()`
- `core.type Generator`
- `core.type Generator.close()`
- `core.type Generator.done`
- `core.type Generator.next()`
- `core.type GeneratorResult`
- `core.type GlobalConsole.globalLog()`
- `core.type Int32.max()`
- `core.type Int32.min()`
- `core.type Int32.pred()`
- `core.type Int32.succ()`
- `core.type Int32.toFloat64()`
- `core.type Int32.toInt64()`
- `core.type Int32.toString()`
- `core.type Int64.max()`
- `core.type Int64.min()`
- `core.type Int64.pred()`
- `core.type Int64.succ()`
- `core.type Int64.toFloat64()`
- `core.type Int64.toFloat64Unsafe()`
- `core.type Int64.toInt32()`
- `core.type Int64.toInt32Unsafe()`
- `core.type Int64.toString()`
- `core.type List.forEach()`
- `core.type List.get length()`
- `core.type List.get()`
- `core.type List.toList()`
- `core.type List.toListBuilder()`
- `core.type ListBuilder.add()`
- `core.type ListBuilder.addAll()`
- `core.type ListBuilder.clear()`
- `core.type ListBuilder.constructor()`
- `core.type ListBuilder.get length()`
- `core.type ListBuilder.removeLast()`
- `core.type ListBuilder.reverse()`
- `core.type ListBuilder.set()`
- `core.type ListBuilder.sort()`
- `core.type ListBuilder.splice()`
- `core.type ListBuilder.toList()`
- `core.type ListBuilder.toListBuilder()`
- `core.type Listed.filter()`
- `core.type Listed.get isEmpty()`
- `core.type Listed.get length()`
- `core.type Listed.get()`
- `core.type Listed.getOr()`
- `core.type Listed.join()`
- `core.type Listed.map()`
- `core.type Listed.reduce()`
- `core.type Listed.reduceFrom()`
- `core.type Listed.slice()`
- `core.type Listed.sorted()`
- `core.type Listed.toList()`
- `core.type Listed.toListBuilder()`
- `core.type Map.constructor()`
- `core.type MapBuilder.clear()`
- `core.type MapBuilder.constructor()`
- `core.type MapBuilder.remove()`
- `core.type MapBuilder.set()`
- `core.type Mapped.forEach()`
- `core.type Mapped.get length()`
- `core.type Mapped.get()`
- `core.type Mapped.getOr()`
- `core.type Mapped.has()`
- `core.type Mapped.keys()`
- `core.type Mapped.toList()`
- `core.type Mapped.toListBuilder()`
- `core.type Mapped.toListBuilderWith()`
- `core.type Mapped.toListWith()`
- `core.type Mapped.toMap()`
- `core.type Mapped.toMapBuilder()`
- `core.type Mapped.values()`
- `core.type NoStringIndex`
- `core.type NoStringIndex.constructor()`
- `core.type Pair.constructor()`
- `core.type Promise`
- `core.type PromiseBuilder`
- `core.type PromiseBuilder.breakPromise()`
- `core.type PromiseBuilder.complete()`
- `core.type PromiseBuilder.constructor()`
- `core.type PromiseBuilder.get promise()`
- `core.type SafeGenerator.next()`
- `core.type SafeGenerator.nextSafe()`
- `core.type String.begin`
- `core.type String.countBetween()`
- `core.type String.forEach()`
- `core.type String.fromCodePoint()`
- `core.type String.fromCodePoints()`
- `core.type String.get end()`
- `core.type String.get isEmpty()`
- `core.type String.get()`
- `core.type String.hasAtLeast()`
- `core.type String.hasIndex()`
- `core.type String.indexOf()`
- `core.type String.next()`
- `core.type String.prev()`
- `core.type String.slice()`
- `core.type String.split()`
- `core.type String.step()`
- `core.type String.toFloat64()`
- `core.type String.toInt32()`
- `core.type String.toInt64()`
- `core.type String.toString()`
- `core.type StringBuilder`
- `core.type StringBuilder.append()`
- `core.type StringBuilder.appendBetween()`
- `core.type StringBuilder.appendCodePoint()`
- `core.type StringBuilder.clear()`
- `core.type StringBuilder.constructor()`
- `core.type StringBuilder.get end()`
- `core.type StringBuilder.toString()`
- `core.type StringIndex`
- `core.type StringIndex.none`
- `core.type StringIndexOption`
- `core.type StringIndexOption.compareTo()`
- `core.type ValueResult`
- `core.type ValueResult.constructor()`
<!-- end CoreModule-connected -->

### Standard library connected methods

The standard library is available with Temper but its modules aren't
automatically imported. It still contains `@connected` methods for backends
to implement:

<!-- The below is kept up-to-date automatically -->
<!-- start std-connected -->
- `std/net.sendRequest()`
- `std/net.type NetResponse`
- `std/net.type NetResponse.get bodyContent()`
- `std/net.type NetResponse.get contentType()`
- `std/net.type NetResponse.get status()`
- `std/regex.type Regex.compiledFind()`
- `std/regex.type Regex.compiledFound()`
- `std/regex.type Regex.compiledReplace()`
- `std/regex.type Regex.compiledSplit()`
- `std/regex.type RegexFormatter.adjustCodeSet()`
- `std/regex.type RegexFormatter.pushCaptureName()`
- `std/regex.type RegexFormatter.pushCodeTo()`
- `std/regex.type RegexFormatter.regexCompileFormatted()`
- `std/regex.type RegexFormatter.regexFormat()`
- `std/temporal.type Date`
- `std/temporal.type Date.constructor()`
- `std/temporal.type Date.day`
- `std/temporal.type Date.fromIsoString()`
- `std/temporal.type Date.get dayOfWeek()`
- `std/temporal.type Date.month`
- `std/temporal.type Date.toString()`
- `std/temporal.type Date.today()`
- `std/temporal.type Date.year`
- `std/temporal.type Date.yearsBetween()`
- `std/testing.processTestCases()`
- `std/testing.reportTestResults()`
- `std/testing.runTestCases()`
- `std/testing.type Test.assert()`
- `std/testing.type Test.assertHard()`
- `std/testing.type Test.bail()`
- `std/testing.type Test.get failedOnAssert()`
- `std/testing.type Test.get passing()`
- `std/testing.type Test.messages()`
<!-- end std-connected -->
