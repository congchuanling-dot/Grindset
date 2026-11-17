package com.grindset.backend.service;

import com.grindset.backend.common.constant.JudgeLanguages;
import com.grindset.backend.common.constant.SubmissionStatus;
import com.grindset.backend.entity.QuestionTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.grindset.backend.common.constant.InputLimits.MAX_CODE_LENGTH;
import static com.grindset.backend.common.constant.InputLimits.MAX_FEEDBACK_LENGTH;

/**
 * 代码判题:每次运行起一个一次性 Docker 容器,断网、限 CPU/内存/进程数,超时即杀。
 *
 * <p>约定选手代码从标准输入读、向标准输出写:Java 为 Main.java 的 main 方法,
 * Python 为 main.py。Java 先编译再逐用例运行;每个用例只跑一次进程,互不影响。</p>
 */
@Service
public class JudgeService {

    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);

    private static final int COMPILE_TIMEOUT_MS = 30_000;
    private static final int HOST_GRACE_MS = 15_000;
    private static final int OUTPUT_LIMIT = 20_000;

    /**
     * 容器内运行脚本:按秒限时执行,stdin 走 input.txt,并把程序自身耗时打到 stderr。
     * 用脚本文件而不是 sh -c 拼接命令,避免 Windows 下参数转义把命令弄坏。
     */
    private static final String RUNNER_SCRIPT = """
            limit=$1
            shift
            s=$(date +%s%N)
            timeout "$limit" "$@" < /workspace/input.txt
            rc=$?
            e=$(date +%s%N)
            echo "__ELAPSED__$(( (e - s) / 1000000 ))" >&2
            exit $rc
            """;

    /** 判题镜像可配置:国内网络建议先按镜像站地址 pull,再在这里填对应镜像名 */
    private final String javaImage;
    private final String pythonImage;

    /** 读进程输出的线程池,避免 stdout/stderr 缓冲区写满阻塞子进程 */
    private final ExecutorService readers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "judge-io");
        thread.setDaemon(true);
        return thread;
    });

    public JudgeService(@Value("${app.judge.java-image:eclipse-temurin:21-jdk-alpine}") String javaImage,
                        @Value("${app.judge.python-image:python:3.12-slim}") String pythonImage) {
        this.javaImage = javaImage;
        this.pythonImage = pythonImage;
    }

    public boolean supports(String language) {
        return JudgeLanguages.JAVA.equals(normalizeLanguage(language)) || JudgeLanguages.PYTHON.equals(normalizeLanguage(language));
    }

    public static String normalizeLanguage(String language) {
        String value = language == null ? "" : language.strip().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "JAVA", "JDK" -> JudgeLanguages.JAVA;
            case "PYTHON", "PY", "PYTHON3" -> JudgeLanguages.PYTHON;
            default -> value;
        };
    }

    /**
     * 跑一组用例。status 为最终结论,passed/total 为通过数;
     * 编译失败、系统错误时 cases 为空或已执行的部分结果。
     */
    public JudgeOutcome judge(String language, String code, List<QuestionTestCase> cases,
                              int timeLimitMs, int memoryLimitMb) {
        String lang = normalizeLanguage(language);
        if (!JudgeLanguages.JAVA.equals(lang) && !JudgeLanguages.PYTHON.equals(lang)) {
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "暂不支持的语言：" + language, List.of());
        }
        if (code == null || code.isBlank()) {
            return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0, "代码不能为空", List.of());
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0,
                    "代码不能超过 " + MAX_CODE_LENGTH + " 字符", List.of());
        }

        String image = JudgeLanguages.JAVA.equals(lang) ? javaImage : pythonImage;
        if (!dockerImageExists(image)) {
            log.error("判题镜像不存在 image={}", image);
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "判题环境未就绪，缺少镜像 " + image + "，请先执行 docker pull " + image, List.of());
        }

        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("grindset-judge-");
            Files.writeString(workspace.resolve("runner.sh"), RUNNER_SCRIPT, StandardCharsets.UTF_8);
            if (JudgeLanguages.JAVA.equals(lang)) {
                Files.writeString(workspace.resolve("Main.java"), code, StandardCharsets.UTF_8);
                ExecResult compile = exec(dockerRun(workspace, image, memoryLimitMb,
                                List.of("sh", "-c", "javac -encoding UTF-8 -d out Main.java")),
                        COMPILE_TIMEOUT_MS);
                if (!compile.ok()) {
                    return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0,
                            firstNonBlank(limit(compile.stderr(), MAX_FEEDBACK_LENGTH),
                                    limit(compile.stdout(), MAX_FEEDBACK_LENGTH)),
                            List.of());
                }
            } else {
                Files.writeString(workspace.resolve("main.py"), code, StandardCharsets.UTF_8);
            }

            List<CaseOutcome> outcomes = new ArrayList<>();
            int passed = 0;
            int totalRuntime = 0;
            String overallStatus = SubmissionStatus.ACCEPTED;
            String errorMessage = null;
            int index = 0;
            for (QuestionTestCase testCase : cases) {
                index++;
                ExecResult run = runCase(lang, workspace, image, memoryLimitMb, timeLimitMs, testCase);
                totalRuntime += run.elapsedMs();
                String actual = limit(run.stdout(), OUTPUT_LIMIT);

                if (run.timedOut()) {
                    outcomes.add(new CaseOutcome(index, testCase.getInput(), testCase.getExpectedOutput(),
                            actual, false, "超出时间限制"));
                    overallStatus = SubmissionStatus.TIME_LIMIT_EXCEEDED;
                    errorMessage = "用例 " + index + " 运行超时（限制 " + timeLimitMs + "ms）";
                    break;
                }
                if (run.exitCode() != 0) {
                    outcomes.add(new CaseOutcome(index, testCase.getInput(), testCase.getExpectedOutput(),
                            actual, false, "运行出错"));
                    overallStatus = SubmissionStatus.RUNTIME_ERROR;
                    errorMessage = "用例 " + index + " 运行出错："
                            + limit(firstNonBlank(run.stderr(), run.stdout()), MAX_FEEDBACK_LENGTH);
                    break;
                }
                boolean ok = normalize(actual).equals(normalize(testCase.getExpectedOutput()));
                outcomes.add(new CaseOutcome(index, testCase.getInput(), testCase.getExpectedOutput(),
                        actual, ok, ok ? "通过" : "输出与期望不一致"));
                if (ok) {
                    passed++;
                } else if (SubmissionStatus.ACCEPTED.equals(overallStatus)) {
                    overallStatus = SubmissionStatus.WRONG_ANSWER;
                    errorMessage = "用例 " + index + " 输出与期望不一致";
                }
            }
            return new JudgeOutcome(overallStatus, passed, cases.size(), totalRuntime, errorMessage, outcomes);
        } catch (IOException e) {
            log.error("判题失败 language={}", language, e);
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "判题服务异常：" + e.getMessage(), List.of());
        } finally {
            deleteQuietly(workspace);
        }
    }

    /** 运行单个用例;超时用容器内 timeout 命令,宿主侧再做兜底 kill */
    private ExecResult runCase(String lang, Path workspace, String image, int memoryLimitMb,
                               int timeLimitMs, QuestionTestCase testCase) throws IOException {
        int seconds = Math.max(1, (timeLimitMs + 999) / 1000);
        Files.writeString(workspace.resolve("input.txt"),
                testCase.getInput() == null ? "" : testCase.getInput(), StandardCharsets.UTF_8);

        List<String> program = JudgeLanguages.JAVA.equals(lang)
                ? List.of("java", "-Xmx" + heapMb(memoryLimitMb) + "m", "-Xss16m", "-cp", "out", "Main")
                : List.of("python3", "-I", "-B", "main.py");
        List<String> args = new ArrayList<>(List.of("sh", "/workspace/runner.sh", String.valueOf(seconds)));
        args.addAll(program);
        return exec(dockerRun(workspace, image, memoryLimitMb, args), timeLimitMs + HOST_GRACE_MS);
    }

    private List<String> dockerRun(Path workspace, String image, int memoryLimitMb, List<String> tail) {
        String name = "grindset-judge-" + UUID.randomUUID().toString().substring(0, 8);
        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("run");
        args.add("--rm");
        args.add("--name");
        args.add(name);
        args.add("--network");
        args.add("none");
        args.add("--cpus");
        args.add("0.5");
        args.add("-m");
        args.add(Math.max(64, memoryLimitMb) + "m");
        args.add("--pids-limit");
        args.add("64");
        args.add("--cap-drop");
        args.add("ALL");
        args.add("--security-opt");
        args.add("no-new-privileges");
        args.add("--read-only");
        args.add("--tmpfs");
        args.add("/tmp:rw,size=64m");
        args.add("-v");
        args.add(workspace.toAbsolutePath().toString().replace('\\', '/') + ":/workspace");
        args.add("-w");
        args.add("/workspace");
        args.add(image);
        args.addAll(tail);
        return args;
    }

    private boolean dockerImageExists(String image) {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect", image)
                    .redirectErrorStream(true).start();
            drain(process.getInputStream());
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            log.error("Docker 不可用", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ExecResult exec(List<String> command, int timeoutMs) throws IOException {
        long startedAt = System.currentTimeMillis();
        Process process = new ProcessBuilder(command).start();
        Future<String> stdout = readers.submit(() -> readLimited(process.getInputStream()));
        Future<String> stderr = readers.submit(() -> readLimited(process.getErrorStream()));
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // 进程提前退出时关闭 stdin 会失败,忽略
        }

        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }
        if (!finished) {
            process.destroyForcibly();
            killContainer(command);
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String out = await(stdout);
        String err = await(stderr);
        long elapsed = System.currentTimeMillis() - startedAt;

        long containerElapsed = parseElapsed(err);
        if (containerElapsed >= 0) {
            err = stripElapsed(err);
        } else {
            containerElapsed = elapsed;
        }
        int exitCode = finished ? process.exitValue() : 124;
        return new ExecResult(exitCode, out, err, containerElapsed, !finished || exitCode == 124);
    }

    /** 超时兜底:按容器名强杀,避免 docker run 残留 */
    private void killContainer(List<String> command) {
        int index = command.indexOf("--name");
        if (index < 0 || index + 1 >= command.size()) {
            return;
        }
        String name = command.get(index + 1);
        try {
            new ProcessBuilder("docker", "rm", "-f", name).start();
        } catch (IOException e) {
            log.warn("清理判题容器失败 name={}", name);
        }
    }

    private static long parseElapsed(String stderr) {
        if (stderr == null) {
            return -1;
        }
        String marker = "__ELAPSED__";
        int index = stderr.lastIndexOf(marker);
        if (index < 0) {
            return -1;
        }
        String digits = stderr.substring(index + marker.length()).strip().split("\\s+")[0];
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String stripElapsed(String stderr) {
        String marker = "__ELAPSED__";
        int index = stderr.lastIndexOf(marker);
        if (index < 0) {
            return stderr;
        }
        String before = stderr.substring(0, index);
        String after = stderr.substring(index);
        int newline = after.indexOf('\n');
        return newline < 0 ? before : before + after.substring(newline + 1);
    }

    private static String await(Future<String> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return "";
        }
    }

    private static String readLimited(InputStream in) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        long total = 0;
        try {
            while ((read = in.read(chunk)) != -1) {
                if (total < OUTPUT_LIMIT) {
                    buffer.write(chunk, 0, (int) Math.min(read, OUTPUT_LIMIT - total));
                }
                total += read;
            }
        } catch (IOException ignored) {
            // 进程被杀时读到 EOF 异常属正常
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static void drain(InputStream in) {
        try (in) {
            in.readAllBytes();
        } catch (IOException ignored) {
            // 忽略
        }
    }

    /** 输出比对:统一换行符,去掉每行行尾空白和末尾空行 */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            kept.add(line.stripTrailing());
        }
        int end = kept.size();
        while (end > 0 && kept.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join("\n", kept.subList(0, end));
    }

    private static int heapMb(int memoryLimitMb) {
        return Math.max(64, memoryLimitMb - 96);
    }

    private static String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null ? "" : second.strip();
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 忽略清理失败
                }
            });
        } catch (IOException ignored) {
            // 忽略清理失败
        }
    }

    private record ExecResult(int exitCode, String stdout, String stderr, long elapsedMs, boolean timedOut) {
        boolean ok() {
            return exitCode == 0 && !timedOut;
        }
    }

    public record CaseOutcome(
            int index,
            String input,
            String expectedOutput,
            String actualOutput,
            boolean passed,
            String message) {
    }

    public record JudgeOutcome(
            String status,
            int passed,
            int total,
            int runtimeMs,
            String errorMessage,
            List<CaseOutcome> cases) {
    }
}
