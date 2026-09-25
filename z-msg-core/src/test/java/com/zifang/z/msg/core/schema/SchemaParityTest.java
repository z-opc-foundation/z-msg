package com.zifang.z.msg.core.schema;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zifang.z.msg.core.domain.entity.InAppMessage;
import com.zifang.z.msg.core.domain.entity.MsgBatchTaskDO;
import com.zifang.z.msg.core.domain.entity.MsgDeliveryLogDO;
import com.zifang.z.msg.core.domain.entity.MsgTemplateDO;
import com.zifang.z.msg.core.domain.entity.MsgTemplateI18nDO;
import com.zifang.z.msg.core.domain.entity.MsgTemplateVersionDO;
import com.zifang.z.msg.core.domain.entity.MsgUserPreferenceDO;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构契约测试。z-msg 从来没有过 DDL，实体与库表的一致性完全靠人眼，
 * 这份测试把三件容易悄悄腐坏的事钉住：
 * <ol>
 *   <li>MySQL 与 H2 两份 schema 必须定义同一批表、同一批索引（只改一份是最常见的事故）；</li>
 *   <li>H2 那份必须真能在 H2 上跑起来（文本一致但语法不合法同样致命）；</li>
 *   <li>实体字段与表列必须一一对应（加字段忘加列，运行期才在 insert 处炸）。</li>
 * </ol>
 */
public class SchemaParityTest {

    private static final String MYSQL_SQL = "z-msg/sql/schema-mysql.sql";
    private static final String H2_SQL = "z-msg/sql/schema-h2.sql";

    private static final List<Class<?>> ENTITIES = Arrays.asList(
            InAppMessage.class,
            MsgTemplateDO.class,
            MsgTemplateI18nDO.class,
            MsgTemplateVersionDO.class,
            MsgDeliveryLogDO.class,
            MsgBatchTaskDO.class,
            MsgUserPreferenceDO.class);

    private static final Pattern TABLE_START =
            Pattern.compile("^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)\\s*\\($", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_END = Pattern.compile("^\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_MYSQL =
            Pattern.compile("^\\s*(?:UNIQUE\\s+|FULLTEXT\\s+)?(?:KEY|INDEX)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern INDEX_H2 =
            Pattern.compile("CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)", Pattern.CASE_INSENSITIVE);

    /** 约束类行不是列，解析列时要跳过。 */
    private static final Set<String> NOT_A_COLUMN = new HashSet<String>(Arrays.asList(
            "PRIMARY", "UNIQUE", "KEY", "INDEX", "CONSTRAINT", "FOREIGN"));

    @Test
    public void bothDialectsDefineTheSameTables() throws IOException {
        Map<String, List<String>> mysql = parseTables(read(MYSQL_SQL));
        Map<String, List<String>> h2 = parseTables(read(H2_SQL));

        assertEquals(mysql.keySet(), h2.keySet(),
                "MySQL 与 H2 schema 的表集合不一致（只改了一份？）");
        assertEquals(ENTITIES.size(), mysql.size(),
                "表数量与实体数量不符：实体 " + ENTITIES.size() + " 张，schema " + mysql.size() + " 张");
    }

    @Test
    public void bothDialectsDefineTheSameIndexes() throws IOException {
        Set<String> mysql = parseIndexNames(read(MYSQL_SQL));
        Set<String> h2 = parseIndexNames(read(H2_SQL));

        assertFalse(mysql.isEmpty(), "索引解析结果为空说明解析器坏了，不能让这条测试假绿");
        assertEquals(mysql, h2, "MySQL 与 H2 的索引集合不一致：仅 MySQL 有 "
                + diff(mysql, h2) + "；仅 H2 有 " + diff(h2, mysql));
    }

    /**
     * 在真 H2 上执行 H2 脚本，再用 information_schema 回读 —— 证明这份脚本不只是"和另一份文本一致"，
     * 而是语法合法、表确实建成。
     */
    @Test
    public void h2ScriptActuallyExecutes() throws Exception {
        Map<String, List<String>> expected = parseTables(read(H2_SQL));
        assertFalse(expected.isEmpty(), "解析不到任何表，说明解析器或资源路径坏了");

        String url = "jdbc:h2:mem:schema_parity_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
            try (Statement st = conn.createStatement()) {
                st.execute(String.join("\n", read(H2_SQL)));
            }
            Set<String> actual = new LinkedHashSet<String>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC'")) {
                while (rs.next()) {
                    actual.add(rs.getString(1).toLowerCase());
                }
            }
            assertEquals(expected.keySet(), actual,
                    "H2 里实际建出来的表与脚本声明的不一致");
        }
    }

    @Test
    public void entityFieldsMatchDeclaredColumns() throws IOException {
        Map<String, List<String>> mysql = parseTables(read(MYSQL_SQL));
        Map<String, List<String>> h2 = parseTables(read(H2_SQL));

        for (Class<?> clazz : ENTITIES) {
            TableName tableName = clazz.getAnnotation(TableName.class);
            assertNotNull(tableName, clazz.getSimpleName() + " 缺 @TableName");
            String table = tableName.value();

            Set<String> columns = new LinkedHashSet<String>(mysql.get(table));
            List<String> h2Columns = h2.get(table);
            assertTrue(columns.size() == h2Columns.size() && columns.containsAll(h2Columns),
                    table + " 的列集合在两份方言里不一致 —— 仅 MySQL 有 " + diff(columns, new LinkedHashSet<String>(h2Columns))
                            + "；仅 H2 有 " + diff(new LinkedHashSet<String>(h2Columns), columns)
                            + "；MySQL 列序 " + columns + " / H2 列序 " + h2Columns);

            Set<String> fields = new LinkedHashSet<String>();
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                fields.add(camelToSnake(f.getName()));
            }
            assertEquals(columns, fields,
                    clazz.getSimpleName() + " 的字段与 " + table + " 的列不对应 —— 仅实体有 "
                            + diff(fields, columns) + "；仅表有 " + diff(columns, fields));
        }
    }

    // ---------------- helpers ----------------

    private static void assertNotNull(Object value, String message) {
        assertTrue(value != null, message);
    }

    private static Set<String> diff(Set<String> left, Set<String> right) {
        Set<String> out = new LinkedHashSet<String>(left);
        out.removeAll(right);
        return out;
    }

    private static String camelToSnake(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Map<String, List<String>> parseTables(List<String> lines) {
        Map<String, List<String>> tables = new LinkedHashMap<String, List<String>>();
        String current = null;
        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (current == null) {
                Matcher m = TABLE_START.matcher(line);
                if (m.matches()) {
                    current = m.group(1).toLowerCase();
                    tables.put(current, new ArrayList<String>());
                }
                continue;
            }
            if (TABLE_END.matcher(line).find()) {
                current = null;
                continue;
            }
            String first = line.split("[\\s(]+")[0].toUpperCase();
            if (NOT_A_COLUMN.contains(first)) {
                continue;
            }
            tables.get(current).add(line.split("[\\s(]+")[0].toLowerCase());
        }
        return tables;
    }

    private static Set<String> parseIndexNames(List<String> lines) {
        Set<String> names = new LinkedHashSet<String>();
        for (String raw : lines) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher mysql = INDEX_MYSQL.matcher(line);
            if (mysql.find()) {
                names.add(mysql.group(1).toLowerCase());
                continue;
            }
            Matcher h2 = INDEX_H2.matcher(line);
            if (h2.find()) {
                names.add(h2.group(1).toLowerCase());
            }
        }
        return names;
    }

    private static String stripComment(String line) {
        int idx = line.indexOf("--");
        return idx < 0 ? line : line.substring(0, idx);
    }

    private static List<String> read(String resource) throws IOException {
        InputStream in = SchemaParityTest.class.getClassLoader().getResourceAsStream(resource);
        assertTrue(in != null, "classpath 上找不到 " + resource);
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
