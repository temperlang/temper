using R = Roaring.Net.CRoaring;

namespace Connecteds
{
    public class Bitset
    {
        R::Roaring32Bitmap roaring = new R::Roaring32Bitmap();

        public Add(int i)
        {
            // Two's complement is fine here, providing unique values.
            roaring.Add(unchecked((uint)i));
        }

        public Contains(int i)
        {
            // Again, two's complement is fine here.
            roaring.Contains(unchecked((uint)i));
        }
    }
}
