package com.ruoyi.system.ai;

/**
 * 大模型客户端抽象。
 *
 * 这个接口是「诊断能力可插拔」这句话的落点：业务层只认 chat / chatWithImage，
 * 后续无论是换成专业植保识别模型、本地私有化部署的模型，还是加一路兜底服务商，
 * 都只需要再写一个实现类，DiagnosisService 一行不用改。
 *
 * @author tianzhen
 */
public interface LlmClient
{
    /**
     * 当前客户端是否可用（配了 key、开关打开）。
     * 调用方应当先问这个方法，再决定是否走大模型路径。
     */
    boolean isAvailable();

    /**
     * 服务商标识，用于日志与前端展示。
     */
    String getProvider();

    /**
     * 纯文本对话。
     *
     * @param systemPrompt 系统提示词（角色设定与硬性约束）
     * @param userPrompt   用户提示词（本次任务的具体输入）
     * @return 调用结果，失败时不抛异常，通过 {@link LlmResponse#isSuccess()} 判断
     */
    LlmResponse chat(String systemPrompt, String userPrompt);

    /**
     * 带图片的多模态对话。
     *
     * 图片以字节数组传入而不是 URL —— 因为上传的图片存本地，外网大模型
     * 根本访问不到 http://127.0.0.1 的地址，必须由服务端读出来转 base64 内联发送。
     *
     * @param imageBytes   图片字节
     * @param mimeType     图片 MIME 类型，如 image/jpeg
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 调用结果
     */
    LlmResponse chatWithImage(byte[] imageBytes, String mimeType, String systemPrompt, String userPrompt);
}
