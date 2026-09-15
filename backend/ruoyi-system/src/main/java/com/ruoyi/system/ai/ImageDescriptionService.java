package com.ruoyi.system.ai;

import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.domain.TzImagePreset;

/**
 * 照片自动描述。
 *
 * <p>巡田登记的第一个动作是拍照，第二件事才是写字。让基层农技员蹲在树下对着
 * 手机敲三行症状描述，是整套流程里最容易被跳过的一步 —— 而它偏偏又是知识库
 * 检索的唯一依据。所以这里把顺序倒过来：<b>照片必填，描述由系统看照片先写一版，
 * 用户只在上面改。</b>
 *
 * <p>两级链路，与 {@link DiagnosisService} 同一套纪律：
 *
 * <ol>
 *   <li><b>预置样张登记的描述</b> —— 图片 MD5 命中登记过的样张，直接用登记时
 *       人工写好的那段观察记录。演示用的三张样图走的都是这条，因此
 *       <b>不配 API Key 也能把描述填出来</b>，功能在离线状态下依然完整。</li>
 *   <li><b>多模态模型读图</b> —— 未命中预置样张且配了 key 时，交给视觉模型。</li>
 *   <li>两条都不通 —— 明确返回「没能自动识别」并说明原因，把输入权交还用户，
 *       而不是塞一段看起来像描述的文字进去。描述会被拿去检索知识库、写进巡田报告，
 *       编出来的东西会顺着链路一路污染到结论。</li>
 * </ol>
 *
 * <p>这里刻意<b>不下结论</b>：提示词硬性禁止出现病名与用药。拍照就出病名的话，
 * 「描述 → 诊断」的先后顺序就没有意义了，用户会以为系统已经诊断过，报告里
 * 也会混进一段来路不明的结论。
 *
 * <p>AI 调用日志的 {@code biz_type} 记为 {@code describe}，{@code biz_id} 记的是
 * 图片 MD5（此时巡田记录还没保存，没有 recordId 可用）。
 *
 * @author tianzhen
 */
@Service
public class ImageDescriptionService
{
    private static final Logger log = LoggerFactory.getLogger(ImageDescriptionService.class);

    /** 描述入库长度上限，与 tz_scouting_record.symptom_text 的 1000 对齐后留足余量 */
    private static final int DESCRIPTION_MAX = 500;

    @Autowired
    private PresetDiagnosisResolver presetResolver;

    @Autowired
    private PromptBuilder promptBuilder;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private AiCallLogRecorder callLogRecorder;

    @Autowired
    private AiProperties properties;

    /**
     * 为一张已上传的照片生成症状描述。
     *
     * @param imageUrl  上传接口返回的图片地址（/profile/...）
     * @param cropType  作物类型，可空，默认按柑橘
     * @param plantPart 用户已选的拍摄部位，可空
     * @return 描述结果；未成功时 {@code success=false} 并带上来由
     */
    public ImageDescriptionResult describe(String imageUrl, String cropType, String plantPart)
    {
        if (StringUtils.isBlank(imageUrl))
        {
            throw new ServiceException("请先上传现场照片。");
        }

        ImageDescriptionResult result = new ImageDescriptionResult();
        result.setImageUrl(imageUrl);
        result.setDisclaimer(properties.getDisclaimer());

        // 哈希只算一次：预置匹配和调用日志都要用，而读文件算 MD5 是这条链路上
        // 唯一确定会发生的磁盘 IO
        String imageHash = ImageHash.ofUploadedFile(imageUrl);

        // ---- 第 1 级：预置样张登记过的描述 ----
        if (tryPreset(imageHash, result))
        {
            callLogRecorder.record("describe", imageHash, null, false,
                    "命中预置样张登记的观察记录，未调用大模型");
            return result;
        }

        // ---- 第 2 级：多模态模型读图 ----
        String degradeReason;
        if (!llmClient.isAvailable())
        {
            degradeReason = "未配置大模型 API Key（设置环境变量 TZ_AI_API_KEY 后自动启用）";
        }
        else
        {
            degradeReason = tryLlm(imageUrl, cropType, plantPart, imageHash, result);
            if (degradeReason == null)
            {
                return result;
            }
        }

        // ---- 第 3 级：如实说没识别出来，把输入权还给用户 ----
        log.info("照片未能自动生成描述：{}", degradeReason);
        result.setSuccess(false);
        result.setDescription(null);
        result.setDegradeReason(degradeReason);
        return result;
    }

    // ------------------------------------------------------------------ 两级链路

    /**
     * 命中预置样张且登记过症状描述时直接采用。
     *
     * <p>登记过哈希但没登记描述时不返回 true —— 那种情况下还要继续往大模型走，
     * 否则一次「只登记了结论、忘了登记观察记录」的疏忽会把整条描述链路堵死。
     */
    private boolean tryPreset(String imageHash, ImageDescriptionResult result)
    {
        TzImagePreset preset = presetResolver.resolve(imageHash);
        if (preset == null || StringUtils.isBlank(preset.getSymptomText()))
        {
            return false;
        }

        result.setSuccess(true);
        result.setDescription(trim(preset.getSymptomText()));
        result.setSource("preset");
        result.setSourceText("来自预置样张登记的描述（" + StringUtils.defaultIfBlank(preset.getImageName(), "演示样张")
                + "），未经过大模型识别，可自行修改");
        return true;
    }

    /**
     * 走多模态模型。返回 null 表示成功，非空字符串表示失败原因。
     */
    private String tryLlm(String imageUrl, String cropType, String plantPart,
                          String imageHash, ImageDescriptionResult result)
    {
        byte[] imageBytes = UploadedImage.read(imageUrl);
        if (imageBytes == null || imageBytes.length == 0)
        {
            String reason = "读不到这张照片的文件（可能已被清理），无法识别";
            callLogRecorder.record("describe", imageHash, null, true, reason);
            return reason;
        }

        LlmResponse response = llmClient.chatWithImage(imageBytes, UploadedImage.mimeType(imageUrl),
                promptBuilder.describeSystemPrompt(),
                promptBuilder.describeUserPrompt(cropType, plantPart));

        result.setProvider(response.getProvider());
        result.setModel(response.getModel());
        result.setLatencyMs(response.getLatencyMs());

        if (!response.isSuccess())
        {
            String reason = "大模型调用失败：" + StringUtils.defaultIfBlank(response.getErrorMsg(), "未知错误");
            callLogRecorder.record("describe", imageHash, response, true, reason);
            return reason;
        }

        Map<String, Object> parsed = LlmJson.parse(response.getContent());
        String description = LlmJson.str(parsed, "description", null);
        if (StringUtils.isBlank(description))
        {
            String reason = "大模型没有返回可用的描述";
            callLogRecorder.record("describe", imageHash, response, true, reason);
            return reason;
        }

        // 让模型自己判断照片够不够清楚。它说看不清就宁可不用 —— 一张糊照片能编出
        // 一段像模像样的症状描述，而那段描述会一路带进检索和报告。
        if (isMarkedUnobservable(parsed))
        {
            String reason = "照片清晰度不足，或画面里没有可辨认的植物特征，模型无法作出描述";
            callLogRecorder.record("describe", imageHash, response, true, reason);
            return reason;
        }

        result.setSuccess(true);
        result.setDescription(trim(description));
        result.setSource("llm");
        result.setSourceText("AI 识别照片自动生成（" + response.getModel() + "，耗时 "
                + response.getLatencyMs() + " ms），可自行修改");
        callLogRecorder.record("describe", imageHash, response, false, null);
        return null;
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 读模型返回的 observable 标记。
     *
     * <p>字段缺省时按「可用」处理：少一个字段就让用户自己写字，属于因为提示词
     * 的一点小偏差而白白丢掉整次识别。反过来，模型明确说了看不清才是有效拒答。
     */
    private boolean isMarkedUnobservable(Map<String, Object> parsed)
    {
        Object value = parsed == null ? null : parsed.get("observable");
        if (value == null)
        {
            return false;
        }
        if (value instanceof Boolean)
        {
            return !((Boolean) value);
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "false".equals(text) || "no".equals(text) || "否".equals(text);
    }

    /** 压掉换行与多余空白并截断：描述要占一行输入框，不是一段排版稿 */
    private String trim(String text)
    {
        String flat = StringUtils.defaultString(text).replaceAll("\\s+", " ").trim();
        return flat.length() > DESCRIPTION_MAX ? flat.substring(0, DESCRIPTION_MAX) : flat;
    }
}
