package com.grindset.backend.service;

import com.grindset.backend.common.FreeRunStatus;
import com.grindset.backend.common.constant.JudgeLanguage;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.grindset.backend.common.constant.InputLimits.MAX_CODE_LENGTH;
import static com.grindset.backend.common.constant.InputLimits.MAX_FEEDBACK_LENGTH;

/**
 * 代码自由运行:每次运行起一个一次性 Docker 容器,断网、限 CPU/内存/进程数,超时即杀。
 *
 * <p>选手写完整程序:Java 为 Main.java 的 main 方法,Python 为 main.py。
 * 平台不提供测试用例,也不比对答案,只执行并回传控制台输出;正确性交给 AI 点评。</p>
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

    /** 兼容前端/历史语言的别名,不认识返回 null */
    public static JudgeLanguage parse(String language) {
        return JudgeLanguage.from(language);
    }

    /**
     * 自由运行:执行完整程序并返回控制台输出。
     * 不提供测试用例、不比对期望答案,status 只表示是否成功跑完。
     */
    public FreeRunOutcome runFree(JudgeLanguage lang, String code, int timeLimitMs, int memoryLimitMb) {
        if (lang == null) {
            return new FreeRunOutcome(FreeRunStatus.SYSTEM_ERROR, "", "暂不支持的语言",
                    -1, 0, false);
        }
        if (code == null || code.isBlank()) {
            return new FreeRunOutcome(FreeRunStatus.COMPILE_ERROR, "", "代码不能为空",
                    -1, 0, false);
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return new FreeRunOutcome(FreeRunStatus.COMPILE_ERROR, "",
                    "代码不能超过 " + MAX_CODE_LENGTH + " 字符", -1, 0, false);
        }

        String image = lang == JudgeLanguage.JAVA ? javaImage : pythonImage;
        if (!dockerImageExists(image)) {
            log.error("运行镜像不存在 image={}", image);
            return new FreeRunOutcome(FreeRunStatus.SYSTEM_ERROR, "",
                    "运行环境未就绪，缺少镜像 " + image + "，请先执行 docker pull " + image,
                    -1, 0, false);
        }

        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("grindset-free-run-");
            Files.writeString(workspace.resolve("runner.sh"), RUNNER_SCRIPT, StandardCharsets.UTF_8);
            if (lang == JudgeLanguage.JAVA) {
                Files.writeString(workspace.resolve("Main.java"), code, StandardCharsets.UTF_8);
                ExecResult compile = exec(dockerRun(workspace, image, memoryLimitMb,
                                List.of("sh", "-c", "javac -encoding UTF-8 -d out Main.java")),
                        COMPILE_TIMEOUT_MS);
                if (!compile.ok()) {
                    return new FreeRunOutcome(FreeRunStatus.COMPILE_ERROR, "",
                            limit(firstNonBlank(compile.stderr(), compile.stdout()), MAX_FEEDBACK_LENGTH),
                            compile.exitCode(), (int) compile.elapsedMs(), compile.timedOut());
                }
            } else {
                Files.writeString(workspace.resolve("main.py"), code, StandardCharsets.UTF_8);
            }

            ExecResult run = runCase(lang, workspace, image, memoryLimitMb, timeLimitMs, "");

            FreeRunStatus status = run.timedOut()
                    ? FreeRunStatus.TIME_LIMIT_EXCEEDED
                    : run.exitCode() == 0 ? FreeRunStatus.SUCCESS : FreeRunStatus.RUNTIME_ERROR;
            return new FreeRunOutcome(status,
                    limit(run.stdout(), OUTPUT_LIMIT),
                    limit(run.stderr(), OUTPUT_LIMIT),
                    run.exitCode(), (int) run.elapsedMs(), run.timedOut());
        } catch (IOException e) {
            log.error("FREE_RUN 执行失败 language={}", lang, e);
            return new FreeRunOutcome(FreeRunStatus.SYSTEM_ERROR, "",
                    "运行服务异常：" + e.getMessage(), -1, 0, false);
        } finally {
            deleteQuietly(workspace);
        }
    }

    /** 运行单个程序;超时用容器内 timeout 命令,宿主侧再做兜底 kill */
    private ExecResult runCase(JudgeLanguage lang, Path workspace, String image, int memoryLimitMb,
                               int timeLimitMs, String input) throws IOException {
        int seconds = Math.max(1, (timeLimitMs + 999) / 1000);
        Files.writeString(workspace.resolve("input.txt"),
                input == null ? "" : input, StandardCharsets.UTF_8);

        List<String> args = new ArrayList<>(List.of("sh", "/workspace/runner.sh", String.valueOf(seconds)));
        args.addAll(defaultProgram(lang, memoryLimitMb));
        return exec(dockerRun(workspace, image, memoryLimitMb, args), timeLimitMs + HOST_GRACE_MS);
    }

    private List<String> defaultProgram(JudgeLanguage lang, int memoryLimitMb) {
        return lang == JudgeLanguage.JAVA
                ? List.of("java", "-Xmx" + heapMb(memoryLimitMb) + "m", "-Xss16m", "-cp", "out", "Main")
                : List.of("python3", "-I", "-B", "main.py");
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

    /** FREE_RUN 结果:只代表代码是否成功跑完,不判断答案对错 */
    public record FreeRunOutcome(
            FreeRunStatus status,
            String stdout,
            String stderr,
            int exitCode,
            int runtimeMs,
            boolean timedOut) {
    }
}
