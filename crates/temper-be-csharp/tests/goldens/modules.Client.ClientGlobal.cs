using Modules;

namespace Modules.Client;

public static class ClientGlobal
{
    public static int SumTwice(int n)
    {
        return ModulesGlobal.Sum(n) * 2;
    }
}
