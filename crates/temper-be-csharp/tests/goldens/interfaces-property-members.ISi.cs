namespace PropertyMembers;

public interface ISi<T>
    where T : class, II
{
    T Thing { get; }
}
