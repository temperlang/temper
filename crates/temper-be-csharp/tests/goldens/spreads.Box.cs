using System.Globalization;

namespace Spreads;

internal class Box : ISized
{
    private readonly int w;
    private readonly int h;
    private readonly string? label;

    public Box(int w, int h, string? label = null)
    {
        this.w = w;
        this.h = h;
        this.label = label;
    }

    public int W
    {
        get
        {
            return this.w;
        }
    }

    public int H
    {
        get
        {
            return this.h;
        }
    }

    public string? Label
    {
        get
        {
            return this.label;
        }
    }

    public int Area()
    {
        return this.w * this.h;
    }

    public override string ToString()
    {
        string? subject1 = this.label;
        return string.Create(CultureInfo.InvariantCulture, $"{(subject1 == null ? "box" : subject1)} {this.w}x{this.h}");
    }
}
