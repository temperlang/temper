using System.Globalization;

namespace Classes;

internal class Theme
{
    private readonly string fg;
    private readonly string bg;
    private readonly int size;
    private readonly bool bold;
    private readonly string? note;

    public Theme(string fg, string bg, int size, bool bold, string? note = null)
    {
        this.fg = fg;
        this.bg = bg;
        this.size = size;
        this.bold = bold;
        this.note = note;
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

    public string? Note
    {
        get
        {
            return this.note;
        }
    }

    public string Weight()
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

    public override string ToString()
    {
        string @string1 = this.fg + "/" + this.bg + " " + this.size.ToString(CultureInfo.InvariantCulture) + " " + this.Weight();
        string? subject1 = this.note;
        return @string1 + (subject1 == null ? "" : subject1);
    }
}
