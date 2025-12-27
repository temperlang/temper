import {
    bubble, INT32_MAX, INT32_MIN, INT64_MAX, INT64_MIN, parseBigInt
} from "./core.js";

/**
 * Implements extension method String::fromCodePoint
 * @param {number} codePoint
 * @returns {string}
 */
export const stringFromCodePoint = (codePoint) => {
    denySurrogate(codePoint);
    return String.fromCodePoint(codePoint);
};

/**
 * Implements extension method String::fromCodePoints
 * @param {number[]} codePoints
 * @returns {string}
 */
export const stringFromCodePoints = (codePoints) => {
    for (const codePoint of codePoints) {
        denySurrogate(codePoint);
    }
    // TODO Append in batches if codePoints is long?
    return String.fromCodePoint(...codePoints);
};

/**
 * Implements extension method String::isEmpty
 * @param {string} s
 * @returns {boolean}
 */
export const stringIsEmpty = (s) => {
    return s === "";
};

/**
 * Implements extension method String::split
 * @param {string} s
 * @param {string | undefined} separator
 * @returns {string[]}
 */
export const stringSplit = (s, separator) => {
    return separator ? s.split(separator).map((s) => s) : Array.from(s);
};

/**
 * Implements extension method String::toFloat64
 * @param {string} s
 * @returns {number}
 */
export const stringToFloat64 = (s) => {
    // TODO Consider JSON.parse + bonus constants instead? Faster or not?
    if (!/^\s*-?(?:\d+(?:\.\d+)?(?:[eE][-+]?\d+)?|NaN|Infinity)\s*$/.test(s)) {
        bubble();
    }
    return Number(s);
};

/**
 * Implements extension method String::toInt32
 * @param {string} s
 * @param {number?} radix
 * @returns {number}
 */
export const stringToInt32 = (s, radix) => {
    // This currently maybe allocates for trim and then also for check.
    // TODO Avoid that with manual char checks? Arbitrary base makes regex harder.
    s = s.trim();
    radix = radix ?? 10;
    const result = parseInt(s, radix);
    // This check also catches nan.
    if (!(result >= INT32_MIN && result <= INT32_MAX)) {
        bubble();
    }
    const trimmed = s.slice(0, s.length - 1);
    if (parseInt(trimmed, radix) === result) {
        // Extraneous junk was ignored that we disallow.
        bubble();
    }
    return result;
};

/**
 * Implements extension method String::toInt64
 * @param {string} s
 * @param {number?} radix
 * @returns {number}
 */
export const stringToInt64 = (s, radix) => {
    const result = parseBigInt(s, radix);
    if (result < INT64_MIN || result > INT64_MAX) {
        bubble();
    }
    return result;
};

/**
 * @param {string} s
 * @param {number} begin
 * @param {number} end
 * @returns {number}
 */
export const stringCountBetween = (s, begin, end) => {
    let count = 0;
    for (let i = begin; i < end; ++i) {
        let cp = s.codePointAt(i);
        if (cp !== undefined) {
            count += 1;
            i += !!(cp >>> 16); // skip over trailing surrogate
        }
    }
    return count;
};

/**
 * @param {string} s
 * @param {number} i
 * @returns {number}
 */
export const stringGet = (s, i) => {
    const c = s.codePointAt(i);
    if (c === undefined) {
        throw new Error();
    }
    return c;
};

/**
 * @param {string} s
 * @param {number} begin
 * @param {number} end
 * @param {number} minCount
 * @returns {boolean}
 */
export const stringHasAtLeast = (s, begin, end, minCount) => {
    let { length } = s;
    begin = Math.min(begin, length);
    end = Math.max(begin, Math.min(end, length));
    let nUtf16 = end - begin;
    if (nUtf16 < minCount) { return false; }
    if (nUtf16 >= minCount * 2) { return true; }
    // Fall back to an early-outing version of countBetween.
    let count = 0;
    for (let i = begin; i < end; ++i) {
        let cp = s.codePointAt(i);
        if (cp !== undefined) {
            count += 1;
            i += !!(cp >>> 16); // skip over trailing surrogate
            if (count >= minCount) { return true; }
        }
    }
    return count >= minCount;
};

/**
 * @param {string} s
 * @param {number} i
 * @returns {number}
 */
export const stringNext = (s, i) => {
    let iNext = Math.min(s.length, i);
    let cp = s.codePointAt(i);
    if (cp !== undefined) {
        iNext += 1 + !!(cp >>> 16);
    }
    return iNext;
};

/**
 * @param {string} s
 * @param {number} i
 * @returns {number}
 */
export const stringPrev = (s, i) => {
    let iPrev = Math.min(s.length, i);
    if (iPrev) {
        iPrev -= 1;
        if (iPrev && s.codePointAt(iPrev - 1) >>> 16) {
            iPrev -= 1;
        }
    }
    return iPrev;
};

/**
 * @param {string} s
 * @param {number} i
 * @param {number} by
 * @returns {number}
 */
export const stringStep = (s, i, by) => {
    let step = by >= 0 ? stringNext : stringPrev;
    by = Math.abs(by);
    for (let j = 0; j < by; j += 1) {
        const iOld = i;
        i = step(s, i);
        if (i == iOld) {
            break;
        }
    }
    return i;
};

/**
 * @param {string} s
 * @param {(number) => void} f
 */
export const stringForEach = (s, f) => {
    let { length } = s;
    for (let i = 0; i < length; ++i) {
        let cp = s.codePointAt(i);
        f(cp);
        if (cp >>> 16) {
            ++i; // Skip both surrogates
        }
    }
};

export const stringIndexNone = -1;

/**
 * Casts a *StringIndexOption* to a *StringIndex*
 *
 * @param {number} i a string index or no string index
 * @returns {number} i if it's a valid StringIndex.
 */
export const requireStringIndex = (i) => {
    if (i >= 0) { return i; }
    throw new TypeError(`Expected StringIndex, not ${i}`);
};

/**
 * Casts a *StringIndexOption* to a *NoStringIndex*.
 * @param {number} i a string index or no string index
 * @returns {number} i if it's valid NoStringIndex.
 */
export const requireNoStringIndex = (i) => {
    if (i < 0) { return i; }
    throw new TypeError(`Expected NoStringIndex, not ${i}`);
};

/**
 * @param {string[]} s
 * @param {number} c
 */
export const stringBuilderAppendCodePoint = (s, c) => {
    denySurrogate(c);
    s[0] += String.fromCodePoint(c);
}

/**
 * @param {number} c
 */
const denySurrogate = (c) => {
    if (c >= 0xD800 && c <= 0xDFFF) {
        throw new RangeError(`Invalid Unicode scalar value ${c}`)
    }
}
