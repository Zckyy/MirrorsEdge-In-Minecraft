package dev.faithrunner;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * faith_ffi (faith.h) through Java's foreign-function API: the same C interface the Skyrim plugin
 * links. Host frame: Z up, 1 unit a metre, headings clockwise from north (+Y).
 */
final class Native {
	static final StructLayout VEC3 = MemoryLayout.structLayout(JAVA_FLOAT.withName("x"), JAVA_FLOAT.withName("y"), JAVA_FLOAT.withName("z"));

	/** sizeof(FaithInput) and sizeof(FaithFrame) (faith.h's static_asserts). */
	static final long INPUT_SIZE = 24;
	static final long FRAME_SIZE = 120;

	// FaithFrame offsets.
	static final long F_FEET = 0, F_VELOCITY = 12, F_HEADING = 24, F_PITCH = 28, F_BODY_HEADING = 32, F_CAM_POS = 36,
		F_CAM_FORWARD = 48, F_CAM_UP = 60, F_CAM_RIGHT = 72, F_FOV = 84, F_LAND_IMPACT = 88, F_EVENTS = 96, F_ON_GROUND = 104,
		F_ANIMATED = 105, F_LOW = 107, F_SPEED_BLUR = 108, F_REACTION = 112, F_GAME_SPEED = 116;

	final MethodHandle create, destroy, animated, setWorld, teleport, step, stateName, lastError, setAutoStepUp, soundPause;
	final MethodHandle setHostFixtures, setCandidates, doorsOpened, setAutoStepUpMax, bodyBone;

	/** sizeof(FaithHostFixture), FaithFixtureCandidate, FaithXform (faith.h). */
	static final long HOST_FIXTURE = 48, CANDIDATE = 32, XFORM = 32;

	final Path path;

	Native(Path dll) {
		path = dll;
		Linker linker = Linker.nativeLinker();
		SymbolLookup lib = SymbolLookup.libraryLookup(dll, Arena.global());
		create = fn(linker, lib, "faith_create", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_FLOAT));
		destroy = fn(linker, lib, "faith_destroy", FunctionDescriptor.ofVoid(ADDRESS));
		animated = fn(linker, lib, "faith_animated", FunctionDescriptor.of(JAVA_BYTE, ADDRESS));
		setWorld = fn(linker, lib, "faith_set_world", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));
		teleport = fn(linker, lib, "faith_teleport", FunctionDescriptor.ofVoid(ADDRESS, VEC3, JAVA_FLOAT));
		step = fn(linker, lib, "faith_step", FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT, ADDRESS, ADDRESS));
		stateName = fn(linker, lib, "faith_state_name", FunctionDescriptor.of(ADDRESS, ADDRESS));
		lastError = fn(linker, lib, "faith_last_error", FunctionDescriptor.of(ADDRESS));
		setAutoStepUp = fn(linker, lib, "faith_set_auto_step_up", FunctionDescriptor.ofVoid(ADDRESS, JAVA_BYTE));
		soundPause = fn(linker, lib, "faith_sound_pause", FunctionDescriptor.ofVoid(ADDRESS, JAVA_BYTE));
		setHostFixtures = fn(linker, lib, "faith_set_host_fixtures", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));
		setCandidates = fn(linker, lib, "faith_set_fixture_candidates", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));
		doorsOpened = fn(linker, lib, "faith_doors_opened", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
		setAutoStepUpMax = fn(linker, lib, "faith_set_auto_step_up_max", FunctionDescriptor.ofVoid(ADDRESS, JAVA_FLOAT));
		bodyBone = fn(linker, lib, "faith_body_bone", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS));
	}

	private static MethodHandle fn(Linker linker, SymbolLookup lib, String name, FunctionDescriptor d) {
		return linker.downcallHandle(lib.find(name).orElseThrow(() -> new IllegalStateException("faith_ffi has no " + name)), d);
	}

	static String cString(MemorySegment p) {
		if (p.address() == 0) {
			return "";
		}
		return p.reinterpret(4096).getString(0);
	}

	static float f(MemorySegment s, long off) {
		return s.get(JAVA_FLOAT, off);
	}

	static long events(MemorySegment frame) {
		return frame.get(JAVA_LONG, F_EVENTS);
	}
}
