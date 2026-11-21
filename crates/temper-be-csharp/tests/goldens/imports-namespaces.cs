using Namespaces.Shapes;
using System.Collections.Generic;
using System.Globalization;
using TemperLang.Core;

namespace Namespaces;

public static class NamespacesGlobal
{
    internal static int Bump(int shapes)
    {
        return shapes + 1;
    }

    static NamespacesGlobal()
    {
        ILoggingConsole console = Support.Logging.LoggingConsoleFactory.CreateConsole("Namespaces");
        console.Log(ShapesGlobal.Unit.ToString(CultureInfo.InvariantCulture));
        console.Log(ShapesGlobal.Twice(21).ToString(CultureInfo.InvariantCulture));
        Box<int> box = new Box<int>(3);
        IReadOnlyList<IShape> squares = Listed.CreateReadOnlyList<IShape>(new Square(4), new Square(ShapesGlobal.Unit), Square.Sized(5));
        {
            int length = squares.Count;
            int i = 0;
            while (i < length)
            {
                IShape s = squares[i];
                i = i + 1;
                console.Log("area " + s.Area().ToString(CultureInfo.InvariantCulture));
            }
        }
        IReadOnlyList<IShape> mixed = Listed.CreateReadOnlyList<IShape>(new Circle(2), new Square(2));
        {
            int length2 = mixed.Count;
            int i2 = 0;
            while (i2 < length2)
            {
                IShape s2 = mixed[i2];
                i2 = i2 + 1;
                {
                    if (s2 is Square)
                    {
                        console.Log("a square of " + ((Square)s2).Area().ToString(CultureInfo.InvariantCulture));
                    }
                    else
                    {
                        console.Log("something else of " + s2.Area().ToString(CultureInfo.InvariantCulture));
                    }
                }
            }
        }
        console.Log(Bump(box.Value).ToString(CultureInfo.InvariantCulture));
    }
}
