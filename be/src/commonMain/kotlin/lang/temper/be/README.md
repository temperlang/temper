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
example, some backends implement `ListBuilder::sort` to customize behavior to
match Temper semantics, but some backends already have a method called `sort`
with matching semantics on the backend type used for `ListBuilder`.

### Implicits module connected methods

The set of *\@connected* methods from Temper's *ImplicitsModule* are:

<!-- The below is kept up-to-date automatically -->
<!-- start ImplicitsModule-connected -->
- `::getConsole`
- `Boolean::toString`
- `Console::log`
- `DenseBitVector::constructor`
- `DenseBitVector::get`
- `DenseBitVector::set`
- `Deque::add`
- `Deque::constructor`
- `Deque::isEmpty`
- `Deque::removeFirst`
- `DoneResult`
- `Empty`
- `Float64::abs`
- `Float64::acos`
- `Float64::asin`
- `Float64::atan`
- `Float64::atan2`
- `Float64::ceil`
- `Float64::cos`
- `Float64::cosh`
- `Float64::e`
- `Float64::exp`
- `Float64::expm1`
- `Float64::floor`
- `Float64::log`
- `Float64::log10`
- `Float64::log1p`
- `Float64::max`
- `Float64::min`
- `Float64::near`
- `Float64::pi`
- `Float64::round`
- `Float64::sign`
- `Float64::sin`
- `Float64::sinh`
- `Float64::sqrt`
- `Float64::tan`
- `Float64::tanh`
- `Float64::toInt32`
- `Float64::toInt32Unsafe`
- `Float64::toInt64`
- `Float64::toInt64Unsafe`
- `Float64::toString`
- `Generator`
- `Generator::close`
- `Generator::done`
- `Generator::next`
- `GeneratorResult`
- `GlobalConsole::globalLog`
- `Int32::max`
- `Int32::min`
- `Int32::toFloat64`
- `Int32::toInt64`
- `Int32::toString`
- `Int64::max`
- `Int64::min`
- `Int64::toFloat64`
- `Int64::toFloat64Unsafe`
- `Int64::toInt32`
- `Int64::toInt32Unsafe`
- `Int64::toString`
- `List::forEach`
- `List::get`
- `List::length`
- `List::toList`
- `List::toListBuilder`
- `ListBuilder::add`
- `ListBuilder::addAll`
- `ListBuilder::clear`
- `ListBuilder::constructor`
- `ListBuilder::length`
- `ListBuilder::removeLast`
- `ListBuilder::reverse`
- `ListBuilder::set`
- `ListBuilder::sort`
- `ListBuilder::splice`
- `ListBuilder::toList`
- `ListBuilder::toListBuilder`
- `Listed::filter`
- `Listed::get`
- `Listed::getOr`
- `Listed::isEmpty`
- `Listed::join`
- `Listed::length`
- `Listed::map`
- `Listed::mapDropping`
- `Listed::reduce`
- `Listed::reduceFrom`
- `Listed::slice`
- `Listed::sorted`
- `Listed::toList`
- `Listed::toListBuilder`
- `Map::constructor`
- `MapBuilder::clear`
- `MapBuilder::constructor`
- `MapBuilder::remove`
- `MapBuilder::set`
- `Mapped::forEach`
- `Mapped::get`
- `Mapped::getOr`
- `Mapped::has`
- `Mapped::keys`
- `Mapped::length`
- `Mapped::toList`
- `Mapped::toListBuilder`
- `Mapped::toListBuilderWith`
- `Mapped::toListWith`
- `Mapped::toMap`
- `Mapped::toMapBuilder`
- `Mapped::values`
- `NoStringIndex`
- `NoStringIndex::constructor`
- `Pair::constructor`
- `Promise`
- `PromiseBuilder`
- `PromiseBuilder::breakPromise`
- `PromiseBuilder::complete`
- `PromiseBuilder::constructor`
- `PromiseBuilder::getPromise`
- `SafeGenerator::next`
- `String::begin`
- `String::countBetween`
- `String::end`
- `String::forEach`
- `String::fromCodePoint`
- `String::fromCodePoints`
- `String::get`
- `String::hasAtLeast`
- `String::hasIndex`
- `String::indexOf`
- `String::isEmpty`
- `String::next`
- `String::prev`
- `String::slice`
- `String::split`
- `String::step`
- `String::toFloat64`
- `String::toInt32`
- `String::toInt64`
- `String::toString`
- `StringBuilder`
- `StringBuilder::append`
- `StringBuilder::appendBetween`
- `StringBuilder::appendCodePoint`
- `StringBuilder::constructor`
- `StringBuilder::toString`
- `StringIndex`
- `StringIndex::none`
- `StringIndexOption`
- `StringIndexOption::compareTo`
- `ValueResult`
- `ValueResult::constructor`
- `doneResult`
- `empty`
- `ignore`
<!-- end ImplicitsModule-connected -->

### Standard library connected methods

The standard library is available with Temper but its modules aren't
automatically imported. It still contains `@connected` methods for backends
to implement:

<!-- The below is kept up-to-date automatically -->
<!-- start std-connected -->
- `::processTestCases`
- `::reportTestResults`
- `::runTestCases`
- `Date`
- `Date::constructor`
- `Date::fromIsoString`
- `Date::getDay`
- `Date::getDayOfWeek`
- `Date::getMonth`
- `Date::getYear`
- `Date::toString`
- `Date::today`
- `Date::yearsBetween`
- `NetResponse`
- `NetResponse::getBodyContent`
- `NetResponse::getContentType`
- `NetResponse::getStatus`
- `Regex::compileFormatted`
- `Regex::compiledFind`
- `Regex::compiledFound`
- `Regex::compiledReplace`
- `Regex::compiledSplit`
- `Regex::format`
- `RegexFormatter::adjustCodeSet`
- `RegexFormatter::pushCaptureName`
- `RegexFormatter::pushCodeTo`
- `Test::assert`
- `Test::assertHard`
- `Test::bail`
- `Test::failedOnAssert`
- `Test::messages`
- `Test::passing`
- `stdNetSend`
<!-- end std-connected -->
