using R = Roaring.Net.CRoaring;

namespace Connecteds
{
    static class ConnectedsConnected
    {
        internal static object NewBitsetConnected()
        {
            return new R::Roaring32Bitmap();
        }

        internal static void BitsetAdd(object bitset, int i)
        {
            // Two's complement is fine here, providing unique values.
            ((R::Roaring32Bitmap)bitset).Add(unchecked((uint)i));
        }

        internal static bool BitsetContains(object bitset, int i)
        {
            // Two's complement is fine here, providing unique values.
            return ((R::Roaring32Bitmap)bitset).Contains(unchecked((uint)i));
        }
    }
}
