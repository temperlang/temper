Notes on these tests:

 * A dot `.` indicates an implicit delimiter
 * A colon `:` indicates an explicit delimiter
 * A bang `!` indicates an expected failure to output a delimiter

| Name       | Camel          | Pascal         | StrictCamel   | StrictPascal  | Dash            | Snake           | LoudSnake       |
|------------|----------------|----------------|---------------|---------------|-----------------|-----------------|-----------------|
| simple     | `foo.Bar.Qux`  | `Foo.Bar.Qux`  | `foo.Bar.Qux` | `Foo.Bar.Qux` | `foo:-bar:-qux` | `foo:_bar:_qux` | `FOO:_BAR:_QUX` |
| digits     | `foo.123:_bar` | `Foo.123:_Bar` | `foo.123.bar` | `Foo.123.Bar` | `foo.123:-bar`  | `foo.123:_bar`  | `FOO.123:_BAR`  |
| onlyDigits | `:_123:_456`   | `:_123:_456`   | `123!456`     | `123!456`     | `:_123:-456`    | `:_123:_456`    | `:_123:_456`    |
| characters | `foo.计算机.Qux`  | `Foo.计算机.Qux`  | `foo.计算机.Qux` | `Foo.计算机.Qux` | `foo:-计算机:-qux` | `foo:_计算机:_qux` | `FOO:_计算机:_QUX` |
