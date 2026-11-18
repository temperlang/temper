namespace PropertyMembers;

internal interface ILabeled<L, T> : IHolder<T>
{
    string Label(L l);
}
