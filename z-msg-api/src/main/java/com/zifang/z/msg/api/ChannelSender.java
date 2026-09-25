package com.zifang.z.msg.api;

/**
 * 统一通道 Provider SPI (1.1.0)
 * <p>
 * 一个实现 = 一个 (channel, provider) 组合，例如
 * {@code channel=IM_DINGTALK, provider=robot} 对应钉钉群机器人。
 * <p>
 * 新增一个外部渠道 = 写一个本接口实现 + 一段 yml 配置，不需要改 router / gateway 源码。
 * 实现注册为 Spring Bean 后由 {@code SenderRegistry} 自动收集。
 * <p>
 * 约定：
 * <ul>
 *   <li>send 不得抛异常表达"供应商业务拒绝"（额度不足、模板不存在），必须返回
 *       {@link MessageSendResult#fail(String, String, String)}，否则上层的重试/降级逻辑失效；
 *       只有编程错误才允许抛异常。</li>
 *   <li>provider 为 mock 时必须让 {@link #isMock()} 返回 true，投递日志会记 status=3(mock)
 *       而不是成功，避免测试环境的假成功流到生产统计。</li>
 * </ul>
 */
public interface ChannelSender {

    /**
     * 通道，取值见 {@link Channels}
     */
    String channel();

    /**
     * 该通道内的 provider 标识，与 yml 中 {@code z-msg.channel.<channel>.provider} 对应
     */
    String provider();

    /**
     * 发送
     */
    MessageSendResult send(Message message);

    /**
     * 是否为 mock/录制实现（不真正外发）
     */
    default boolean isMock() {
        return false;
    }

    /**
     * 该 provider 的必要配置是否齐备；返回 false 时 router 会跳过该通道并在日志里记
     * PROVIDER_NOT_CONFIGURED，而不是抛异常打断整条 fan-out。
     */
    default boolean ready() {
        return true;
    }
}
