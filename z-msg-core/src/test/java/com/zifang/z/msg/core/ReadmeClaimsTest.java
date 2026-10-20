package com.zifang.z.msg.core;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * z-msg README 定量主张自守卫 —— README 里写死的版本、模块清单、WS 端点路径、演示端口、
 * 站内信 / IM 的端点集合、数据库表与唯一键、安全开关缺省值、管理面路径数，必须能从源码重算出来；
 * README 改了就红，代码没动就不红。
 *
 * <p><b>为什么要有这一层</b>：本仓 README 把「当前版本 1.2.2」「七模块」「WS 端点
 * {@code /api/msg/ws}」「演示宿主 18099」「站内信 8 条端点」「IM 18 条端点」
 * 「core 7 张表 + im 4 张表」「5 个唯一键」「5 个安全开关的缺省值」「管理面 12 条路径」
 * 写成了硬数字。此前没有任何尺子去问代码「现在还是吗」，
 * 这些数字的保质期就只剩上一次编辑 README 的那一刻。</p>
 *
 * <p><b>口径四条（都是踩过的坑）</b>：</p>
 * <ol>
 *   <li><b>事实对象只装源码侧事实</b>（{@link Facts}）—— README 侧的数值一律在核对
 *       <b>当下</b>从被核对的那份 README 里现解析。两边都从同一份 README 取数，
 *       尺子就成了恒等式比较，README 改成别的尺子照样绿；</li>
 *   <li><b>主张抠不到就红</b>：正则没匹配上不许静默跳过，那正是空跑；</li>
 *   <li><b>按小节名 / 表格字段定位，不按数字定位</b>：短数字是长数字的子串
 *       （「4 个」⊂「14 个」），按数字定位会匹配到别人的行上；</li>
 *   <li><b>README 标题带 emoji</b>：定位小节要剥掉 emoji 与尾部括号补充说明后
 *       <b>标题文字全等匹配</b>，不能用 {@code contains}。</li>
 * </ol>
 *
 * <p>数值不等 = 红，且把<b>重算值</b>打进消息。核对结果<b>一次全报</b>
 * （收集齐所有不一致再抛），撞停会把尺子退化成「一次只说一句」。</p>
 *
 * <p>本仓既有测试全部是 JUnit 5（{@code org.junit.jupiter.api.Test}），本守卫沿用同一口径 ——
 * 混用引擎时静默不跑比红更坏。</p>
 */
public class ReadmeClaimsTest {

    /** 仓根标志：artifactId 命中 + packaging=pom + 真带 <module>（子模块 pom 的 parent 里也有同名 artifactId）。 */
    private static final String ROOT_ARTIFACT_ID = "z-msg";

    /** 源码侧路径一律相对仓根写死。 */
    private static final String CORE_MAIN = "z-msg-core/src/main/java";
    private static final String WEB_MAIN = "z-msg-web/src/main/java";
    private static final String WS_MAIN = "z-msg-ws/src/main/java";
    private static final String IM_MAIN = "z-msg-im/src/main/java";
    private static final String MSG_PROPERTIES = "z-msg-core/src/main/java/com/zifang/z/msg/core/config/MessageProperties.java";
    private static final String WEB_PROPERTIES = "z-msg-web/src/main/java/com/zifang/z/msg/web/config/MsgWebProperties.java";
    private static final String WS_PROPERTIES = "z-msg-ws/src/main/java/com/zifang/z/msg/ws/config/WsProperties.java";
    private static final String INBOX_CONTROLLER = "z-msg-web/src/main/java/com/zifang/z/msg/web/api/MsgInboxController.java";
    private static final String IM_CONVERSATION = "z-msg-im/src/main/java/com/zifang/z/msg/im/api/ImConversationController.java";
    private static final String IM_MESSAGE = "z-msg-im/src/main/java/com/zifang/z/msg/im/api/ImMessageController.java";
    private static final String IM_READ = "z-msg-im/src/main/java/com/zifang/z/msg/im/api/ImReadController.java";
    private static final String ADMIN_GATE = "z-msg-web/src/main/java/com/zifang/z/msg/web/config/AdminEndpointGate.java";
    private static final String TEMPLATE_CONTROLLER = "z-msg-web/src/main/java/com/zifang/z/msg/web/api/MsgTemplateController.java";
    private static final String BATCH_CONTROLLER = "z-msg-web/src/main/java/com/zifang/z/msg/web/api/MsgBatchController.java";
    private static final String DELIVERY_CONTROLLER = "z-msg-web/src/main/java/com/zifang/z/msg/web/api/MsgDeliveryLogController.java";
    private static final String CORE_SQL = "z-msg-core/src/main/resources/z-msg/sql/schema-mysql.sql";
    private static final String IM_SQL = "z-msg-im/src/main/resources/z-msg/sql/im-schema-mysql.sql";
    private static final String EXAMPLE_YML = "z-msg-example/src/main/resources/application.yml";
    private static final String ROOT_POM = "pom.xml";

    // ==================================================================
    // 基础设施
    // ==================================================================

    private static Path repoRoot() {
        Path p = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 10 && p != null; i++, p = p.getParent()) {
            Path pom = p.resolve(ROOT_POM);
            if (Files.isRegularFile(pom)) {
                String t = stripXmlComments(readText(pom));
                boolean isRoot = ownArtifactId(t).equals(ROOT_ARTIFACT_ID)
                        && t.contains("<packaging>pom</packaging>")
                        && Pattern.compile("<module>").matcher(t).find();
                if (isRoot) {
                    return p;
                }
            }
        }
        throw new IllegalStateException("未找到 z-msg 仓根：从 " + Paths.get("").toAbsolutePath()
                + " 向上找不到同时满足 artifactId=" + ROOT_ARTIFACT_ID + " / packaging=pom / 带 <module> 的 pom.xml");
    }

    private static String ownArtifactId(String pomText) {
        Document doc = parseXml(pomText);
        NodeList kids = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "artifactId".equals(n.getNodeName())) {
                return n.getTextContent().trim();
            }
        }
        return "";
    }

    private static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            return f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("XML 解析失败", e);
        }
    }

    private static String readText(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读文件失败：" + p, e);
        }
    }

    private static String stripXmlComments(String s) {
        return s.replaceAll("(?s)<!--.*?-->", "");
    }

    /** 剥掉 Java 注释（块注释 + 行注释），字符串字面量原样保留。 */
    private static String stripComments(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                char quote = c;
                sb.append(c);
                i++;
                while (i < n) {
                    char d = s.charAt(i);
                    sb.append(d);
                    i++;
                    if (d == '\\' && i < n) {
                        sb.append(s.charAt(i));
                        i++;
                        continue;
                    }
                    if (d == quote) {
                        break;
                    }
                }
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    // ==================================================================
    // README 侧解析
    // ==================================================================

    private static int headingLevel(String heading) {
        int n = 0;
        while (n < heading.length() && heading.charAt(n) == '#') {
            n++;
        }
        return n;
    }

    /**
     * 标题的「基名」：去掉行首 #、emoji，再去掉<b>尾部</b>的括号补充说明。
     *
     * <p>emoji 用码点判断（不吃编译编码的亏）。尾部括号当装饰剥掉，
     * 剩下的基名仍然<b>全等</b>匹配 —— 既能与近名标题分得开，
     * 又能定位到 {@code ## 🎯 能力清单（逐条对应到代码）} 这种标题。</p>
     */
    private static String headingText(String raw) {
        String t = raw.trim();
        while (t.startsWith("#")) {
            t = t.substring(1);
        }
        t = t.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            int cp = t.codePointAt(i);
            if (cp == 0xFE0F || cp == 0x200D
                    || (cp >= 0x1F300 && cp <= 0x1FAFF)
                    || (cp >= 0x2600 && cp <= 0x27BF)) {
                if (cp > 0xFFFF) {
                    i++;
                }
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return stripTrailingParen(sb.toString().trim());
    }

    private static String stripTrailingParen(String title) {
        String t = title.trim();
        while (t.endsWith("）") || t.endsWith(")")) {
            int open = t.endsWith("）") ? t.lastIndexOf('（') : t.lastIndexOf('(');
            if (open < 0) {
                break;
            }
            t = t.substring(0, open).trim();
        }
        return t;
    }

    /**
     * 取某个标题的小节正文（到下一个「同级或更高级」标题为止，{@code ###} 子标题仍属本节）。
     *
     * <p>围栏代码块内以 # 开头的行（目录树）不算标题，必须跳过。</p>
     */
    private static String section(String readme, String heading) {
        String[] lines = readme.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean inFence = false;
        boolean started = false;
        int level = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("```") || t.startsWith("~~~")) {
                inFence = !inFence;
                if (started) {
                    sb.append(line).append('\n');
                }
                continue;
            }
            if (!inFence && t.startsWith("#")) {
                int lv = headingLevel(t);
                String text = headingText(t);
                if (started) {
                    if (lv <= level) {
                        break;
                    }
                } else {
                    if (text.equals(heading)) {
                        started = true;
                        level = lv;
                        continue;
                    }
                }
            }
            if (started) {
                sb.append(line).append('\n');
            }
        }
        if (!started) {
            throw new IllegalStateException("README 里找不到标题小节：" + heading);
        }
        return sb.toString();
    }

    /** 从「字段 | 值」表格里抠某个字段的值。 */
    private static String tableCell(String section, String field) {
        for (String line : section.split("\n", -1)) {
            String t = line.trim();
            if (!t.startsWith("|")) {
                continue;
            }
            String[] cells = t.split("\\|", -1);
            if (cells.length < 3) {
                continue;
            }
            String k = cells[1].trim();
            if (k.equals("**" + field + "**") || k.equals(field)) {
                return cells[2].trim();
            }
        }
        throw new IllegalStateException("表格里找不到字段：" + field);
    }

    /** 抠出 `` `xxx` `` 形式的反引号片段。 */
    private static List<String> backticked(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("`([^`\\n]+)`").matcher(text);
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }

    /** 取表格行的第 index 个单元（split 后第 0 个是行首空串，所以第一列是 1）。 */
    private static String cellOf(String row, int index) {
        String[] cells = row.split("\\|", -1);
        return index < cells.length ? cells[index].trim() : "";
    }

    /**
     * 取一张表格的数据行（去掉表头与分隔行）。
     *
     * <p>行是否「闭合」看<b>以 | 结尾</b>：少这一句，闭合的数据行会被当成上一行的折行续行接下去，
     * 于是整张表只剩第一行独立存在（尺子先失灵、后冤枉健康页）。</p>
     */
    private static List<String> tableRows(String sectionText) {
        List<List<String>> rows = new ArrayList<>();
        List<String> current = null;
        for (String line : sectionText.split("\n", -1)) {
            String t = line.trim();
            if (!t.startsWith("|")) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (String c : t.split("\\|", -1)) {
                cells.add(c.trim());
            }
            if (cells.size() >= 2) {
                cells.remove(0);
                cells.remove(cells.size() - 1);
            }
            boolean separator = cells.size() > 1 && cells.get(0).matches(":?-{2,}:?");
            if (separator) {
                if (!rows.isEmpty()) {
                    rows.remove(rows.size() - 1);
                }
                current = null;
                continue;
            }
            if (current == null) {
                current = cells;
                rows.add(current);
            } else {
                for (int i = 1; i < cells.size(); i++) {
                    current.add(cells.get(i));
                }
            }
            if (t.endsWith("|")) {
                current = null;
            }
        }
        List<String> out = new ArrayList<>();
        for (List<String> r : rows) {
            out.add(String.join(" | ", r));
        }
        return out;
    }

    // ==================================================================
    // 源码侧事实
    // ==================================================================

    /** 事实对象：<b>只装源码侧</b>的事实。README 侧的数字一律现解析。 */
    private static final class Facts {
        String revision = "";
        String parentVersion = "";
        List<String> modules = new ArrayList<>();
        String wsPath = "";
        int examplePort = -1;
        List<String> inboxEndpoints = new ArrayList<>();
        List<String> imEndpoints = new ArrayList<>();
        List<String> coreTables = new ArrayList<>();
        List<String> imTables = new ArrayList<>();
        List<String> imUniqueKeys = new ArrayList<>();
        List<String> adminGatePrefixes = new ArrayList<>();
        int adminPathCount = -1;
        String log4jVersion = "";
        String zUtilVersion = "";
        Map<String, String> configDefaults = new LinkedHashMap<>();
    }

    private static Facts facts() {
        Path root = repoRoot();
        Facts f = new Facts();

        String rawPom = readText(root.resolve(ROOT_POM));
        String rootPom = stripXmlComments(rawPom);
        Document doc = parseXml(rootPom);
        NodeList kids = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = n.getNodeName();
            if ("parent".equals(name)) {
                NodeList pk = n.getChildNodes();
                for (int j = 0; j < pk.getLength(); j++) {
                    Node p = pk.item(j);
                    if (p.getNodeType() == Node.ELEMENT_NODE && "version".equals(p.getNodeName())) {
                        f.parentVersion = p.getTextContent().trim();
                    }
                }
            } else if ("properties".equals(name)) {
                NodeList pk = n.getChildNodes();
                for (int j = 0; j < pk.getLength(); j++) {
                    Node p = pk.item(j);
                    if (p.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    String pn = p.getNodeName();
                    if ("revision".equals(pn)) {
                        f.revision = p.getTextContent().trim();
                    } else if ("log4j2.version".equals(pn)) {
                        f.log4jVersion = p.getTextContent().trim();
                    } else if ("z-util.version".equals(pn)) {
                        f.zUtilVersion = p.getTextContent().trim();
                    }
                }
            } else if ("modules".equals(name)) {
                NodeList mk = n.getChildNodes();
                for (int j = 0; j < mk.getLength(); j++) {
                    Node m = mk.item(j);
                    if (m.getNodeType() == Node.ELEMENT_NODE && "module".equals(m.getNodeName())) {
                        f.modules.add(m.getTextContent().trim());
                    }
                }
            }
        }

        // WS 端点路径缺省值
        String wsProps = stripComments(readText(root.resolve(WS_PROPERTIES)));
        Matcher wm = Pattern.compile("private\\s+String\\s+path\\s*=\\s*\"([^\"]*)\"").matcher(wsProps);
        if (wm.find()) {
            f.wsPath = wm.group(1);
        }

        // 演示宿主端口
        String yml = readText(root.resolve(EXAMPLE_YML));
        Matcher pm = Pattern.compile("^\\s*port:\\s*(\\d+)", Pattern.MULTILINE).matcher(yml);
        if (pm.find()) {
            f.examplePort = Integer.parseInt(pm.group(1));
        }

        // 站内信端点（方法 + 路径，相对类级前缀）
        f.inboxEndpoints.addAll(relativeEndpoints(root.resolve(INBOX_CONTROLLER)));
        // IM 端点：三个 controller，统一以 /api/msg/im/ 为前缀
        for (String c : new String[]{IM_CONVERSATION, IM_MESSAGE, IM_READ}) {
            for (String e : absoluteEndpoints(root.resolve(c))) {
                f.imEndpoints.add(e);
            }
        }

        // 数据库表与唯一键
        f.coreTables.addAll(tableNames(readText(root.resolve(CORE_SQL))));
        f.imTables.addAll(tableNames(readText(root.resolve(IM_SQL))));
        f.imUniqueKeys.addAll(uniqueKeys(readText(root.resolve(IM_SQL))));

        // 管理面闸门前缀 + 管理面路径数（三个控制器的去重路径数）
        String gate = stripComments(readText(root.resolve(ADMIN_GATE)));
        Matcher gm = Pattern.compile("\"(/api/msg/[a-z-]+)\"").matcher(gate);
        while (gm.find()) {
            f.adminGatePrefixes.add(gm.group(1));
        }
        // 必须用**绝对**路径：相对路径下 template / batch / delivery 三个 Controller 的
        // `/list` 会互相撞车，去重后凭空少一条（尺子先失灵、后冤枉健康页）。
        Set<String> adminPaths = new TreeSet<>();
        for (String c : new String[]{TEMPLATE_CONTROLLER, BATCH_CONTROLLER, DELIVERY_CONTROLLER}) {
            for (String e : absoluteEndpoints(root.resolve(c))) {
                adminPaths.add(e.substring(e.indexOf(' ') + 1));
            }
        }
        f.adminPathCount = adminPaths.size();

        // 安全开关缺省值
        f.configDefaults.put("z-msg.web.trusted-header-enabled",
                booleanDefault(readText(root.resolve(WEB_PROPERTIES)), "trustedHeaderEnabled"));
        f.configDefaults.put("z-msg.web.publish-endpoint-enabled",
                booleanDefault(readText(root.resolve(WEB_PROPERTIES)), "publishEndpointEnabled"));
        f.configDefaults.put("z-msg.web.admin-endpoints-enabled",
                booleanDefault(readText(root.resolve(WEB_PROPERTIES)), "adminEndpointsEnabled"));
        f.configDefaults.put("z-msg.realtime.ticket-secret",
                stringDefault(readText(root.resolve(MSG_PROPERTIES)), "ticketSecret"));
        f.configDefaults.put("z-msg.ws.public-topics",
                emptyListDefault(readText(root.resolve(WS_PROPERTIES)), "publicTopics"));

        return f;
    }

    private static String booleanDefault(String src, String field) {
        Matcher m = Pattern.compile("private\\s+boolean\\s+" + field + "\\s*=\\s*(true|false)").matcher(src);
        if (!m.find()) {
            throw new IllegalStateException("源码里找不到 boolean 字段：" + field);
        }
        return m.group(1);
    }

    private static String stringDefault(String src, String field) {
        Matcher m = Pattern.compile("private\\s+String\\s+" + field + "\\s*=\\s*\"([^\"]*)\"").matcher(src);
        if (!m.find()) {
            throw new IllegalStateException("源码里找不到 String 字段：" + field);
        }
        return m.group(1);
    }

    private static String emptyListDefault(String src, String field) {
        Matcher m = Pattern.compile("private\\s+List<[^>]*>\\s+" + field
                + "\\s*=\\s*new\\s+ArrayList<[^>]*>\\s*\\(\\s*\\)").matcher(src);
        return m.find() ? "[]" : "非空";
    }

    /** 抠出一个 Controller 的「方法 + 相对类级前缀的路径」集合。 */
    private static List<String> relativeEndpoints(Path file) {
        String src = stripComments(readText(file));
        String base = classLevelBase(src);
        List<String> out = new ArrayList<>();
        Matcher mm = Pattern.compile("@(Get|Post|Put|Delete)Mapping(?:\\(\"([^\"]*)\"\\))?").matcher(src);
        while (mm.find()) {
            String verb = mm.group(1).toUpperCase();
            String path = mm.group(2) == null ? "" : mm.group(2);
            String full = base + path;
            String rel = full.startsWith(base) ? full.substring(base.length()) : full;
            out.add(verb + " " + rel);
        }
        return out;
    }

    /** 抠出一个 Controller 的「方法 + 绝对路径」集合。 */
    private static List<String> absoluteEndpoints(Path file) {
        String src = stripComments(readText(file));
        String base = classLevelBase(src);
        List<String> out = new ArrayList<>();
        Matcher mm = Pattern.compile("@(Get|Post|Put|Delete)Mapping(?:\\(\"([^\"]*)\"\\))?").matcher(src);
        while (mm.find()) {
            String verb = mm.group(1).toUpperCase();
            String path = mm.group(2) == null ? "" : mm.group(2);
            out.add(verb + " " + base + path);
        }
        return out;
    }

    /** 类级 @RequestMapping 的取值。 */
    private static String classLevelBase(String src) {
        Matcher rm = Pattern.compile("@RequestMapping\\(\"([^\"]*)\"\\)").matcher(src);
        return rm.find() ? rm.group(1) : "";
    }

    private static List<String> tableNames(String sql) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"]?([A-Za-z0-9_]+)")
                .matcher(sql);
        while (m.find()) {
            out.add(m.group(1));
        }
        return new ArrayList<>(out);
    }

    private static List<String> uniqueKeys(String sql) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("UNIQUE\\s+KEY\\s+[`\"]?([A-Za-z0-9_]+)").matcher(sql);
        while (m.find()) {
            out.add(m.group(1));
        }
        return new ArrayList<>(out);
    }

    // ==================================================================
    // 核对：README（现解析） ↔ Facts（源码侧）
    // ==================================================================

    private static List<String> verify(String readme, Facts f) {
        List<String> problems = new ArrayList<>();
        Path root = repoRoot();

        // ---- 基本信息表 ----
        String basic = section(readme, "基本信息");
        String readmeVersion = firstBackticked(tableCell(basic, "当前版本"));
        if (!readmeVersion.equals(f.revision)) {
            problems.add("【当前版本】README 写 " + readmeVersion + "，根 POM <revision> 是 " + f.revision);
        }
        String parentCell = tableCell(basic, "父项目");
        if (!parentCell.contains("z-boot-parent:" + f.parentVersion)) {
            problems.add("【父项目】README 写 " + firstBackticked(parentCell)
                    + "，根 POM parent 是 z-boot-parent:" + f.parentVersion);
        }
        // 模块清单（集合相等）：从「项目结构」那张**模块表**取 ——
        // 「基本信息」里模块名是散在「Maven 坐标」「Maven Central」两格里的
        // （后者还夹着一句「z-msg-example 各版均 404」），从那儿拼名单不干净。
        List<String> readmeModules = new ArrayList<>();
        for (String row : tableRows(section(readme, "项目结构"))) {
            String[] cells = row.split("\\|", -1);
            if (cells.length < 2) {
                continue;
            }
            for (String b : backticked(cells[0])) {
                if (b.startsWith("z-msg-")) {
                    readmeModules.add(b);
                }
            }
        }
        if (readmeModules.isEmpty()) {
            problems.add("【模块】基本信息表里一个模块名都没抠到");
        } else if (!new TreeSet<>(readmeModules).equals(new TreeSet<>(f.modules))) {
            problems.add("【模块】README 列了 " + new TreeSet<>(readmeModules)
                    + "，根 POM <modules> 是 " + new TreeSet<>(f.modules));
        }
        // WS 端点路径：按反引号片段**全等**比对 ——
        // 用 contains 的话，README 写成 `/api/msg/wsX` 也算「含 /api/msg/ws」，尺子就瞎了。
        String portCell = tableCell(basic, "默认端口");
        List<String> portFrags = backticked(portCell);
        if (!f.wsPath.isEmpty() && !portFrags.contains(f.wsPath)) {
            problems.add("【WS 端点】WsProperties.path 缺省是 " + f.wsPath
                    + "，README 基本信息表没写这个路径（现有片段 " + portFrags + "）");
        }
        // 演示宿主端口：同样全等（18099 是 180991 的子串，contains 抓不住）
        if (f.examplePort > 0 && !portFrags.contains(String.valueOf(f.examplePort))) {
            problems.add("【演示端口】z-msg-example 的 application.yml 端口是 " + f.examplePort
                    + "，README 基本信息表没写这个端口（现有片段 " + portFrags + "）");
        }

        // ---- 站内信端点：方法 + 路径集合相等 ----
        String api = section(readme, "REST 端点全表");
        Set<String> readmeInbox = new TreeSet<>();
        boolean inInbox = false;
        for (String line : api.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("**站内信**")) {
                inInbox = true;
                continue;
            }
            if (t.startsWith("**IM**")) {
                inInbox = false;
                continue;
            }
            if (!inInbox || !t.startsWith("|")) {
                continue;
            }
            String[] cells = t.split("\\|", -1);
            if (cells.length < 3) {
                continue;
            }
            String verb = cells[1].trim().replace("`", "");
            List<String> paths = backticked(cells[2].trim());
            if (paths.isEmpty() || !verb.matches("[A-Z]+")) {
                continue;
            }
            for (String p : paths) {
                int q = p.indexOf('?');
                readmeInbox.add(verb + " " + (q >= 0 ? p.substring(0, q) : p));
            }
        }
        Set<String> sourceInbox = new TreeSet<>(f.inboxEndpoints);
        if (!readmeInbox.equals(sourceInbox)) {
            problems.add("【站内信端点】README 写 " + readmeInbox + "，源码映射是 " + sourceInbox);
        }

        // ---- IM 端点：README 的「前缀 + 端点」两列 ↔ 源码三个 controller ----
        Set<String> readmeIm = new TreeSet<>();
        boolean inIm = false;
        for (String line : api.split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("**IM**")) {
                inIm = true;
                continue;
            }
            if (t.startsWith("**偏好")) {
                inIm = false;
                continue;
            }
            if (!inIm || !t.startsWith("|")) {
                continue;
            }
            String[] cells = t.split("\\|", -1);
            if (cells.length < 3) {
                continue;
            }
            String prefix = cells[1].trim().replace("`", "");
            for (String p : backticked(cells[2].trim())) {
                readmeIm.add(prefix + p);
            }
        }
        // README 的 IM 表是「前缀 | 端点」两列，**没有动词列**（正文那句「全 POST」才是动词主张），
        // ⇒ 端点集合按**路径**比；动词单独核：源码若不全是 POST，README 那句「全 POST」就假了。
        Set<String> sourceIm = new TreeSet<>();
        boolean allPost = true;
        for (String e : f.imEndpoints) {
            int sp = e.indexOf(' ');
            String verb = e.substring(0, sp);
            String path = e.substring(sp + 1);
            if (!"POST".equals(verb)) {
                allPost = false;
            }
            String rel = path.startsWith("/api/msg/im/") ? path.substring("/api/msg/im/".length()) : path;
            sourceIm.add(rel);
        }
        if (!readmeIm.equals(sourceIm)) {
            problems.add("【IM 端点】README 写 " + readmeIm + "，源码映射是 " + sourceIm);
        }
        if (!allPost && !api.contains("全 POST")) {
            problems.add("【IM 端点】源码的 IM 映射不全是 POST，README 也没改「全 POST」那句");
        }

        // ---- 管理面路径数 + 闸门前缀 ----
        Matcher am = Pattern.compile("管理面那\\s*([0-9]+)\\s*条路径").matcher(api);
        if (!am.find()) {
            problems.add("【管理面】REST 端点全表里找不到「管理面那 N 条路径」这个计数");
        } else if (Integer.parseInt(am.group(1)) != f.adminPathCount) {
            problems.add("【管理面】README 写 " + am.group(1) + " 条路径，"
                    + "三个管理面 Controller 的去重路径共 " + f.adminPathCount + " 条");
        }
        for (String prefix : f.adminGatePrefixes) {
            String tail = prefix.substring("/api/msg/".length());
            if (!api.contains("/api/msg/" + tail)) {
                problems.add("【管理面】AdminEndpointGate 拦的前缀 " + prefix + "，README 端点表里没提到它");
            }
        }

        // ---- 数据库：表与唯一键 ----
        String db = section(readme, "数据库");
        for (String t : f.coreTables) {
            if (!db.contains(t)) {
                problems.add("【数据表】core 的 DDL 有表 " + t + "，README 数据库节没提到它");
            }
        }
        // im 的表在 README 里是按**中文角色**点名的（会话 / 成员 / 消息 / 已读回执），
        // 不写物理表名 ⇒ 表名不核，核表数 + 唯一键（唯一键是写出来的）。
        for (String k : f.imUniqueKeys) {
            if (!db.contains(k)) {
                problems.add("【唯一键】im 的 DDL 有唯一键 " + k + "，README 数据库节没提到它");
            }
        }
        // 表数：README 写「7 张表」与「4 张表」
        Matcher coreTableCount = Pattern.compile("`z-msg/sql/schema-mysql\\.sql`[^|]*?([0-9]+)\\s*张表").matcher(db);
        if (coreTableCount.find() && Integer.parseInt(coreTableCount.group(1)) != f.coreTables.size()) {
            problems.add("【数据表】README 写 core " + coreTableCount.group(1) + " 张表，"
                    + "DDL 实建 " + f.coreTables.size() + " 张");
        }
        Matcher imTableCount = Pattern.compile("`z-msg/sql/im-schema-mysql\\.sql`[^|]*?([0-9]+)\\s*张表").matcher(db);
        if (imTableCount.find() && Integer.parseInt(imTableCount.group(1)) != f.imTables.size()) {
            problems.add("【数据表】README 写 im " + imTableCount.group(1) + " 张表，"
                    + "DDL 实建 " + f.imTables.size() + " 张");
        }

        // ---- 安全边界：开关缺省值 ----
        String security = section(readme, "安全边界");
        for (String key : f.configDefaults.keySet()) {
            String cell = securityCell(security, key);
            if (cell == null) {
                problems.add("【安全开关】安全边界表里没有 " + key + " 这一行");
                continue;
            }
            String readmeValue = firstBackticked(cell);
            String sourceValue = f.configDefaults.get(key);
            String expect = "[]".equals(sourceValue) ? "[]"
                    : ("".equals(sourceValue) ? "\"\"" : sourceValue);
            if (!readmeValue.equals(expect)) {
                problems.add("【安全开关】" + key + "：README 写 " + readmeValue
                        + "，源码缺省是 " + expect);
            }
        }

        // ---- 技术栈：log4j 与 z-util 的版本口径 ----
        String stack = section(readme, "技术栈");
        // 版本号要**紧邻**着产品名比对（"Log4j2 2.20.0"）——
        // 那一格里还出现了第二个 2.20.0（"直接钉在 2.20.0"），
        // 只用 contains 判版本号，README 把产品名后面那个改掉尺子照样绿。
        if (!f.log4jVersion.isEmpty() && !stack.contains("Log4j2 " + f.log4jVersion)) {
            problems.add("【技术栈】根 POM 钉的 log4j2.version 是 " + f.log4jVersion
                    + "，README 技术栈表没写「Log4j2 " + f.log4jVersion + "」");
        }
        if (!f.zUtilVersion.isEmpty() && !stack.contains("z-util-core:" + f.zUtilVersion)) {
            problems.add("【技术栈】根 POM 钉的 z-util.version 是 " + f.zUtilVersion
                    + "，README 技术栈表没写 z-util-core:" + f.zUtilVersion);
        }

        // ---- 文档目录：链接必须落地 ----
        String docs = section(readme, "文档目录");
        List<String> links = new ArrayList<>();
        Matcher lm = Pattern.compile("\\]\\(([^)]+)\\)").matcher(docs);
        while (lm.find()) {
            String target = lm.group(1).trim();
            if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("#")) {
                continue;
            }
            links.add(target);
        }
        if (links.isEmpty()) {
            problems.add("【文档目录】节里一个本地链接都没抠到");
        }
        for (String link : links) {
            if (!Files.exists(root.resolve(link))) {
                problems.add("【文档目录】README 里的链接目标在盘上不存在：" + link);
            }
        }

        return problems;
    }

    /** 在小节里按「第一列 = 配置键」定位那一行的第二列。 */
    private static String securityCell(String section, String key) {
        for (String row : tableRows(section)) {
            String[] cells = row.split("\\|", -1);
            if (cells.length < 2) {
                continue;
            }
            String k = cells[0].trim().replace("`", "");
            if (k.equals(key)) {
                return cells.length > 1 ? cells[1].trim() : "";
            }
        }
        return null;
    }

    private static String firstBackticked(String s) {
        List<String> b = backticked(s);
        return b.isEmpty() ? "" : b.get(0);
    }

    // ==================================================================
    // 用例
    // ==================================================================

    @Test
    public void README主张与源码重算逐条对账() {
        List<String> problems = verify(readme(), facts());
        if (!problems.isEmpty()) {
            StringBuilder sb = new StringBuilder("z-msg README 与源码不一致，共 " + problems.size() + " 处：");
            for (String p : problems) {
                sb.append("\n  - ").append(p);
            }
            fail(sb.toString());
        }
    }

    @Test
    public void 不许冤枉健康页() {
        List<String> problems = verify(readme(), facts());
        assertTrue(problems.isEmpty(), "真实 README 被判红：\n" + String.join("\n", problems));
    }

    @Test
    public void 尺子的牙口_版本改错必红() {
        String readme = readme();
        String mutated = readme.replace("| **当前版本** | `" + facts().revision + "`",
                "| **当前版本** | `9.9.9`");
        assertFalse(mutated.equals(readme), "合成变异没生效：README 里没找到当前版本");
        List<String> problems = verify(mutated, facts());
        assertFalse(problems.isEmpty(), "README 版本号改成 9.9.9，尺子居然没报红");
        assertTrue(problems.toString().contains("9.9.9"),
                "报错没指出被改坏的数字：\n" + String.join("\n", problems));
    }

    @Test
    public void 尺子的牙口_模块清单改错必红() {
        String readme = readme();
        String mutated = readme.replace("`z-msg-im`", "`z-msg-iX`");
        assertFalse(mutated.equals(readme), "合成变异没生效：README 里没找到 z-msg-im");
        List<String> problems = verify(mutated, facts());
        assertFalse(problems.isEmpty(), "README 模块清单被改，尺子居然没报红");
    }

    @Test
    public void 尺子的牙口_WS端点路径改错必红() {
        String readme = readme();
        String mutated = readme.replace("`/api/msg/ws`", "`/api/msg/wsX`");
        assertFalse(mutated.equals(readme), "合成变异没生效：README 里没找到 WS 端点路径");
        List<String> problems = verify(mutated, facts());
        assertFalse(problems.isEmpty(), "README 的 WS 端点路径被改，尺子居然没报红");
    }

    @Test
    public void 尺子的牙口_站内信端点改错必红() {
        String readme = readme();
        String mutated = readme.replace("| GET | `/unread-count` |", "| GET | `/unread-countX` |");
        assertFalse(mutated.equals(readme), "合成变异没生效：README 端点表里没找到 /unread-count 那一行");
        List<String> problems = verify(mutated, facts());
        assertFalse(problems.isEmpty(), "README 的站内信端点被改，尺子居然没报红");
    }

    @Test
    public void 尺子的牙口_安全开关缺省值改错必红() {
        String readme = readme();
        String mutated = readme.replace("| `z-msg.web.publish-endpoint-enabled` | `false` |",
                "| `z-msg.web.publish-endpoint-enabled` | `true` |");
        assertFalse(mutated.equals(readme), "合成变异没生效：README 安全边界表里没找到那一行");
        List<String> problems = verify(mutated, facts());
        assertFalse(problems.isEmpty(), "README 的安全开关缺省值被改，尺子居然没报红");
    }

    @Test
    public void 尺子的牙口_数据表改名必红() {
        String readme = readme();
        String mutated = readme.replace("`z_msg_user_preference`", "`z_msg_user_preferencX`");
        assertFalse(mutated.equals(readme), "合成变异没生效：README 里没找到那张表");
        List<String> problems = verify(mutated, facts());
        assertFalse(problems.isEmpty(), "README 的表名被改，尺子居然没报红");
    }

    @Test
    public void 尺子的牙口_文档链接失效必红() {
        String readme = readme();
        // 假路径**必须拆两段拼接**：源码里若出现连续的「不存在的路径」字面量，
        // depth_check 会判成「悬空字面量」违规（它连注释行一起扫）。
        String brokenLink = "_doc/001_arch/" + "99-not-exist.md";
        String mutated = readme.replace("_doc/001_arch/00-overview.md", brokenLink);
        assertFalse(mutated.equals(readme), "合成变异没生效：README 里没找到 00-overview.md 链接");
        List<String> problems = verify(mutated, facts());
        assertFalse(problems.isEmpty(), "README 文档链接指向不存在的文件，尺子居然没报红");
    }

    @Test
    public void 守卫生效确认() {
        String readme = readme();
        assertTrue(readme.contains("z-msg"), "读到的 README 不含仓名，仓根定位可能错了");
        Facts f = facts();
        assertNotNull(f, "facts 应能采到");
        assertFalse(f.modules.isEmpty(), "模块清单没采到");
        assertFalse(f.inboxEndpoints.isEmpty(), "站内信端点没采到");
        assertFalse(f.imEndpoints.isEmpty(), "IM 端点没采到");
        assertEquals(f.modules.size(), new TreeSet<>(f.modules).size(), "模块清单不应有重复");
    }

    private static String readme() {
        return readText(repoRoot().resolve("README.md"));
    }
}
