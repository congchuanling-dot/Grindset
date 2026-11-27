package com.grindset.backend.service;

import com.grindset.backend.common.FreeRunStatus;
import com.grindset.backend.common.constant.JudgeLanguage;
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
import java.util.Set;
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

    /**
     * 函数模式注入到用户代码旁边的 JSON 工具类。
     * 全部用字符码写引号/反斜杠,避免模板本身的转义歧义。
     */
    private static final String JSON_HELPER = """
            class Json {
                private final String s;
                private int i;

                private Json(String s) {
                    this.s = s;
                }

                @SuppressWarnings("unchecked")
                static List<Object> parse(String text) {
                    Json p = new Json(text);
                    p.ws();
                    return (List<Object>) p.value();
                }

                private void ws() {
                    while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                        i++;
                    }
                }

                private Object value() {
                    ws();
                    char c = s.charAt(i);
                    if (c == '[') {
                        i++;
                        List<Object> list = new ArrayList<>();
                        ws();
                        if (i < s.length() && s.charAt(i) == ']') {
                            i++;
                            return list;
                        }
                        while (true) {
                            list.add(value());
                            ws();
                            if (s.charAt(i++) == ']') {
                                return list;
                            }
                        }
                    }
                    if (c == '"') {
                        return text();
                    }
                    if (c == 't') {
                        i += 4;
                        return Boolean.TRUE;
                    }
                    if (c == 'f') {
                        i += 5;
                        return Boolean.FALSE;
                    }
                    if (c == 'n') {
                        i += 4;
                        return null;
                    }
                    int start = i;
                    while (i < s.length() && "+-.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                        i++;
                    }
                    return Double.valueOf(s.substring(start, i));
                }

                private String text() {
                    i++;
                    StringBuilder b = new StringBuilder();
                    while (true) {
                        char c = s.charAt(i++);
                        if (c == '"') {
                            return b.toString();
                        }
                        if (c != (char) 92) {
                            b.append(c);
                            continue;
                        }
                        char e = s.charAt(i++);
                        if (e == 'u') {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        } else if (e == 'n') {
                            b.append((char) 10);
                        } else if (e == 't') {
                            b.append((char) 9);
                        } else if (e == 'r') {
                            b.append((char) 13);
                        } else if (e == 'b') {
                            b.append((char) 8);
                        } else if (e == 'f') {
                            b.append((char) 12);
                        } else {
                            b.append(e);
                        }
                    }
                }

                static int num(Object o) {
                    return ((Number) o).intValue();
                }

                static long lng(Object o) {
                    return ((Number) o).longValue();
                }

                static double dbl(Object o) {
                    return ((Number) o).doubleValue();
                }

                static boolean bool(Object o) {
                    return Boolean.TRUE.equals(o);
                }

                static String str(Object o) {
                    return o == null ? null : String.valueOf(o);
                }

                static char chr(Object o) {
                    String v = str(o);
                    return v == null || v.isEmpty() ? ' ' : v.charAt(0);
                }

                @SuppressWarnings("unchecked")
                static int[] ints(Object o) {
                    List<Object> l = (List<Object>) o;
                    int[] r = new int[l.size()];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = num(l.get(k));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static long[] longs(Object o) {
                    List<Object> l = (List<Object>) o;
                    long[] r = new long[l.size()];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = lng(l.get(k));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static double[] doubles(Object o) {
                    List<Object> l = (List<Object>) o;
                    double[] r = new double[l.size()];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = dbl(l.get(k));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static boolean[] bools(Object o) {
                    List<Object> l = (List<Object>) o;
                    boolean[] r = new boolean[l.size()];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = bool(l.get(k));
                    }
                    return r;
                }

                static char[] chars(Object o) {
                    String v = str(o);
                    return v == null ? new char[0] : v.toCharArray();
                }

                @SuppressWarnings("unchecked")
                static String[] strs(Object o) {
                    List<Object> l = (List<Object>) o;
                    String[] r = new String[l.size()];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = str(l.get(k));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static int[][] ints2(Object o) {
                    List<Object> l = (List<Object>) o;
                    int[][] r = new int[l.size()][];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = ints(l.get(k));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static char[][] chars2(Object o) {
                    List<Object> l = (List<Object>) o;
                    char[][] r = new char[l.size()][];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = chars(l.get(k));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static String[][] strs2(Object o) {
                    List<Object> l = (List<Object>) o;
                    String[][] r = new String[l.size()][];
                    for (int k = 0; k < r.length; k++) {
                        r[k] = strs(l.get(k));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static List<Integer> intList(Object o) {
                    List<Object> l = (List<Object>) o;
                    List<Integer> r = new ArrayList<>();
                    for (Object item : l) {
                        r.add(num(item));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static List<String> strList(Object o) {
                    List<Object> l = (List<Object>) o;
                    List<String> r = new ArrayList<>();
                    for (Object item : l) {
                        r.add(str(item));
                    }
                    return r;
                }

                @SuppressWarnings("unchecked")
                static List<List<Integer>> intList2(Object o) {
                    List<Object> l = (List<Object>) o;
                    List<List<Integer>> r = new ArrayList<>();
                    for (Object item : l) {
                        r.add(intList(item));
                    }
                    return r;
                }

                static String toJson(Object o) {
                    if (o == null) {
                        return "null";
                    }
                    if (o instanceof Boolean || o instanceof Integer || o instanceof Long
                            || o instanceof Short || o instanceof Byte) {
                        return o.toString();
                    }
                    if (o instanceof Double || o instanceof Float) {
                        double d = ((Number) o).doubleValue();
                        if (!Double.isInfinite(d) && !Double.isNaN(d) && d == Math.rint(d)) {
                            return String.valueOf((long) d);
                        }
                        return String.valueOf(d);
                    }
                    if (o instanceof Number) {
                        return o.toString();
                    }
                    if (o instanceof Character) {
                        return quote(o.toString());
                    }
                    if (o instanceof String) {
                        return quote((String) o);
                    }
                    if (o instanceof char[]) {
                        return quote(new String((char[]) o));
                    }
                    if (o.getClass().isArray()) {
                        int n = java.lang.reflect.Array.getLength(o);
                        StringBuilder b = new StringBuilder("[");
                        for (int k = 0; k < n; k++) {
                            if (k > 0) {
                                b.append(',');
                            }
                            b.append(toJson(java.lang.reflect.Array.get(o, k)));
                        }
                        return b.append(']').toString();
                    }
                    if (o instanceof Collection) {
                        StringBuilder b = new StringBuilder("[");
                        boolean first = true;
                        for (Object item : (Collection<?>) o) {
                            if (!first) {
                                b.append(',');
                            }
                            first = false;
                            b.append(toJson(item));
                        }
                        return b.append(']').toString();
                    }
                    return quote(String.valueOf(o));
                }

                private static String quote(String v) {
                    StringBuilder b = new StringBuilder();
                    b.append((char) 34);
                    for (int k = 0; k < v.length(); k++) {
                        char c = v.charAt(k);
                        if (c == (char) 34 || c == (char) 92) {
                            b.append((char) 92).append(c);
                        } else if (c == (char) 10) {
                            b.append((char) 92).append('n');
                        } else if (c == (char) 13) {
                            b.append((char) 92).append('r');
                        } else if (c == (char) 9) {
                            b.append((char) 92).append('t');
                        } else {
                            b.append(c);
                        }
                    }
                    b.append((char) 34);
                    return b.toString();
                }
            }
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
        return JudgeLanguage.from(language) != null;
    }

    /** 兼容前端/历史语言的别名,不认识返回 null */
    public static JudgeLanguage parse(String language) {
        return JudgeLanguage.from(language);
    }

    /**
     * 跑一组用例。status 为最终结论,passed/total 为通过数;
     * 编译失败、系统错误时 cases 为空或已执行的部分结果。
     */
    public JudgeOutcome judge(JudgeLanguage lang, String code, List<QuestionTestCase> cases,
                              int timeLimitMs, int memoryLimitMb) {
        if (lang == null) {
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "暂不支持的语言", List.of());
        }
        if (code == null || code.isBlank()) {
            return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0, "代码不能为空", List.of());
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0,
                    "代码不能超过 " + MAX_CODE_LENGTH + " 字符", List.of());
        }

        String image = lang == JudgeLanguage.JAVA ? javaImage : pythonImage;
        if (!dockerImageExists(image)) {
            log.error("判题镜像不存在 image={}", image);
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "判题环境未就绪，缺少镜像 " + image + "，请先执行 docker pull " + image, List.of());
        }

        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("grindset-judge-");
            Files.writeString(workspace.resolve("runner.sh"), RUNNER_SCRIPT, StandardCharsets.UTF_8);
            if (lang == JudgeLanguage.JAVA) {
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
            SubmissionStatus overallStatus = SubmissionStatus.ACCEPTED;
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
                } else if (overallStatus == SubmissionStatus.ACCEPTED) {
                    overallStatus = SubmissionStatus.WRONG_ANSWER;
                    errorMessage = "用例 " + index + " 输出与期望不一致";
                }
            }
            return new JudgeOutcome(overallStatus, passed, cases.size(), totalRuntime, errorMessage, outcomes);
        } catch (IOException e) {
            log.error("判题失败 language={}", lang, e);
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "判题服务异常：" + e.getMessage(), List.of());
        } finally {
            deleteQuietly(workspace);
        }
    }

    /**
     * FREE_RUN 面试模式:候选人写完整 main,不提供测试用例,只执行并返回控制台输出。
     * 不比对期望答案,status 只表示是否成功跑完。
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

            QuestionTestCase empty = new QuestionTestCase();
            empty.setInput("");
            empty.setExpectedOutput("");
            ExecResult run = runCaseWith(lang, workspace, image, memoryLimitMb, timeLimitMs, empty,
                    defaultProgram(lang, memoryLimitMb));

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

    // ---------- 力扣模式:函数签名 ----------

    /** 函数题签名:方法名、参数类型(按顺序)、返回值类型 */
    public record FunctionSpec(String method, List<String> paramTypes, String returnType) {
    }

    /**
     * 函数模式判题:用户只写 Solution 类,这里生成一个测试驱动读入 JSON 参数、
     * 调用目标方法并把返回值按 JSON 打印,再与期望输出逐字节比较。
     */
    public JudgeOutcome judgeFunction(JudgeLanguage lang, String code, FunctionSpec spec,
                                      List<QuestionTestCase> cases, int timeLimitMs, int memoryLimitMb) {
        if (lang == null) {
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "暂不支持的语言", List.of());
        }
        if (spec == null || spec.method() == null || spec.method().isBlank()) {
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "这道函数题还没有配置函数签名", List.of());
        }
        for (String type : spec.paramTypes()) {
            if (!SUPPORTED_TYPES.contains(type)) {
                return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                        "暂不支持参数类型 " + type, List.of());
            }
        }
        if (code == null || code.isBlank()) {
            return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0, "代码不能为空", List.of());
        }
        if (code.length() > MAX_CODE_LENGTH) {
            return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0,
                    "代码不能超过 " + MAX_CODE_LENGTH + " 字符", List.of());
        }

        String image = lang == JudgeLanguage.JAVA ? javaImage : pythonImage;
        if (!dockerImageExists(image)) {
            log.error("判题镜像不存在 image={}", image);
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "判题环境未就绪，缺少镜像 " + image + "，请先执行 docker pull " + image, List.of());
        }

        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("grindset-judge-");
            Files.writeString(workspace.resolve("runner.sh"), RUNNER_SCRIPT, StandardCharsets.UTF_8);

            List<String> program;
            if (lang == JudgeLanguage.JAVA) {
                Files.writeString(workspace.resolve("Solution.java"), code, StandardCharsets.UTF_8);
                Files.writeString(workspace.resolve("Runner.java"), javaRunner(spec), StandardCharsets.UTF_8);
                ExecResult compile = exec(dockerRun(workspace, image, memoryLimitMb,
                                List.of("sh", "-c", "javac -encoding UTF-8 -d out Solution.java Runner.java")),
                        COMPILE_TIMEOUT_MS);
                if (!compile.ok()) {
                    return new JudgeOutcome(SubmissionStatus.COMPILE_ERROR, 0, cases.size(), 0,
                            firstNonBlank(limit(compile.stderr(), MAX_FEEDBACK_LENGTH),
                                    limit(compile.stdout(), MAX_FEEDBACK_LENGTH)),
                            List.of());
                }
                program = List.of("java", "-Xmx" + heapMb(memoryLimitMb) + "m", "-Xss16m", "-cp", "out", "Runner");
            } else {
                Files.writeString(workspace.resolve("solution.py"), code, StandardCharsets.UTF_8);
                Files.writeString(workspace.resolve("runner.py"), pythonRunner(spec), StandardCharsets.UTF_8);
                // 函数模式要 import 同目录的 solution,不能用 -I 隔离模式
                program = List.of("python3", "-B", "runner.py");
            }

            List<CaseOutcome> outcomes = new ArrayList<>();
            int passed = 0;
            int totalRuntime = 0;
            SubmissionStatus overallStatus = SubmissionStatus.ACCEPTED;
            String errorMessage = null;
            int index = 0;
            for (QuestionTestCase testCase : cases) {
                index++;
                ExecResult run = runCaseWith(lang, workspace, image, memoryLimitMb, timeLimitMs,
                        testCase, program);
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
                boolean ok = sameJson(actual, testCase.getExpectedOutput());
                outcomes.add(new CaseOutcome(index, testCase.getInput(), testCase.getExpectedOutput(),
                        actual, ok, ok ? "通过" : "输出与期望不一致"));
                if (ok) {
                    passed++;
                } else if (overallStatus == SubmissionStatus.ACCEPTED) {
                    overallStatus = SubmissionStatus.WRONG_ANSWER;
                    errorMessage = "用例 " + index + " 输出与期望不一致";
                }
            }
            return new JudgeOutcome(overallStatus, passed, cases.size(), totalRuntime, errorMessage, outcomes);
        } catch (IOException e) {
            log.error("函数题判题失败 language={}", lang, e);
            return new JudgeOutcome(SubmissionStatus.SYSTEM_ERROR, 0, cases.size(), 0,
                    "判题服务异常：" + e.getMessage(), List.of());
        } finally {
            deleteQuietly(workspace);
        }
    }

    /** Java 测试驱动:读 JSON 参数 -> 调 Solution -> 打印 JSON 结果 */
    private static String javaRunner(FunctionSpec spec) {
        StringBuilder decls = new StringBuilder();
        StringBuilder args = new StringBuilder();
        List<String> types = spec.paramTypes();
        for (int i = 0; i < types.size(); i++) {
            String type = types.get(i);
            decls.append("        ").append(type).append(" p").append(i)
                    .append(" = Json.").append(reader(type)).append("(argv.get(").append(i).append("));\n");
            if (i > 0) {
                args.append(", ");
            }
            args.append('p').append(i);
        }
        return """
                import java.util.*;

                public class Runner {
                    @SuppressWarnings("unchecked")
                    public static void main(String[] args) throws Exception {
                        Scanner sc = new Scanner(System.in);
                        StringBuilder sb = new StringBuilder();
                        while (sc.hasNextLine()) {
                            sb.append(sc.nextLine()).append('\\n');
                        }
                        String raw = sb.toString().trim();
                        List<Object> argv = raw.isEmpty() ? new ArrayList<>() : Json.parse(raw);
                %s        Object result = new Solution().%s(%s);
                        System.out.println(Json.toJson(result));
                    }
                }

                %s
                """.formatted(decls.toString(), spec.method(), args.toString(), JSON_HELPER);
    }

    /** Python 测试驱动:json.loads 参数 -> 调 Solution 方法 -> json.dumps 输出 */
    private static String pythonRunner(FunctionSpec spec) {
        return """
                import json
                import sys

                from solution import Solution

                args = json.loads(sys.stdin.read().strip() or '[]')
                result = Solution().%s(*args)
                if isinstance(result, bool):
                    print('true' if result else 'false')
                else:
                    print(json.dumps(result, ensure_ascii=False, separators=(',', ':')))
                """.formatted(spec.method());
    }

    /** 类型 -> Json 读取方法名 */
    private static String reader(String type) {
        return switch (type) {
            case "int" -> "num";
            case "long" -> "lng";
            case "double" -> "dbl";
            case "boolean" -> "bool";
            case "char" -> "chr";
            case "String" -> "str";
            case "int[]" -> "ints";
            case "long[]" -> "longs";
            case "double[]" -> "doubles";
            case "boolean[]" -> "bools";
            case "char[]" -> "chars";
            case "String[]" -> "strs";
            case "int[][]" -> "ints2";
            case "char[][]" -> "chars2";
            case "String[][]" -> "strs2";
            case "List<Integer>" -> "intList";
            case "List<String>" -> "strList";
            case "List<List<Integer>>" -> "intList2";
            default -> "unsupported";
        };
    }

    /** 函数模式支持的基础类型,新增类型时同步 reader() 与 JSON_HELPER */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "int", "long", "double", "boolean", "char", "String",
            "int[]", "long[]", "double[]", "boolean[]", "char[]", "String[]",
            "int[][]", "char[][]", "String[][]",
            "List<Integer>", "List<String>", "List<List<Integer>>");

    /** 比对函数返回值:优先按 JSON 语义比较,退化为文本归一化比较 */
    static boolean sameJson(String actual, String expected) {
        String a = normalize(actual);
        String b = normalize(expected);
        if (a.equals(b)) {
            return true;
        }
        String compactA = a.replaceAll("\\s+", "");
        String compactB = b.replaceAll("\\s+", "");
        return compactA.equals(compactB);
    }

    /** 运行单个用例;超时用容器内 timeout 命令,宿主侧再做兜底 kill */
    private ExecResult runCase(JudgeLanguage lang, Path workspace, String image, int memoryLimitMb,
                               int timeLimitMs, QuestionTestCase testCase) throws IOException {
        return runCaseWith(lang, workspace, image, memoryLimitMb, timeLimitMs, testCase,
                defaultProgram(lang, memoryLimitMb));
    }

    private List<String> defaultProgram(JudgeLanguage lang, int memoryLimitMb) {
        return lang == JudgeLanguage.JAVA
                ? List.of("java", "-Xmx" + heapMb(memoryLimitMb) + "m", "-Xss16m", "-cp", "out", "Main")
                : List.of("python3", "-I", "-B", "main.py");
    }

    /** 用给定程序命令跑一个用例,input 来自用例的 input.txt */
    private ExecResult runCaseWith(JudgeLanguage lang, Path workspace, String image, int memoryLimitMb,
                                   int timeLimitMs, QuestionTestCase testCase, List<String> program)
            throws IOException {
        int seconds = Math.max(1, (timeLimitMs + 999) / 1000);
        Files.writeString(workspace.resolve("input.txt"),
                testCase.getInput() == null ? "" : testCase.getInput(), StandardCharsets.UTF_8);

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
            SubmissionStatus status,
            int passed,
            int total,
            int runtimeMs,
            String errorMessage,
            List<CaseOutcome> cases) {
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
