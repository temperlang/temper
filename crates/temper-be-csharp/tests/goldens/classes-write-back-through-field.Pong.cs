namespace WriteBackThroughField;

internal class Pong
{
    public Pong()
    {
    }

    public void Pong_(Ping a)
    {
        {
            a.N = a.N + 1;
        }
    }

    public int Bump(Ping a, int by)
    {
        {
            a.N = a.N + by;
        }
        return a.N;
    }
}
