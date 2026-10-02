# Checks the typespecs be-elixir generated for a library against its code.
#
#   elixir dialyze.exs <plt> <the library's Mix project>
#
# Compiles the project, builds the PLT (Erlang/OTP and Elixir) if it is not
# there, and runs Dialyzer over every app the project built: the library,
# temper-core, and any library it depends on. Prints each warning, then
# SPEC-TOTAL, the warnings about a spec (a spec the code contradicts, a call
# that breaks one, a type that does not exist), and TOTAL, all of them.
[plt, project] = System.argv()
plt = String.to_charlist(plt)

{out, status} = System.cmd("mix", ["compile"], cd: project, stderr_to_stdout: true)
if status != 0, do: raise("mix compile failed:\n" <> out)

unless File.exists?(plt) do
  File.mkdir_p!(Path.dirname(to_string(plt)))
  apps = [:erts, :kernel, :stdlib, :crypto, :inets, :ssl, :public_key, :elixir, :logger]
  :dialyzer.run(analysis_type: :plt_build, output_plt: plt, files_rec: for(a <- apps, do: :code.lib_dir(a) ++ ~c"/ebin"))
end

build = Path.join(project, "_build/dev/lib")
ebins = for app <- File.ls!(build), do: String.to_charlist(Path.join([build, app, "ebin"]))

warnings =
  :dialyzer.run(
    analysis_type: :succ_typings,
    plts: [plt],
    files_rec: ebins,
    warnings: [:unmatched_returns, :error_handling, :extra_return, :missing_return, :unknown]
  )

text = fn w -> String.trim(to_string(:dialyzer.format_warning(w))) end

spec? = fn {tag, _, {kind, _}} = w ->
  String.starts_with?(to_string(tag), "warn_contract") or
    kind in [:extra_range, :missing_range, :invalid_contract, :contract_with_opaque] or
    String.contains?(text.(w), ["breaks the contract", "Unknown type", "specification"])
end

{specs, others} = Enum.split_with(warnings, spec?)
for w <- specs, do: IO.puts("SPEC " <> text.(w))
for w <- others, do: IO.puts("OTHER " <> text.(w))
IO.puts("SPEC-TOTAL #{length(specs)}")
IO.puts("TOTAL #{length(warnings)}")
