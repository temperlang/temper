using System.Globalization;

namespace Classes;

internal class Theme
{
    private readonly string fg;
    private readonly string bg;
    private readonly int size;
    private readonly bool bold;

    public Theme(string fg, string bg, int? size = null, bool? bold = null)
    {
        int size2 = size ?? 12;
        bool bold2 = bold ?? false;
        this.fg = fg;
        this.bg = bg;
        this.size = size2;
        this.bold = bold2;
    }

    public string Fg
    {
        get
        {
            return this.fg;
        }
    }

    public string Bg
    {
        get
        {
            return this.bg;
        }
    }

    public int Size
    {
        get
        {
            return this.size;
        }
    }

    public bool Bold
    {
        get
        {
            return this.bold;
        }
    }

    public string Weight
    {
        get
        {
            if (this.bold)
            {
                return "bold";
            }
            else
            {
                return "plain";
            }
        }
    }

    public override string ToString()
    {
        return this.fg + "/" + this.bg + " " + this.size.ToString(CultureInfo.InvariantCulture) + " " + this.Weight;
    }
}
