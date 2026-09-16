package com.zifang.z.msg.core.template;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zifang.z.msg.core.config.MessageProperties;
import com.zifang.z.msg.core.domain.entity.MsgTemplateDO;
import com.zifang.z.msg.core.domain.entity.MsgTemplateI18nDO;
import com.zifang.z.msg.core.domain.mapper.MsgTemplateI18nMapper;
import com.zifang.z.msg.core.domain.mapper.MsgTemplateMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板渲染器 (Phase 1-3 重构)
 * <p>
 * 模板查找优先级:
 * 1. DB z_msg_template_i18n (locale 匹配)
 * 2. DB z_msg_template (主表)
 * 3. yml z-msg.template.{bizType} (历史兼容)
 * 4. 内置默认 (REGISTER/LOGIN/RESET_PWD)
 * <p>
 * 占位符: ${varName}。
 * 缓存策略: 启动时一次性预热 + 写操作时 invalidate。
 * <p>
 * Phase 3 多语言: 传 locale="zh-CN" 时优先匹配 i18n 表;不传则取主表。
 */
@Component
public class MessageTemplateEngine {

    private static final Logger log = LogManager.getLogger(MessageTemplateEngine.class);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");

    private final MessageProperties properties;
    private final MsgTemplateMapper templateMapper;
    private final MsgTemplateI18nMapper i18nMapper;

    /**
     * 内存缓存: bizType+channel -> (subject, content)
     */
    private final Map<String, TemplateEntry> cache = new ConcurrentHashMap<>();

    /**
     * yml 自定义 (历史兼容)
     */
    private final Map<String, String> ymlTemplates = new ConcurrentHashMap<>();

    public MessageTemplateEngine(MessageProperties properties,
                                 MsgTemplateMapper templateMapper,
                                 MsgTemplateI18nMapper i18nMapper) {
        this.properties = properties;
        this.templateMapper = templateMapper;
        this.i18nMapper = i18nMapper;
    }

    @PostConstruct
    public void init() {
        // 内置默认
        ymlTemplates.put("REGISTER", "【z-opc】您的注册验证码是:${code},5 分钟内有效。");
        ymlTemplates.put("LOGIN", "【z-opc】您的登录验证码是:${code},5 分钟内有效。");
        ymlTemplates.put("RESET_PWD", "【z-opc】您正在重置密码,验证码:${code},5 分钟内有效。");
        // yml 覆盖
        if (properties.getTemplate() != null) {
            ymlTemplates.putAll(properties.getTemplate());
        }
        // DB 预热
        refreshCache();
        log.info("[MsgTemplate] 初始化完成: db cache={}, yml={}", cache.size(), ymlTemplates.size());
    }

    /**
     * 从 DB 全量刷新缓存 (启动 + 写后失效用)
     */
    public void refreshCache() {
        LambdaQueryWrapper<MsgTemplateDO> qw = new LambdaQueryWrapper<>();
        qw.eq(MsgTemplateDO::getStatus, 1);
        List<MsgTemplateDO> list = templateMapper.selectList(qw);
        Map<String, TemplateEntry> fresh = new ConcurrentHashMap<>();
        for (MsgTemplateDO t : list) {
            String key = t.getBizType() + "|" + t.getChannel();
            fresh.put(key, new TemplateEntry(t.getId(), t.getSubject(), t.getContent(), t.getVersion()));
        }
        cache.clear();
        cache.putAll(fresh);
        log.info("[MsgTemplate] DB cache refreshed: {} entries", cache.size());
    }

    /**
     * 渲染 SMS / IM / PUSH (无 subject)
     */
    public String render(String bizType, Map<String, String> params) {
        return render(bizType, "SMS", params);
    }

    /**
     * 渲染指定 channel 的 content
     */
    public String render(String bizType, String channel, Map<String, String> params) {
        TemplateEntry t = lookup(bizType, channel, null);
        return doRender(t == null ? null : t.content, params);
    }

    /**
     * 渲染 EMAIL 的 body
     */
    public String renderEmailBody(String bizType, Map<String, String> params) {
        return render(bizType, "EMAIL", params);
    }

    /**
     * 渲染 EMAIL subject (无 params)
     */
    public String renderEmailSubject(String bizType) {
        TemplateEntry t = lookup(bizType, "EMAIL", null);
        if (t != null && t.subject != null && !t.subject.isEmpty()) {
            return t.subject;
        }
        return "z-opc 通知";
    }

    /**
     * 通用渲染 (Phase 3): 支持 locale
     */
    public RenderedTemplate renderWithLocale(String bizType, String channel, String locale, Map<String, String> params) {
        TemplateEntry t = lookup(bizType, channel, locale);
        if (t == null) {
            return new RenderedTemplate("z-opc 通知", "【z-opc】您有一条 ${bizType} 通知");
        }
        String subject = doRender(t.subject, params);
        String content = doRender(t.content, params);
        return new RenderedTemplate(subject, content);
    }

    /**
     * 模板查找: i18n(locale) > db main > yml > default
     */
    public TemplateEntry lookup(String bizType, String channel, String locale) {
        // 1. i18n
        if (locale != null && !locale.isEmpty()) {
            LambdaQueryWrapper<MsgTemplateDO> mainQw = new LambdaQueryWrapper<>();
            mainQw.eq(MsgTemplateDO::getBizType, bizType).eq(MsgTemplateDO::getChannel, channel).eq(MsgTemplateDO::getStatus, 1).last("LIMIT 1");
            MsgTemplateDO main = templateMapper.selectOne(mainQw);
            if (main != null) {
                LambdaQueryWrapper<MsgTemplateI18nDO> iQw = new LambdaQueryWrapper<>();
                iQw.eq(MsgTemplateI18nDO::getTemplateId, main.getId()).eq(MsgTemplateI18nDO::getLocale, locale).last("LIMIT 1");
                MsgTemplateI18nDO i18n = i18nMapper.selectOne(iQw);
                if (i18n != null) {
                    return new TemplateEntry(main.getId(), i18n.getSubject(), i18n.getContent(), main.getVersion());
                }
            }
        }
        // 2. DB cache
        String key = bizType + "|" + channel;
        TemplateEntry cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        // 3. yml
        String yml = ymlTemplates.get(bizType);
        if (yml != null) {
            return new TemplateEntry(null, null, yml, 0);
        }
        // 4. default
        return new TemplateEntry(null, "z-opc 通知", "【z-opc】您的验证码是:${code}", 0);
    }

    private String doRender(String template, Map<String, String> params) {
        if (template == null) {
            return "";
        }

        if (params == null || params.isEmpty()) {
            return template;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String value = params.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static class TemplateEntry {
        public final Long id;
        public final String subject;
        public final String content;
        public final Integer version;

        TemplateEntry(Long id, String subject, String content, Integer version) {
            this.id = id;
            this.subject = subject;
            this.content = content;
            this.version = version;
        }
    }

    public static class RenderedTemplate {
        private final String subject;
        private final String content;

        public RenderedTemplate(String subject, String content) {
            this.subject = subject;
            this.content = content;
        }

        public String getSubject() {
            return subject;
        }

        public String getContent() {
            return content;
        }
    }
}
