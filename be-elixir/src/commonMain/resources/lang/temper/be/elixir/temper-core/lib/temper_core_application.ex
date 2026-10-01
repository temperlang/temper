defmodule TemperCore.Application do
  @moduledoc """
  What a node running Temper code shares: the table of module values
  (`TemperCore.Global`), the registry that names actors, and two
  supervisors: `TemperCore.LibraryActors` for the actors a library's top
  level makes, which belong to the node, and `TemperCore.Actors`, which
  `TemperCore.Actor.supervised/1` starts actors under by default.

  Each supervised actor runs under its own keeper supervisor, which holds
  its restart limit, so an actor that keeps crashing ends alone instead of
  using up a limit every other actor shares.

  It starts with the `:temper_core` application, so a Mix project that
  depends on a translated library gets it, `mix run` and Phoenix alike.
  """
  use Application

  @impl true
  def start(_type, _args) do
    children = [
      TemperCore.State,
      {Registry, keys: :unique, name: TemperCore.Actors.Registry},
      {DynamicSupervisor, name: TemperCore.LibraryActors, strategy: :one_for_one},
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
