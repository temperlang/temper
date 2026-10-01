# Elixir implementations of the @connected declarations in
# connected.temper.md. be-elixir copies this file into the library's lib/
# and gives each @connected function a body that calls TemperConnected.<name>
# with its defaults already applied.
defmodule TemperConnected do
  def sum(i, j, bonus), do: i + j + bonus

  # Hidden is a translated class, so its public property is read through its
  # getter, the same way translated code reads it
  def prod(hidden, j), do: TemperCore.call(hidden, :get_i, []) * j

  # -1 for a null string, as the other backends' versions answer
  def length(nil), do: -1
  def length(s), do: Kernel.length(String.to_charlist(s))
end
