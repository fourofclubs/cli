package cli.core;

import static fj.F1Functions.mapOption;
import static fj.Function.apply;
import static fj.Function.compose;
import static fj.Function.constant;
import static fj.Ord.stringOrd;
import static fj.P2.tuple;
import static fj.data.IOFunctions.append;
import static fj.data.IOFunctions.as;
import static fj.data.IOFunctions.bind;
import static fj.data.IOFunctions.map;
import static fj.data.IOFunctions.stdinReadLine;
import static fj.data.IOFunctions.stdoutPrint;
import static fj.data.IOFunctions.stdoutPrintln;
import static fj.data.IOFunctions.unit;
import static fj.data.IOFunctions.voided;
import static fj.data.Option.iif;
import static fj.data.Option.none;
import static fj.data.Option.some;

import fj.F;
import fj.F1Functions;
import fj.Function;
import fj.P2;
import fj.Unit;
import fj.data.IO;
import fj.data.IOFunctions;
import fj.data.List;
import fj.data.Option;
import fj.data.Stream;
import fj.data.TreeMap;
import fj.function.Characters;

public final class CommandExecutor {
	public final String context;
	private final TreeMap<String, F<Option<String>, IO<Option<CommandExecutor>>>> dispatch;

	CommandExecutor(final String context,
			final TreeMap<String, F<Option<String>, IO<Option<CommandExecutor>>>> dispatch) {
		this.context = context;
		this.dispatch = dispatch;
	}

	public static final CommandExecutor commandExecutor(final String context) {
		return new CommandExecutor(context, TreeMap.empty(stringOrd));
	}

	public final CommandExecutor until(final String command) {
		return new CommandExecutor(this.context, this.dispatch.set(command, constant(unit(none()))));
	}

	public final CommandExecutor withCommand(final String command,
			final F<Option<String>, IO<Option<CommandExecutor>>> execute) {
		return new CommandExecutor(this.context, this.dispatch.set(command, execute));
	}

	public final CommandExecutor withSimpleCommand(final String command, final F<Option<String>, IO<Unit>> execute) {
		return this.withCommand(command, compose(io -> as(io, some(this)), execute));
	}

	public static F<String, P2<String, Option<String>>> command() {
		return str -> command(str);
	}

	public static P2<String, Option<String>> command(final String str) {
		final P2<List<Character>, List<Character>> p = List.fromString(str).breakk(Characters.isWhitespace);
		return p.map1(List.asString()).map2(compose(Option.fromString(), List.asString()));
	}

	public final IO<Option<CommandExecutor>> execute(final String name, final Option<String> arg) {
		final IO<Option<CommandExecutor>> handleUnknown = as(stdoutPrintln("Unrecognized command."), some(this));
		return this.dispatch.get(name).map(apply(arg)).orSome(handleUnknown);
	}

	public final IO<Unit> processStream(final Stream<String> commands) {
		if (commands.isEmpty()) {
			return IOFunctions.ioUnit;
		}
		final P2<String, Option<String>> command = command(commands.head());
		return bind(execute(command._1(), command._2()), optExe -> voided(
				bind(IOFunctions.fromF(commands.tail()), tail -> optExe.traverseIO(exe -> exe.processStream(tail)))));
	}

	public final IO<Unit> processStdIn() {
		final F<String, IO<Option<String>>> readLine = cxt -> append(stdoutPrint(cxt + ">"),
				map(stdinReadLine(), s -> iif(!s.equals("exit"), s)));
		return processReads(readLine);
	}

	private final IO<Unit> processReads(final F<String, IO<Option<String>>> readLine) {
		final IO<Option<P2<String, Option<String>>>> read = map(readLine.f(this.context), mapOption(command()));
		final F<IO<CommandExecutor>, IO<Unit>> executeNext = io -> bind(io, exe -> exe.processReads(readLine));
		return voided(bind(read,
				optCommand -> optCommand.traverseIO(
						compose(compose(executeNext, io -> IOFunctions.map(io, optExe -> optExe.orSome(this))),
								tuple(this::execute)))));
	}
}
