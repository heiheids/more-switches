"""Runs the JUnit 5 tests from the command line, without Gradle.

The sandbox cannot start Gradle daemons, so the launcher is driven directly. Only the pure-logic
tests are run: they never touch Minecraft types, which keeps this fast and independent of the game.
"""

import io
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PROJECT = r"D:\@临时工程\更多开关功能"
GR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1")
JAVA = r"C:\Users\BZX\Documents\deepseek-harness\default-workspace\mcmod\tools\jdk25\bin\java.exe"
JAVAC = r"C:\Users\BZX\Documents\deepseek-harness\default-workspace\mcmod\tools\jdk25\bin\javac.exe"

MAIN_OUT = os.path.join(PROJECT, "_build", "nf-main")
TEST_OUT = os.path.join(PROJECT, "_build", "nf-test")
RUNNER_SRC = os.path.join(PROJECT, "_build", "runner", "RunTests.java")
RUNNER_OUT = os.path.join(PROJECT, "_build", "runner", "classes")


def newest_jar(group, artifact):
    base = os.path.join(GR, group, artifact)
    if not os.path.isdir(base):
        return None
    versions = [v for v in os.listdir(base) if os.path.isdir(os.path.join(base, v))]
    versions.sort(key=lambda v: [int(p) if p.isdigit() else 0 for p in v.split(".")])
    for version in reversed(versions):
        for directory, _dirs, files in os.walk(os.path.join(base, version)):
            for name in files:
                if name.endswith(".jar") and "sources" not in name and "javadoc" not in name:
                    return os.path.join(directory, name)
    return None


def junit_jars():
    found = []
    for group, artifact in (
            ("org.junit.platform", "junit-platform-launcher"),
            ("org.junit.platform", "junit-platform-engine"),
            ("org.junit.platform", "junit-platform-commons"),
            ("org.junit.jupiter", "junit-jupiter-engine"),
            ("org.junit.jupiter", "junit-jupiter-api"),
            ("org.opentest4j", "opentest4j"),
            ("org.apiguardian", "apiguardian-api"),
    ):
        jar = newest_jar(group, artifact)
        if jar:
            found.append(jar)
    return found


RUNNER = r'''
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

public final class RunTests {
    public static void main(String[] args) {
        LauncherDiscoveryRequest req = request().selectors(selectPackage("com.heishandashu")).build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(req, listener);
        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out);
        summary.printTo(out);
        summary.printFailuresTo(out);
        out.flush();
        System.out.println("TESTS_SUCCEEDED=" + summary.getTestsSucceededCount());
        System.out.println("TESTS_FAILED=" + summary.getTestsFailedCount());
        System.exit(summary.getTestsFailedCount() == 0 ? 0 : 1);
    }
}
'''


def main():
    os.makedirs(os.path.dirname(RUNNER_SRC), exist_ok=True)
    io.open(RUNNER_SRC, "w", encoding="utf-8").write(RUNNER)

    junit = junit_jars()
    if len(junit) < 5:
        print("missing junit jars:", junit)
        return 1

    # ScreenProjection works on Vec3, so the game jar has to be on the test classpath too.
    # Vec3's static initialiser pulls in the data-fixer-upper codecs, so the whole game classpath
    # is needed rather than the game jar alone.
    sys.path.insert(0, os.path.join(PROJECT, "tools"))
    import nf_compile
    game = nf_compile.classpath()
    cp = ";".join(junit + game + [MAIN_OUT, TEST_OUT])
    os.makedirs(RUNNER_OUT, exist_ok=True)
    argfile = os.path.join(RUNNER_OUT, "args.txt")
    io.open(argfile, "w", encoding="utf-8").write("\n".join([
        "-proc:none", "-encoding", "UTF-8", "-d", RUNNER_OUT,
        "-classpath", cp, RUNNER_SRC,
    ]))
    log = os.path.join(RUNNER_OUT, "compile.txt")
    with io.open(log, "w", encoding="utf-8") as handle:
        code = subprocess.run([JAVAC, "@" + argfile], stdout=handle, stderr=handle).returncode
    if code != 0:
        print("runner compile failed")
        print(io.open(log, encoding="utf-8", errors="replace").read()[:4000])
        return 1

    run_cp = ";".join([RUNNER_OUT] + junit + game + [MAIN_OUT, TEST_OUT])
    out_txt = os.path.join(RUNNER_OUT, "run.txt")
    with io.open(out_txt, "w", encoding="utf-8") as handle:
        code = subprocess.run([JAVA, "-classpath", run_cp, "RunTests"],
                              stdout=handle, stderr=handle).returncode
    text = io.open(out_txt, encoding="utf-8", errors="replace").read()
    print(text[-3500:])
    return code


if __name__ == "__main__":
    raise SystemExit(main())
