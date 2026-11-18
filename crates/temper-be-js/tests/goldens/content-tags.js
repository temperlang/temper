import { listedGet, modIntInt, stringSplit, type } from "@temperlang/core";

/**
 * @param {string} problem
 * @param {boolean} isError
 */
function ignore(problem, isError) {
  if (isError) {
    console.log(problem);
  }
}

/**
 * @param {Propagator} p
 * @param {boolean} before
 * @param {string | null} known
 * @param {(arg0: boolean, arg1: boolean) => boolean} same
 * @param {(arg0: string, arg1: boolean) => void} callout
 * @returns {After}
 */
export function propagateOver(p, before, known, same, callout) {
  if (known == null) {
    return new After(Object.freeze([]), before);
  }
  if (known === "!") {
    return new After(Object.freeze([new EventParseEffect(new Mark("bang"))]), before);
  }
  const angles = stringSplit(known, "<").length;
  if (angles > 1) {
    callout("angle bracket in fixed text", false);
  }
  if (!same(before, before)) {
    callout("states differ from themselves", true);
  }
  const quotes = (stringSplit(known, p.quote).length - 1) | 0;
  return new After(Object.freeze([new AppendParseEffect(known)]), modIntInt(quotes, 2) === 1 ? !before : before);
}

/**
 * @param {string} name
 * @param {string} who
 * @returns {string}
 */
function greet(name, who) {
  {
    const collector = Quoting.newCollector();
    {
      collector.append("say 'hi ");
      collector.append(QuoteEscaper.instance.applyString(TextEscaper.instance.applyString(name)));
      collector.append("' to ");
      collector.append(TextEscaper.instance.applyString(who));
      Quoting.enact(new Mark("bang"), collector);
    }
    return Quoting.fromCollector(collector);
  }
}

/**
 * @param {boolean} c
 * @param {string} x
 * @returns {string}
 */
function branch(c, x) {
  {
    const collector = Quoting.newCollector();
    {
      collector.append(TextEscaper.instance.applyString(x));
      if (c) {
        collector.append("''");
      }
      collector.append(" '");
      collector.append(QuoteEscaper.instance.applyString(TextEscaper.instance.applyString(x)));
      collector.append("'");
    }
    return Quoting.fromCollector(collector);
  }
}

/**
 * @param {readonly string[]} xs
 * @returns {string}
 */
function each(xs) {
  {
    const collector = Quoting.newCollector();
    {
      {
        const length = xs.length;
        let i = 0;
        while (i < length) {
          const x = listedGet(/** @type {string[]} */ (xs), i);
          i = (i + 1) | 0;
          collector.append("'");
          collector.append(QuoteEscaper.instance.applyString(TextEscaper.instance.applyString(x)));
          collector.append("' ");
          collector.append(TextEscaper.instance.applyString(x));
          collector.append(";");
        }
      }
    }
    return Quoting.fromCollector(collector);
  }
}

/**
 * @param {number} n
 * @param {string} x
 * @returns {string}
 */
function counted(n, x) {
  {
    const collector = Quoting.newCollector();
    {
      let i = 0;
      while (i < n) {
        i = (i + 1) | 0;
        if (i === 2) {
          continue;
        }
        collector.append("'");
        collector.append(QuoteEscaper.instance.applyString(TextEscaper.instance.applyString(x)));
        collector.append("'");
        if (i === 4) {
          break;
        }
        collector.append(TextEscaper.instance.applyString(x));
        collector.append(",");
      }
    }
    return Quoting.fromCollector(collector);
  }
}

/**
 * @param {boolean} c
 * @param {string} x
 * @returns {string}
 */
function attribute(c, x) {
  {
    const collector = Quoting.newCollector();
    {
      collector.append("a='");
      if (c) {
        return "none";
      }
      collector.append(QuoteEscaper.instance.applyString(TextEscaper.instance.applyString(x)));
      collector.append("'");
    }
    return Quoting.fromCollector(collector);
  }
}

/**
 * @param {readonly string[]} xs
 * @returns {string}
 */
function opened(xs) {
  {
    const accumulator = new Quoting();
    {
      {
        const length = xs.length;
        let i = 0;
        while (i < length) {
          const x = listedGet(/** @type {string[]} */ (xs), i);
          i = (i + 1) | 0;
          accumulator.appendSafe("'");
          accumulator.append(x);
        }
      }
    }
    return accumulator.accumulated;
  }
}

export class Mark extends type() {
  /** @type {string} */
  #label;

  /** @param {string} label */
  constructor(label) {
    super();
    this.#label = label;
  }

  /** @returns {string} */
  get label() {
    return this.#label;
  }
}

export class Buffer extends type() {
  /** @type {string[]} */
  #sb;

  constructor() {
    super();
    this.#sb = [""];
  }

  /** @param {string} x */
  append(x) {
    this.#sb[0] += x;
  }

  /** @param {Mark} m */
  mark(m) {
    this.#sb[0] += `{${m.label}}`;
  }

  /** @returns {string} */
  toString() {
    return this.#sb[0];
  }
}

export class Effect extends type() {
  /** @type {(c: Buffer) => void} */
  enactOn = /** @type {any} */ (undefined);
}

export class AppendParseEffect extends type(Effect) {
  /** @type {string} */
  #text;

  /** @param {string} text */
  constructor(text) {
    super();
    this.#text = text;
  }

  /** @returns {string} */
  get text() {
    return this.#text;
  }

  /** @param {Buffer} c */
  enactOn(c) {
    c.append(this.#text);
  }
}

export class EventParseEffect extends type(Effect) {
  /** @type {Mark} */
  #event;

  /** @param {Mark} event */
  constructor(event) {
    super();
    this.#event = event;
  }

  /** @returns {Mark} */
  get event() {
    return this.#event;
  }

  /** @param {Buffer} c */
  enactOn(c) {
    c.mark(this.#event);
  }
}

export class After extends type() {
  /** @type {readonly Effect[]} */
  #effects;
  /** @type {boolean} */
  #stateAfter;

  /**
   * @param {{ effects: readonly Effect[], stateAfter: boolean }} props
   * @returns {After}
   */
  static new(props) {
    return new After(props.effects, props.stateAfter);
  }

  /**
   * @param {readonly Effect[]} effects
   * @param {boolean} stateAfter
   */
  constructor(effects, stateAfter) {
    super();
    this.#effects = effects;
    this.#stateAfter = stateAfter;
  }

  /** @returns {readonly Effect[]} */
  get effects() {
    return this.#effects;
  }

  /** @returns {boolean} */
  get stateAfter() {
    return this.#stateAfter;
  }
}

export class Propagator extends type() {
  /** @type {string} */
  #quote;

  /** @param {string} quote */
  constructor(quote) {
    super();
    this.#quote = quote;
  }

  /** @returns {string} */
  get quote() {
    return this.#quote;
  }
}

export class Escaper extends type() {
  /** @type {(x: string) => string} */
  applyString = /** @type {any} */ (undefined);
}

/** @type {TextEscaper} */
let TextEscaper$instance;

export class TextEscaper extends type(Escaper) {
  /** @returns {TextEscaper} */
  static get instance() {
    return TextEscaper$instance;
  }

  /**
   * @param {string} x
   * @returns {string}
   */
  applyString(x) {
    return `[${x}]`;
  }
}

/** @type {QuoteEscaper} */
let QuoteEscaper$instance;

export class QuoteEscaper extends type(Escaper) {
  /** @returns {QuoteEscaper} */
  static get instance() {
    return QuoteEscaper$instance;
  }

  /**
   * @param {string} x
   * @returns {string}
   */
  applyString(x) {
    return `(${x})`;
  }
}

export class Both extends type(Escaper) {
  /** @type {Escaper} */
  #first;
  /** @type {Escaper} */
  #second;

  /**
   * @param {{ first: Escaper, second: Escaper }} props
   * @returns {Both}
   */
  static new(props) {
    return new Both(props.first, props.second);
  }

  /**
   * @param {Escaper} first
   * @param {Escaper} second
   */
  constructor(first, second) {
    super();
    this.#first = first;
    this.#second = second;
  }

  /** @returns {Escaper} */
  get first() {
    return this.#first;
  }

  /** @returns {Escaper} */
  get second() {
    return this.#second;
  }

  /**
   * @param {string} x
   * @returns {string}
   */
  applyString(x) {
    return this.#second.applyString(this.#first.applyString(x));
  }
}

export class Picker extends type() {
  /** @type {boolean} */
  #strict;

  /** @param {boolean} strict */
  constructor(strict) {
    super();
    this.#strict = strict;
  }

  /** @returns {boolean} */
  get strict() {
    return this.#strict;
  }

  /**
   * @param {boolean} inQuote
   * @param {(arg0: string, arg1: boolean) => void} callout
   * @returns {Escaper}
   */
  escaperFor(inQuote, callout) {
    if (this.#strict && inQuote) {
      callout("a value in quotes", false);
    }
    if (inQuote) {
      return new Both(TextEscaper.instance, QuoteEscaper.instance);
    } else {
      return TextEscaper.instance;
    }
  }
}

export class ContextualAutoescapingAccumulator extends type() {
  /** @type {string} */
  accumulated = /** @type {any} */ (undefined);
}

export class Quoting extends type(ContextualAutoescapingAccumulator) {
  /** @type {boolean} */
  #inQuote;
  /** @type {Buffer} */
  #collector;

  constructor() {
    super();
    this.#inQuote = false;
    this.#collector = new Buffer();
  }

  /** @returns {Buffer} */
  static newCollector() {
    return new Buffer();
  }

  /** @returns {boolean} */
  static initialState() {
    return false;
  }

  /** @returns {Propagator} */
  static propagator() {
    return new Propagator("'");
  }

  /** @returns {Picker} */
  static picker() {
    return new Picker(false);
  }

  /** @returns {(arg0: boolean, arg1: boolean) => boolean} */
  static sameStatePredicate() {
    return (a, b) => a === b;
  }

  /**
   * @param {boolean} a
   * @param {boolean} b
   * @param {(arg0: string, arg1: boolean) => void} callout
   * @returns {boolean}
   */
  static mergeStates(a, b, callout) {
    if (a !== b) {
      callout("branches end in different contexts", true);
    }
    return a;
  }

  /**
   * @param {boolean} inQuote
   * @param {(arg0: string, arg1: boolean) => void} callout
   */
  static checkEndState(inQuote, callout) {
    if (inQuote) {
      callout("ends inside quotes", true);
    }
  }

  /**
   * @param {Buffer} c
   * @returns {string}
   */
  static fromCollector(c) {
    return c.toString();
  }

  /**
   * @param {Mark} m
   * @param {Buffer} c
   */
  static enact(m, c) {
    c.mark(m);
  }

  /** @param {string} x */
  appendSafe(x) {
    const after = propagateOver(Quoting.propagator(), this.#inQuote, x, Quoting.sameStatePredicate(), ignore);
    this.#inQuote = after.stateAfter;
    {
      /** @type {readonly Effect[]} */
      const list = after.effects;
      const length = list.length;
      let i = 0;
      while (i < length) {
        const e = listedGet(/** @type {Effect[]} */ (list), i);
        i = (i + 1) | 0;
        e.enactOn(this.#collector);
      }
    }
  }

  /** @param {string} x */
  append(x) {
    const escaped = Quoting.picker().escaperFor(this.#inQuote, ignore).applyString(x);
    this.#collector.append(escaped);
  }

  /** @returns {string} */
  get accumulated() {
    return this.#collector.toString();
  }
}

TextEscaper$instance = new TextEscaper();
QuoteEscaper$instance = new QuoteEscaper();
console.log(greet("Ann", "Bob"));
console.log(branch(true, "a"));
console.log(branch(false, "b"));
console.log(each(Object.freeze(["c", "d"])));
console.log(counted(5, "e"));
console.log(`${attribute(false, "f")} ${attribute(true, "f")}`);
console.log(opened(Object.freeze(["g", "h", "i"])));
