package lang.temper.common

const val C_AMP = '&'.code
const val C_BQ = '`'.code
const val C_BS = '\\'.code
const val C_CARET = '^'.code
const val C_COLON = ':'.code
const val C_COMMA = ','.code
const val C_CR = '\r'.code
const val C_DOL = '$'.code
const val C_DOT = '.'.code
const val C_DQ = '"'.code
const val C_EMOJI_PRESENTATION_SELECTOR = 0xFE0F
const val C_EXCL = '!'.code
const val C_GT = '>'.code
const val C_HASH = '#'.code
const val C_LEFT_CURLY = '{'.code
const val C_LEFT_ROUND = '('.code
const val C_LEFT_SQUARE = '['.code
const val C_LF = '\n'.code
const val C_LT = '<'.code
const val C_LOWER_A = 'a'.code
const val C_LOWER_E = 'e'.code
const val C_LOWER_Z = 'z'.code
const val C_NINE = '9'.code
const val C_PCT = '%'.code
const val C_PIPE = '|'.code
const val C_PLUS = '+'.code
const val C_RIGHT_CURLY = '}'.code
const val C_RIGHT_ROUND = ')'.code
const val C_RIGHT_SQUARE = ']'.code
const val C_QUEST = '?'.code
const val C_SLASH = '/'.code
const val C_SPACE = ' '.code
const val C_SQ = '\''.code
const val C_STAR = '*'.code
const val C_TAB = '\t'.code
const val C_TILDE = '~'.code
const val C_UPPER_A = 'A'.code
const val C_UPPER_Z = 'Z'.code
const val C_ZERO = '0'.code
const val C_DASH = '-'.code

const val C_MIN_SURROGATE = 0xD800
const val C_MAX_SURROGATE = 0xDFFF
const val C_MAX_CODEPOINT = 0x10FFFF

const val MIN_PRINTABLE_ASCII = 0x20
const val MAX_PRINTABLE_ASCII = 0x7e
const val MAX_ASCII = 0xFF

const val N_HEX_PER_BYTE = 2
const val N_HEX_PER_UTF16 = 4

const val HEX_RADIX = 16
const val DECIMAL_RADIX = 10
const val OCTAL_RADIX = 8
const val BINARY_RADIX = 2
const val VALUE_HEX_NUMERAL_A = 10
const val BITS_PER_HEX_DIGIT = 4
const val NUM_HEX_IN_U_ESCAPE = 4
const val MAX_HEX_IN_CP = 8 // TODO Or limit to 6?
