using System.Globalization;
using TemperLang.Core;

namespace PrivateMethod;

internal class Money
{
    private readonly int cents;
    private int spent;

    public Money(int cents)
    {
        this.cents = cents;
        this.spent = 0;
    }

    private int Whole()
    {
        return Core.Div(this.cents, 100);
    }

    private int Rounded
    {
        get
        {
            return Core.Div(this.cents + 50, 100);
        }
    }

    public Money Plus(Money other)
    {
        return new Money(this.cents + other.cents);
    }

    public bool SameWhole(Money other)
    {
        return this.Whole() == other.Whole();
    }

    public bool SameRounded(Money other)
    {
        return this.Rounded == other.Rounded;
    }

    public void SpendFrom(Money other)
    {
        other.spent = other.spent + 1;
    }

    public int TimesSpent
    {
        get
        {
            return this.spent;
        }
    }

    public override string ToString()
    {
        return this.cents.ToString(CultureInfo.InvariantCulture) + " cents";
    }
}
