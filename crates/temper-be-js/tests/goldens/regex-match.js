import {
  MissingKey,
  isBubble,
  listedGet,
  mappedGet,
  stringCountBetween,
} from "@temperlang/core";
import {
  Begin,
  Capture,
  CodePart,
  CodePoints,
  CodeRange,
  CodeSet,
  Dot,
  Group,
  Match,
  Or,
  RegexNode,
  Repeat,
  Sequence,
  WordBoundary,
  entire,
  oneOrMore,
} from "@temperlang/std/regex";

/** @param {Group} group */
function checkGroup(group) {
  const name = group.name;
  const value = group.value;
  const begin = group.begin;
  const end = group.end;
  const size = stringCountBetween(text, begin, end).toString();
  const begin2 = stringCountBetween(text, 0, begin).toString();
  console.log(`match group '${name}': '${value}' at ${begin2} size ${size}`);
}

const regex = /** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (oneOrMore(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodePoints("ab")))]))))))), /** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("c")))])))).compiled();
const full = entire(regex.data).compiled();
/** @type {readonly string[]} */
const strings = Object.freeze(["abc", "ac", "bbabac", "ab", "dc", "acc", "cbc"]);
{
  const length = strings.length;
  let i = 0;
  while (i < length) {
    const string = listedGet(/** @type {string[]} */ (strings), i);
    i = (i + 1) | 0;
    console.log(`found ${string}: ${regex.found(string)}`);
    console.log(`matches ${string}: ${full.found(string)}`);
  }
}
const catcher = /** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("a"))), 1, null, false))), /** @type {RegexNode} */ (/** @type {unknown} */ (new Capture("intro", /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("b"))), 1, null, false)))))), /** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("c"))), /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new Or(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (new Capture("option1", /** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("d")))))), /** @type {RegexNode} */ (/** @type {unknown} */ (new Capture("alphaOption2", /** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("e"))))))])))), 1, null, false)))])))).compiled();
const text = "🌍aaabbceef";
const match = catcher.find(text);
checkGroup(match.full);
checkGroup(mappedGet(match.groups, "intro"));
checkGroup(mappedGet(match.groups, "alphaOption2"));
const thing = /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(97, 122)))]), false))), 1, null, false))).compiled();
const multiText = "---abc---xyz---";
const first = thing.find(multiText);
const second = thing.find(multiText, first.full.end);
console.log(`Found both ${first.full.value} and ${second.full.value}.`);
const moreText = "🌍aaabbcee🌎abcd🌏";

/**
 * @param {Match} match
 * @returns {string}
 */
function fn(match) {
  /** @type {Map<string, Group>} */
  const groups = match.groups;
  /** @type {string} */
  let option1;
  try {
    option1 = mappedGet(groups, "option1").value;
  } catch (e$) {
    if (!(e$ instanceof MissingKey)) throw e$;
    option1 = "";
  }
  /** @type {string} */
  let alphaOption2;
  try {
    alphaOption2 = mappedGet(groups, "alphaOption2").value;
  } catch (e$) {
    if (!(e$ instanceof MissingKey)) throw e$;
    alphaOption2 = "";
  }
  const begin = stringCountBetween(moreText, 0, match.full.begin);
  return `-${option1}${alphaOption2}${begin}-`;
}

const replaced = catcher.replace(moreText, fn);
console.log(`replaced: ${replaced}`);
const notReplaced = catcher.replace("nope", (match) => "hi");
console.log(`not replaced: ${notReplaced}`);
/** @type {string} */
let noMatch;
try {
  noMatch = catcher.find("not here").full.value;
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  noMatch = "sorry";
}
console.log(`No match says ${noMatch}`);
const or = /** @type {RegexNode} */ (/** @type {unknown} */ (new Or(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("ab"))), /** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("bc")))])))).compiled().data;
const option1 = "ab";
const option2 = "bc";
const optionNone = "cb";
console.log(`or option 1: ${or.found(option1)}`);
console.log(`or option 2: ${or.found(option2)}`);
console.log(`or option none: ${or.found(optionNone)}`);
const begin = /** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (Begin)), /** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("a")))])))).compiled();
const beginsWithATrue = "ab";
const beginsWithAFalse = "bab";
console.log(`begin true: ${begin.found(beginsWithATrue)}`);
console.log(`begin false: ${begin.found(beginsWithAFalse)}`);
const negatedBasic = entire(/** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("a"))), /** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(97, 99)))]), true)))])))).compiled().data).compiled();
const nbTrue = "a🌍";
const nbFalse = "ac";
console.log(`negated basic true: ${negatedBasic.found(nbTrue)}`);
console.log(`negated basic false: ${negatedBasic.found(nbFalse)}`);
const simplerLines = "It was the best of times.\nIt was the worst of times.";
console.log(/** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([]), true))), 1, null, false))).compiled().find(simplerLines).full.value);
/** @type {string} */
let t$0;
try {
  t$0 = /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([]), false))), 1, null, false))).compiled().find(simplerLines).full.value;
} catch (e$) {
  if (!isBubble(e$)) throw e$;
  t$0 = "-";
}
console.log(t$0);
const notLow = /** @type {RegexNode} */ (/** @type {unknown} */ (oneOrMore(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(0, 47)))]), true)))))).compiled();
console.log(`not low: ${notLow.find("-/a🌍-").full.value}`);
console.log(`not space or >: ${/** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodePoints("\t"))), /** @type {CodePart} */ (/** @type {unknown} */ (new CodePoints("\n"))), /** @type {CodePart} */ (/** @type {unknown} */ (new CodePoints(" "))), /** @type {CodePart} */ (/** @type {unknown} */ (new CodePoints(">")))]), true))), 1, null, false))).compiled().find("ab>c").full.value}`);
const idStart = /** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(97, 122))), /** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(65, 90))), /** @type {CodePart} */ (/** @type {unknown} */ (new CodePoints("_")))]), false))).compiled().data;
const idContinue = new Or(Object.freeze([idStart, /** @type {RegexNode} */ (/** @type {unknown} */ (new CodeSet(Object.freeze([/** @type {CodePart} */ (/** @type {unknown} */ (new CodeRange(48, 57)))]), false))).compiled().data]));
const word = /** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (WordBoundary)), idStart, /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (idContinue)), 0, null))), /** @type {RegexNode} */ (/** @type {unknown} */ (WordBoundary))])))).compiled();
const expected = "def_456";
const found = word.find(`123_abc ${expected} ghi_789`).full.value;
console.log(`Found id: ${found}`);
const stringToEscape = "$(*^";
const regexWithInterpolatedString = /** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (Dot)), /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints(stringToEscape))), 1, null, false))), /** @type {RegexNode} */ (/** @type {unknown} */ (Dot))])))).compiled();
const stringToEscapeAndMore = `a${stringToEscape}${stringToEscape}b`;
const escapedMatch = regexWithInterpolatedString.find(stringToEscapeAndMore);
console.log(escapedMatch.full.value);
const ownFullMatch = /** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (Dot)), /** @type {RegexNode} */ (/** @type {unknown} */ (new Capture("full", /** @type {RegexNode} */ (/** @type {unknown} */ (Dot)))))])))).compiled().find("ab");
const ownFull = mappedGet(ownFullMatch.groups, "full").value;
console.log(`Own full: ${ownFull} vs ${ownFullMatch.full.value}`);
const comboLines = "It was the best\nof times.\r\n\rIt was the worst of times.";
{
  /** @type {readonly string[]} */
  const list = /** @type {RegexNode} */ (/** @type {unknown} */ (new Or(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("\n"))), /** @type {RegexNode} */ (/** @type {unknown} */ (new Sequence(Object.freeze([/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("\r"))), /** @type {RegexNode} */ (/** @type {unknown} */ (new Repeat(/** @type {RegexNode} */ (/** @type {unknown} */ (new CodePoints("\n"))), 0, 1, false)))]))))])))).compiled().split(comboLines);
  const length2 = list.length;
  let i2 = 0;
  while (i2 < length2) {
    const line = listedGet(/** @type {string[]} */ (list), i2);
    i2 = (i2 + 1) | 0;
    console.log(`${line};`);
  }
}
