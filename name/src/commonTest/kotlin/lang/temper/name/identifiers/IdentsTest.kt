@file:Suppress("SpellCheckingInspection")

package lang.temper.name.identifiers

import lang.temper.name.identifiers.IdentStyle.Camel
import lang.temper.name.identifiers.IdentStyle.Dash
import lang.temper.name.identifiers.IdentStyle.Human
import lang.temper.name.identifiers.IdentStyle.LoudDash
import lang.temper.name.identifiers.IdentStyle.LoudSnake
import lang.temper.name.identifiers.IdentStyle.Pascal
import lang.temper.name.identifiers.IdentStyle.Snake
import lang.temper.name.identifiers.IdentStyle.StrictCamel
import lang.temper.name.identifiers.IdentStyle.StrictPascal
import lang.temper.name.identifiers.Tok.Caseless
import lang.temper.name.identifiers.Tok.Digit
import lang.temper.name.identifiers.Tok.Lower
import lang.temper.name.identifiers.Tok.Upper
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentsTest {

    @Test
    fun testCamelCaseToWordsSimple() {
        assertEquals(
            listOf(
                Lower.seg("foo"),
                Lower.seg("Bar"),
            ),
            Camel.split("fooBar"),
        )
    }

    @Test
    fun testCamelCaseToWordsPascalCase() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Lower.seg("Bar"),
            ),
            Camel.split("FooBar"),
        )
    }

    @Test
    fun testCamelCaseToWordsAcronym() {
        assertEquals(
            listOf(
                Lower.seg("foo"),
                Upper.seg("FUBAR"),
                Lower.seg("Bar"),
            ),
            Camel.split("fooFUBARBar"),
        )
    }

    @Test
    fun testCamelCaseToWordsDigitsAtEnd() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Lower.seg("Bar"),
                Digit.seg("3"),
            ),
            Camel.split("FooBar3"),
        )
    }

    @Test
    fun testCamelCaseToWordsDigitsInMiddle() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Digit.seg("33"),
                Lower.seg("Bar"),
            ),
            Camel.split("Foo33Bar"),
        )
    }

    @Test
    fun testCamelCaseToWordsDigitsLeading() {
        assertEquals(
            listOf(
                Digit.seg("44"),
                Lower.seg("foo"),
                Lower.seg("Bar"),
            ),
            Camel.split("44fooBar"),
        )
    }

    @Test
    fun testCamelCaseToWordsCaselessMiddle() {
        assertEquals(
            listOf(
                Lower.seg("Welcome"),
                Lower.seg("To"),
                Caseless.seg("京都"),
                Lower.seg("City"),
            ),
            Camel.split("WelcomeTo京都City"),
        )
    }

    @Test
    fun testCamelCaseToWordsCaselessPair() {
        assertEquals(
            listOf(
                Lower.seg("Welcome"),
                Lower.seg("To"),
                Caseless.seg("京都"),
                Caseless.seg("市"),
            ),
            Camel.split("WelcomeTo京都・市"),
        )
    }

    @Test
    fun testCamelCaseToWordsCaselessLeading() {
        assertEquals(
            listOf(
                Caseless.seg("京都"),
                Lower.seg("city"),
            ),
            Camel.split("京都city"),
        )
    }

    @Test
    fun testCamelCaseToWordsCaselessTail() {
        assertEquals(
            listOf(
                Lower.seg("city"),
                Lower.seg("Of"),
                Caseless.seg("京都"),
            ),
            Camel.split("cityOf京都"),
        )
    }

    @Test
    fun testSnakeCaseToWordsSimple() {
        assertEquals(
            listOf(
                Lower.seg("foo"),
                Lower.seg("bar"),
            ),
            Snake.split("foo_bar"),
        )
    }

    @Test
    fun testSnakeCaseToWordsAcronym() {
        assertEquals(
            listOf(
                Lower.seg("foo"),
                Upper.seg("FUBAR"),
                Lower.seg("bar"),
            ),
            Snake.split("foo_FUBAR_bar"),
        )
    }

    @Test
    fun testSnakeCaseToWordsDigitsAtEnd1() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Lower.seg("Bar"),
                Digit.seg("3"),
            ),
            Snake.split("Foo_Bar_3"),
        )
    }

    @Test
    fun testSnakeCaseToWordsDigitsAtEnd2() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Lower.seg("Bar"),
                Digit.seg("3"),
            ),
            Snake.split("Foo_Bar3"),
        )
    }

    @Test
    fun testSnakeCaseToWordsDigitsInMiddle1() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Digit.seg("33"),
                Lower.seg("Bar"),
            ),
            Snake.split("Foo_33_Bar"),
        )
    }

    @Test
    fun testSnakeCaseToWordsDigitsInMiddle2() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Digit.seg("33"),
                Lower.seg("Bar"),
            ),
            Snake.split("Foo33Bar"),
        )
    }

    @Test
    fun testSnakeCaseToWordsDigitsLeading() {
        assertEquals(
            listOf(
                Digit.seg("44"),
                Lower.seg("foo"),
                Lower.seg("bar"),
            ),
            Snake.split("44_foo_bar"),
        )
    }

    @Test
    fun testSnakeCaseToWordsCaselessMiddle() {
        assertEquals(
            listOf(
                Lower.seg("welcome"),
                Lower.seg("to"),
                Caseless.seg("京都"),
                Lower.seg("city"),
            ),
            Snake.split("welcome_to_京都city"),
        )
    }

    @Test
    fun testSnakeCaseToWordsCaselessLeading() {
        assertEquals(
            listOf(
                Caseless.seg("京都"),
                Lower.seg("city"),
            ),
            Snake.split("京都_city"),
        )
    }

    @Test
    fun testSnakeCaseToWordsCaselessTail() {
        assertEquals(
            listOf(
                Lower.seg("city"),
                Lower.seg("of"),
                Caseless.seg("京都"),
            ),
            Snake.split("city_of_京都"),
        )
    }

    @Test
    fun testHumanToWordsSimple() {
        assertEquals(
            listOf(
                Lower.seg("foo"),
                Lower.seg("Bar"),
            ),
            Human.split("foo Bar"),
        )
    }

    @Test
    fun testHumanToWordsPascalCase() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Lower.seg("Bar"),
            ),
            Human.split("Foo Bar"),
        )
    }

    @Test
    fun testHumanToWordsAcronym() {
        assertEquals(
            listOf(
                Lower.seg("foo"),
                Upper.seg("FUBAR"),
                Lower.seg("Bar"),
            ),
            Human.split("foo FUBAR Bar"),
        )
    }

    @Test
    fun testHumanToWordsDigitsAtEnd() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Lower.seg("Bar"),
                Digit.seg("3"),
            ),
            Human.split("Foo Bar 3"),
        )
    }

    @Test
    fun testHumanToWordsDigitsInMiddle() {
        assertEquals(
            listOf(
                Lower.seg("Foo"),
                Digit.seg("33"),
                Lower.seg("Bar"),
            ),
            Human.split("Foo 33 Bar"),
        )
    }

    @Test
    fun testHumanToWordsDigitsLeading() {
        assertEquals(
            listOf(
                Digit.seg("44"),
                Lower.seg("foo"),
                Lower.seg("Bar"),
            ),
            Human.split("44 foo Bar"),
        )
    }

    @Test
    fun testHumanToWordsCaselessMiddle() {
        assertEquals(
            listOf(
                Lower.seg("Welcome"),
                Lower.seg("To"),
                Caseless.seg("京都"),
                Lower.seg("City"),
            ),
            Human.split("Welcome To 京都 City"),
        )
    }

    @Test
    fun testHumanToWordsCaselessPair() {
        assertEquals(
            listOf(
                Lower.seg("Welcome"),
                Lower.seg("To"),
                Caseless.seg("京都"),
                Caseless.seg("市"),
            ),
            Human.split("Welcome To 京都・市"),
        )
    }

    @Test
    fun testHumanToWordsCaselessLeading() {
        assertEquals(
            listOf(
                Caseless.seg("京都"),
                Lower.seg("city"),
            ),
            Human.split("京都 city"),
        )
    }

    @Test
    fun testHumanToWordsCaselessTail() {
        assertEquals(
            listOf(
                Lower.seg("city"),
                Lower.seg("of"),
                Caseless.seg("京都"),
            ),
            Human.split("city of 京都"),
        )
    }

    @Test
    fun testWordsToCamelCaseSimple() {
        assertEquals(
            "fooBar",
            Camel.join(
                listOf(
                    Lower.seg("foo"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCasePascalCase() {
        assertEquals(
            "fooBar",
            Camel.join(
                listOf(
                    Lower.seg("Foo"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseAcronym() {
        assertEquals(
            "fooFubarBar",
            Camel.join(
                listOf(
                    Lower.seg("foo"),
                    Upper.seg("FUBAR"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseDigitsAtEnd() {
        assertEquals(
            "fooBar3",
            Camel.join(
                listOf(
                    Lower.seg("Foo"),
                    Lower.seg("Bar"),
                    Digit.seg("3"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseDigitsInMiddle() {
        assertEquals(
            "foo33_bar",
            Camel.join(
                listOf(
                    Lower.seg("Foo"),
                    Digit.seg("33"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseDigitsLeading() {
        assertEquals(
            "_44_fooBar",
            Camel.join(
                listOf(
                    Digit.seg("44"),
                    Lower.seg("foo"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseCaselessMiddle() {
        assertEquals(
            "welcomeTo京都City",
            Camel.join(
                listOf(
                    Lower.seg("Welcome"),
                    Lower.seg("To"),
                    Caseless.seg("京都"),
                    Lower.seg("City"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseCaselessPair() {
        assertEquals(
            "welcomeTo京都·市",
            Camel.join(
                listOf(
                    Lower.seg("Welcome"),
                    Lower.seg("To"),
                    Caseless.seg("京都"),
                    Caseless.seg("市"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseCaselessLeading() {
        assertEquals(
            "京都City",
            Camel.join(
                listOf(
                    Caseless.seg("京都"),
                    Lower.seg("city"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToCamelCaseCaselessTail() {
        assertEquals(
            "cityOf京都",
            Camel.join(
                listOf(
                    Lower.seg("city"),
                    Lower.seg("Of"),
                    Caseless.seg("京都"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseSimple() {
        assertEquals(
            "foo_bar",
            Snake.join(
                listOf(
                    Lower.seg("foo"),
                    Lower.seg("bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseAcronym() {
        assertEquals(
            "foo_fubar_bar",
            Snake.join(
                listOf(
                    Lower.seg("foo"),
                    Upper.seg("FUBAR"),
                    Lower.seg("bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseDigitsAtEnd1() {
        assertEquals(
            "foo_bar3",
            Snake.join(
                listOf(
                    Lower.seg("Foo"),
                    Lower.seg("Bar"),
                    Digit.seg("3"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseDigitsAtEnd2() {
        assertEquals(
            "foo_bar3",
            Snake.join(
                listOf(
                    Lower.seg("Foo"),
                    Lower.seg("Bar"),
                    Digit.seg("3"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseDigitsInMiddle1() {
        assertEquals(
            "foo33_bar",
            Snake.join(
                listOf(
                    Lower.seg("Foo"),
                    Digit.seg("33"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseDigitsInMiddle2() {
        assertEquals(
            "foo33_bar",
            Snake.join(
                listOf(
                    Lower.seg("Foo"),
                    Digit.seg("33"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseDigitsLeading() {
        assertEquals(
            "_44_foo_bar",
            Snake.join(
                listOf(
                    Digit.seg("44"),
                    Lower.seg("foo"),
                    Lower.seg("bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseCaselessMiddle() {
        assertEquals(
            "welcome_to_京都_city",
            Snake.join(
                listOf(
                    Lower.seg("welcome"),
                    Lower.seg("to"),
                    Caseless.seg("京都"),
                    Lower.seg("city"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseCaselessLeading() {
        assertEquals(
            "京都_city",
            Snake.join(
                listOf(
                    Caseless.seg("京都"),
                    Lower.seg("city"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToSnakeCaseCaselessTail() {
        assertEquals(
            "city_of_京都",
            Snake.join(
                listOf(
                    Lower.seg("city"),
                    Lower.seg("of"),
                    Caseless.seg("京都"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanSimple() {
        assertEquals(
            "foo Bar",
            Human.join(
                listOf(
                    Lower.seg("foo"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanPascalCase() {
        assertEquals(
            "Foo Bar",
            Human.join(
                listOf(
                    Lower.seg("Foo"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanAcronym() {
        assertEquals(
            "foo FUBAR Bar",
            Human.join(
                listOf(
                    Lower.seg("foo"),
                    Upper.seg("FUBAR"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanDigitsAtEnd() {
        assertEquals(
            "Foo Bar 3",
            Human.join(
                listOf(
                    Lower.seg("Foo"),
                    Lower.seg("Bar"),
                    Digit.seg("3"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanDigitsInMiddle() {
        assertEquals(
            "Foo 33 Bar",
            Human.join(
                listOf(
                    Lower.seg("Foo"),
                    Digit.seg("33"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanDigitsLeading() {
        assertEquals(
            "44 foo Bar",
            Human.join(
                listOf(
                    Digit.seg("44"),
                    Lower.seg("foo"),
                    Lower.seg("Bar"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanCaselessMiddle() {
        assertEquals(
            "Welcome To 京都 City",
            Human.join(
                listOf(
                    Lower.seg("Welcome"),
                    Lower.seg("To"),
                    Caseless.seg("京都"),
                    Lower.seg("City"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanCaselessPair() {
        assertEquals(
            "Welcome To 京都·市",
            Human.join(
                listOf(
                    Lower.seg("Welcome"),
                    Lower.seg("To"),
                    Caseless.seg("京都"),
                    Caseless.seg("市"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanCaselessLeading() {
        assertEquals(
            "京都 city",
            Human.join(
                listOf(
                    Caseless.seg("京都"),
                    Lower.seg("city"),
                ),
            ),
        )
    }

    @Test
    fun testWordsToHumanCaselessTail() {
        assertEquals(
            "city Of 京都",
            Human.join(
                listOf(
                    Lower.seg("city"),
                    Lower.seg("Of"),
                    Caseless.seg("京都"),
                ),
            ),
        )
    }

    @Test
    fun testCamelCaseToDashSimple() {
        assertEquals("foo-bar-qux", Camel.convertTo(Dash, "fooBarQux"))
    }

    @Test
    fun testCamelCaseToLoudDashSimple() {
        assertEquals("FOO-BAR-QUX", Camel.convertTo(LoudDash, "fooBarQux"))
    }

    @Test
    fun testCamelCaseToDashAcronym() {
        assertEquals("foo-fubar-bar", Camel.convertTo(Dash, "fooFUBARBar"))
    }

    @Test
    fun testCamelCaseToDashDigits() {
        assertEquals("foo-bar44", Camel.convertTo(Dash, "fooBar44"))
    }

    @Test
    fun testCamelCaseToDashDigitsMiddle() {
        assertEquals("foo44-bar", Camel.convertTo(Dash, "foo44-bar"))
    }

    @Test
    fun testCamelCaseToSnakeSimple() {
        assertEquals("foo_bar_qux", Camel.convertTo(Snake, "fooBarQux"))
    }

    @Test
    fun testCamelCaseToSnakeAcronym() {
        assertEquals("foo_fubar_bar", Camel.convertTo(Snake, "fooFUBARBar"))
    }

    @Test
    fun testCamelCaseToSnakeDigits() {
        assertEquals("foo_bar44", Camel.convertTo(Snake, "fooBar44"))
    }

    @Test
    fun testCamelCaseToSnakeDigitsMiddle() {
        assertEquals("foo44_bar", Camel.convertTo(Snake, "foo44bar"))
    }

    @Test
    fun testCamelCaseToLoudSnakeSimple() {
        assertEquals("FOO_BAR_QUX", Camel.convertTo(LoudSnake, "fooBarQux"))
    }

    @Test
    fun testCamelCaseToLoudSnakeAcronym() {
        assertEquals("FOO_FUBAR_BAR", Camel.convertTo(LoudSnake, "fooFUBARBar"))
    }

    @Test
    fun testCamelCaseToLoudSnakeDigits() {
        assertEquals("FOO_BAR44", Camel.convertTo(LoudSnake, "fooBar44"))
    }

    @Test
    fun testCamelCaseToLoudSnakeDigitsMiddle() {
        assertEquals("FOO44_BAR", Camel.convertTo(LoudSnake, "foo44-bar"))
    }

    @Test
    fun testDashCaseToCamelSimple() {
        assertEquals("fooBarQux", Dash.convertTo(Camel, "foo-bar-qux"))
    }

    @Test
    fun testDashCaseToCamelAcronym() {
        assertEquals("fooFubarBar", Dash.convertTo(Camel, "foo-FUBAR-bar"))
    }

    @Test
    fun testDashCaseToCamelDigits() {
        assertEquals("fooBar44", Dash.convertTo(Camel, "foo-bar44"))
    }

    @Test
    fun testDashCaseToCamelDigitsMiddle() {
        assertEquals("foo44_bar", Dash.convertTo(Camel, "foo44bar"))
    }

    @Test
    fun testDashCaseToPascalToHumanSimple() {
        assertEquals("Foo Bar Qux", Pascal.convertTo(Human, Dash.convertTo(Pascal, "foo-bar-qux")))
    }

    @Test
    fun testHumanCaseToDashSimple() {
        assertEquals("foo-bar-qux", Human.convertTo(Dash, "Foo Bar Qux"))
    }

    @Test
    fun testSnakeCaseToCamelSimple() {
        assertEquals("fooBarQux", Snake.convertTo(Camel, "foo_bar_qux"))
    }

    @Test
    fun testSnakeCaseToCamelAcronym() {
        assertEquals("fooFubarBar", Snake.convertTo(Camel, "foo_FUBAR_bar"))
    }

    @Test
    fun testSnakeCaseToCamelDigits() {
        assertEquals("fooBar44", Snake.convertTo(Camel, "foo_bar44"))
    }

    @Test
    fun testSnakeCaseToCamelDigitsMiddle() {
        assertEquals("foo44_bar", Snake.convertTo(Camel, "foo44bar"))
    }

    @Test
    fun testSnakeCaseToStrictCamelDigitsMiddle() {
        assertEquals("foo44bar", Snake.convertTo(StrictCamel, "foo44bar"))
    }

    @Test
    fun testDashCaseToSnakeSimple() {
        assertEquals("foo_bar_qux", Dash.convertTo(Snake, "foo-bar-qux"))
    }

    @Test
    fun testDashCaseToSnakeAcronym() {
        assertEquals("foo_fubar_bar", Dash.convertTo(Snake, "foo-FUBAR-bar"))
    }

    @Test
    fun testDashCaseToSnakeDigits() {
        assertEquals("foo_bar44", Dash.convertTo(Snake, "foo-bar44"))
    }

    @Test
    fun testDashCaseToSnakeDigitsMiddle() {
        assertEquals("foo44_bar", Dash.convertTo(Snake, "foo-44-bar"))
    }

    @Test
    fun testDashCaseToPascalSimple() {
        assertEquals("FooBarQux", Dash.convertTo(Pascal, "foo-bar-qux"))
    }

    @Test
    fun testDashCaseToPascalAcronym() {
        assertEquals("FooFubarBar", Dash.convertTo(Pascal, "foo-FUBAR-bar"))
    }

    @Test
    fun testDashCaseToPascalCapitalized() {
        assertEquals("FooBarQux", Dash.convertTo(Pascal, "Foo-bar-qux"))
    }

    @Test
    fun testDashCaseToPascalDigits() {
        assertEquals("FooBar44", Dash.convertTo(Pascal, "foo-bar44"))
    }

    @Test
    fun testDashCaseToPascalDigitsMiddle() {
        assertEquals("Foo44_Bar", Dash.convertTo(Pascal, "foo44bar"))
    }

    @Test
    fun testSnakeCaseToPascalSimple() {
        assertEquals("FooBarQux", Snake.convertTo(Pascal, "foo_bar_qux"))
    }

    @Test
    fun testSnakeCaseToPascalAcronym() {
        assertEquals("FooFubarBar", Snake.convertTo(Pascal, "foo_FUBAR_bar"))
    }

    @Test
    fun testSnakeCaseToPascalDigits() {
        assertEquals("FooBar44", Snake.convertTo(Pascal, "foo_bar44"))
    }

    @Test
    fun testSnakeCaseToPascalDigitsMiddle() {
        assertEquals("Foo44_Bar", Snake.convertTo(Pascal, "foo44bar"))
    }

    @Test
    fun testSnakeCaseToStrictPascalDigitsMiddle() {
        assertEquals("Foo44Bar", Snake.convertTo(StrictPascal, "foo44bar"))
    }
}
