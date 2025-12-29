import * as runtimeSupport from '../../../commonMain/resources/lang/temper/be/js/temper-core/index.js';

import {readFileSync} from 'fs';
import {dirname,join} from 'path';
import {fileURLToPath} from 'url';

import {describe, it} from 'mocha';
import {expect} from 'chai';

// The README for the backend package contains a list of connected methods that backends should
// implement.

const __dirname = dirname(fileURLToPath(import.meta.url));
const connectedsList = ((content) => {
    const [_, markdownList] =
        /<!-- start ImplicitsModule-connected -->([\s\S]*)<!-- end ImplicitsModule-connected -->/
        .exec(content);
    return markdownList
        .split(/[\r\n]+/)
        .map((x) => x.replace(/^-\s*[`]|[`]\s*$/g, ''))
        .filter((x) => !!x);
})(readFileSync(
    join(
        __dirname, '..', '..', '..', '..', '..',
        'be', 'src', 'commonMain', 'kotlin', 'lang', 'temper', 'be', 'README.md'
    )
));

const skipped = new Set([
    // Reused from implementations for related types.
    "listToListBuilder",
    "listForEach",
    "listFilter",
    "listGet",
    "listGetOr",
    "listJoin",
    "listMap",
    "listMapDropping",
    "listSlice",
    "listedLength",
    "listedToListBuilder",
    "listBuilderConstructor",
    "listBuilderLength",
    "listBuilderToListBuilder",
    "listedIsEmpty",
    // Used only in interpreter.
    "globalConsoleGlobalLog",
    // Used only by docgen and not in js.
    "consoleLog",
    // Inlined.
    "booleanToString",
    "float64Abs",
    "float64Acos",
    "float64Asin",
    "float64Atan",
    "float64Atan2",
    "float64Ceil",
    "float64Cos",
    "float64Cosh",
    "float64E",
    "float64Exp",
    "float64Expm1",
    "float64Floor",
    "float64Log",
    "float64Log10",
    "float64Log1p",
    "float64Max",
    "float64Min",
    "float64Pi",
    "float64Round",
    "float64Sign",
    "float64Sin",
    "float64Sinh",
    "float64Sqrt",
    "float64Tan",
    "float64Tanh",
    "::getConsole",
    "ignore",
    "int32Max",
    "int32Min",
    "int32ToFloat64",
    "int32ToFloat64Unsafe",
    "int32ToInt64",
    "int32ToString",
    "int64ToString",
    "listToList",
    "stringCodeAt",
    "stringBegin",
    "stringBuilder",
    "stringBuilderAppend",
    "stringBuilderAppendBetween",
    "stringBuilderConstructor",
    "stringBuilderToString",
    "stringEnd",
    "stringHasIndex",
    "stringIndexOf",
    "stringSlice",
    "stringToString",
    "stringIndexOptionCompareTo",
    // Type aliases that don't need exports
    "stringIndex",
    "stringIndexOption",
    "noStringIndex",
    "noStringIndexConstructor", // not constructible
    // Related types are connected.
    "doneResult",
    "generator",
    "generatorClose",
    "generatorDone",
    "generatorNext",
    "generatorResult",
    "promise",
    "promiseBuilder",
    "promiseBuilderConstructor",
    "safeGeneratorNext",
    "stringIndex",
    "valueResult",
    "valueResultConstructor",
    // Default js member already works.
    "listBuilderSort",
    "listLength",
    "listedReduce",
    "mapBuilderClear",
    "promiseBuilderBreakPromise",
    "promiseBuilderComplete",
    "promiseBuilderGetPromise",
]);

describe("runtime-support", () => {
    it('connected-methods-exported', () => {
        const connectedMethodKeys = connectedsList.map(
            (connectedMethod) => `${
                connectedMethod[0].toLowerCase()
            }${
                connectedMethod.substring(1).replace(/::(.)/g, (_, x) => x.toUpperCase())
            }`);
        const expected = connectedMethodKeys.filter(
            // We also don't actually expect StringSlice methods because we
            // replace the full type.
            (x) => !(skipped.has(x) || x.includes("StringSlice"))
        );
        const present = connectedMethodKeys.filter((x) => x in runtimeSupport);
        expect(present).to.deep.equal(expected);
    });
});
