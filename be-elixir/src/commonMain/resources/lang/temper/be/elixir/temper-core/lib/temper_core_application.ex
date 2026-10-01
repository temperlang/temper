defmodule TemperCore.Application do
  @moduledoc """
  What a node running Temper code shares: the table of module values
  (`TemperCore.Global`), the registry that names actors, and the supervisor
  that `TemperCore.Actor.supervised/1` starts actors under.

  It starts with the `:temper_core` application, so a Mix project that
  depends on a translated library gets it, `mix run` and Phoenix alike.
  """
  use Application

  @impl true
  def start(_type, _args) do
    children = [
      TemperCore.State,
      {Registry, keys: :unique, name: TemperCore.Actors.Registry},
      {DynamicSupervisor, name: TemperCore.Actors, strategy: :one_for_one}
    ]

    Supervisor.start_link(children, strategy: :one_for_one, name: TemperCore.Supervisor)
  end
end

defmodule TemperCore.State do
  @moduledoc "Owns the ETS table of module values, so it outlives whichever process wrote one."
  use GenServer

  def start_link(_), do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)

  @impl true
  def init(nil) do
    :ets.new(:temper_globals, [:named_table, :public, :set, read_concurrency: true])
    {:ok, nil}
  end
end
