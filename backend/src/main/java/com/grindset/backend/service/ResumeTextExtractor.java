package com.grindset.backend.service;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.ResumeFileType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 简历文本抽取:PDFBox 抽 PDF、POI 抽 DOCX、直接解码 MD/TXT。
 * 只用确定性库拿纯文本,不经过 LLM;结果不落盘、不打日志。
 */
@Service
public class ResumeTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractor.class);

    /** 少于这个字数视为扫描件或空文件 */
    private static final int MIN_CHARS = 50;

    /** 控制 token 成本和响应时间 */
    private static final int MAX_CHARS = 15000;

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.ISO_8859_1);

    /** PDF 规范允许 %PDF- 出现在文件前 1024 字节内,部分工具会在前面加 BOM 或空行 */
    private static final int HEADER_SCAN_BYTES = 1024;

    /**
     * 只做文件名归一、后缀白名单和文件头校验,不抽全文。
     * 上传接口用它拿到类型后先落库/入队,把完整抽取留给解析线程。
     */
    public Preflight preflight(String originalName, Path file) {
        String fileName = baseName(originalName);
        ResumeFileType fileType = fileType(fileName);
        checkHeader(fileType, readHeader(file));
        return new Preflight(fileName, fileType);
    }

    /**
     * 从临时文件抽取文本,不在堆里保留整份文件。
     *
     * @param originalName 上传文件名(可能带路径)
     * @param file         已落盘的临时文件
     */
    public ExtractedText extract(String originalName, Path file) {
        Preflight preflight = preflight(originalName, file);
        String raw;
        try {
            raw = switch (preflight.fileType()) {
                case PDF -> extractPdf(file);
                case DOCX -> extractDocx(file);
                default -> decodeText(Files.readAllBytes(file));
            };
        } catch (IOException e) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "文件读取失败,请重新上传");
        }

        String cleaned = clean(raw);
        validate(cleaned);
        if (cleaned.length() > MAX_CHARS) {
            log.warn("简历文本超过 {} 字,已截断,type={}", MAX_CHARS, preflight.fileType());
            cleaned = cleaned.substring(0, MAX_CHARS);
        }
        return new ExtractedText(preflight.fileName(), preflight.fileType(), cleaned);
    }

    /** 读取文件头,用于防止改后缀上传 */
    private static byte[] readHeader(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(HEADER_SCAN_BYTES);
        } catch (IOException e) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "文件读取失败,请重新上传");
        }
    }

    /** 去掉路径,只保留文件名,并截断到数据库列宽 */
    private static String baseName(String originalName) {
        String name = originalName == null ? "" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.strip();
        if (name.isEmpty()) {
            name = "resume";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static ResumeFileType fileType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        ResumeFileType type = ResumeFileType.fromExtension(ext);
        if (type == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅支持 PDF、DOCX、MD、TXT,.doc 请另存为 .docx");
        }
        return type;
    }

    /** 文件头校验,防止把别的文件改后缀上传 */
    private static void checkHeader(ResumeFileType fileType, byte[] bytes) {
        boolean matched = switch (fileType) {
            // PDF 头部可能被 BOM/空行推后,在前 1024 字节内找到 %PDF- 即可
            case PDF -> contains(bytes, PDF_MAGIC);
            // DOCX 是 ZIP 包
            case DOCX -> bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B
                    && bytes[2] == 0x03 && bytes[3] == 0x04;
            default -> true;
        };
        if (!matched) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件内容与扩展名不符,请重新选择");
        }
    }

    private static boolean contains(byte[] bytes, byte[] needle) {
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            boolean hit = true;
            for (int j = 0; j < needle.length; j++) {
                if (bytes[i + j] != needle[j]) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                return true;
            }
        }
        return false;
    }

    private static String extractPdf(Path file) {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "PDF 已加密,请先解除密码后重试");
        } catch (IOException e) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "文件解析失败,请确认文件未损坏");
        }
    }

    /** 段落和表格都要抽,表格一行拼一行,避免项目经历丢失 */
    private static String extractDocx(Path file) {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    sb.append(paragraph.getText()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText().strip());
                        }
                        sb.append(String.join("\t", cells)).append('\n');
                    }
                }
            }
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "文件解析失败,请确认文件未损坏");
        }
    }

    /** UTF-8 严格解码,失败回退 GB18030(老简历常见) */
    private static String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    /** 统一换行、去 BOM 与零宽字符、去行尾空白、压缩连续空行 */
    private static String clean(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n')
                .replace("\uFEFF", "")
                .replaceAll("[\u200B-\u200D\u2060]", "");

        StringBuilder sb = new StringBuilder(text.length());
        int blankRun = 0;
        for (String line : text.split("\n", -1)) {
            String stripped = line.stripTrailing();
            if (stripped.isBlank()) {
                blankRun++;
                if (blankRun > 2) {
                    continue;
                }
                sb.append('\n');
            } else {
                blankRun = 0;
                sb.append(stripped).append('\n');
            }
        }
        return sb.toString().strip();
    }

    private static void validate(String text) {
        if (text.length() < MIN_CHARS) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "未提取到有效文字,疑似扫描件或图片版简历");
        }
        long garbled = text.chars().filter(c -> c == '\uFFFD').count();
        if (garbled * 10 > text.length()) {
            throw new BizException(ErrorCode.UNPROCESSABLE_ENTITY, "未能正确识别文件内容,请换一份简历重试");
        }
    }

    /**
     * 上传预检结果。
     */
    public record Preflight(String fileName, ResumeFileType fileType) {
    }

    /**
     * 抽取结果。
     *
     * @param fileName 清洗后的文件名
     * @param fileType PDF / DOCX / MD / TXT
     * @param text     清洗并截断后的正文
     */
    public record ExtractedText(String fileName, ResumeFileType fileType, String text) {
    }
}
