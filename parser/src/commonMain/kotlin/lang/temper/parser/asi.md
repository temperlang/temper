# Table of Bracket ASI

Whether to insert a semicolon.

When *Position* is "Before `{`", *Semicolon* is *Y* when we should insert a semicolon because the last token can act as only the kinds of operators described by the other column.

When *Position* is "After `{`", *Semicolon*
is *Y* when we should insert a semicolon because the next token can act as only the kinds of operators described by the other column.

| Position   | Prefix | Infix&dagger; | Postfix | Semicolon   |
|------------|--------|---------------|---------|-------------|
| Before `{` | N      | N             | N       | Y           |
| Before `{` | N      | N             | Y       | Y           |
| Before `{` | N      | Y             | N       | N           |
| Before `{` | N      | Y             | Y       | Y           |
| Before `{` | Y      | N             | N       | N           |
| Before `{` | Y      | N             | Y       | Y           |
| Before `{` | Y      | Y             | N       | N           |
| Before `{` | Y      | Y             | Y       | Y           |
| After  `}` | N      | N             | N       | Y           |
| After  `}` | N      | N             | Y       | N           |
| After  `}` | N      | Y             | N       | N           |
| After  `}` | N      | Y             | Y       | N           |
| After  `}` | Y      | N             | N       | Y           |
| After  `}` | Y      | N             | Y       | Y           |
| After  `}` | Y      | Y             | N       | Y           |
| After  `}` | Y      | Y             | Y       | Y           |

&dagger; or **Separator**

----

before `{` case: semicolon = postfix || !(prefix || infix)
after `}` case: semicolon = prefix || !(infix || postfix)
