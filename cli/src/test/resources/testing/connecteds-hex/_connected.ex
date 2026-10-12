defmodule Temper.ConnectedsHex.Connected do
  def addDecimals(a, b), do: Decimal.add(Decimal.new(a), Decimal.new(b)) |> Decimal.to_string()
end
