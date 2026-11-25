namespace CountFailures;

public static class CountFailuresGlobal
{
    /// <exception cref="BadCount">Where it fails with BadCount.</exception>
    public static int ParseCount(string text)
    {
        {
            if (text == "zero")
            {
                return 0;
            }
            else if (text == "two")
            {
                return 2;
            }
            else
            {
                throw new BadCount(text);
            }
        }
    }
}
