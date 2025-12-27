using R = RootNamespaceSpot;
using System.Runtime.CompilerServices;

namespace RootNamespaceSpot.SupportNamespaceSpot
{
    /// <summary>
    /// Default to initializing all modules for a library when no top is given.
    /// </summary>
    public static class GlobalNameSpot
    {
        static GlobalNameSpot()
        {
            RuntimeHelpers.RunClassConstructor(typeof(R::GlobalNameSpot).TypeHandle);
        }
    }
}
