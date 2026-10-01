defmodule TemperCore.Generator do
  @moduledoc """
  What `adaptGeneratorFn` builds from the step function the coroutine lowering
  leaves behind (CoroutineStrategy.TranslateToRegularFunction).

  A generator is a heap object, like every other mutable Temper object: the
  step function is called with the generator itself, and returns
  `{:value, v}` (a ValueResult) or `:done` (DoneResult). The `done` flag is set
  *before* the step runs and cleared only on a value, exactly as core.temper's
  SafeGeneratorFnWrapper does, so a re-entrant `next` answers `:done` instead
  of running the step inside itself.
  """
  alias TemperCore.Heap

  def adapt(step) when is_function(step, 1), do: Heap.new(:generator, %{step: step, done: false})

  def next(g) do
    if Heap.get(g, :done) do
      :done
    else
      Heap.put(g, :done, true)
      case Heap.get(g, :step).(g) do
        {:value, _} = result ->
          Heap.put(g, :done, false)
          result

        :done ->
          :done

        # A step that falls off its end (nil) once silently ended
        # generators early; anything but a GeneratorResult is a bug.
        other ->
          raise TemperCore.Panic, "generator step returned #{inspect(other)}, not a GeneratorResult"
      end
    end
  end

  def done(g), do: Heap.get(g, :done)

  @doc """
  `ValueResult.value`. A ValueResult is the tuple `{:value, v}`, not a Temper
  object, so TemperCore.call cannot answer for it.
  """
  def value({:value, v}), do: v

  @doc "`is ValueResult`: the tag, not a module, is the type."
  def value_result?(r), do: match?({:value, _}, r)

  def close(g) do
    Heap.put(g, :done, true)
    nil
  end
end
