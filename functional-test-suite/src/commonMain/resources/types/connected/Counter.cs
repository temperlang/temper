namespace Work
{
    /// Temper's connected Counter class, with the members Temper would generate.
    public class Counter
    {
        int total;

        public void Add(int i)
        {
            total += i;
        }

        public int Total()
        {
            return total;
        }
    }
}
