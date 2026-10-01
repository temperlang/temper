defmodule TemperCore.MixProject do
  use Mix.Project

  def project do
    [app: :temper_core, version: "0.1.0", elixir: "~> 1.15", deps: []]
  end

  # std/net uses :httpc. Mix prunes the code path to declared applications,
  # so without :inets its .app is not found; without :ssl, :httpc dies in
  # :public_key even for plain http.
  def application, do: [extra_applications: [:inets, :ssl]]
end
