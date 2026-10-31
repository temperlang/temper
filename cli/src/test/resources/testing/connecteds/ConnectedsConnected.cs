using R = Roaring.Net.CRoaring;

namespace Connecteds
{
    public class Bitset
    {
        R::Roaring32Bitmap roaring = new R::Roaring32Bitmap();

        public void Add(int i)
        {
            // Two's complement is fine here, providing unique values.
            roaring.Add(unchecked((uint)i));
        }

        public bool Contains(int i)
        {
            // Again, two's complement is fine here.
            return roaring.Contains(unchecked((uint)i));
        }
    }
}
