package com.ruoyi.system.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;

/**
 * 本地上传图片的定位与读取。
 *
 * <p>把「图片访问地址 → 磁盘文件」这件事单独放在一处，是因为它必须<b>只有一个实现</b>：
 * 调用方有三处（图片 MD5 预置匹配、诊断读图、照片自动描述），各写一份迟早会漂移，
 * 而漂移的后果是静默的 —— 读不到图不会报错，只会安静地退回「本次无图」，
 * 界面上看起来一切正常，结论却少了一半依据。
 *
 * <p>两处必须照顾到的现实情况：
 *
 * <ul>
 *   <li><b>地址可能是绝对 URL。</b>上传接口返回体里同时有 url 和 fileName，
 *       前端存的是相对路径，而脚本播种的演示数据存的是
 *       {@code http://127.0.0.1:18080/profile/upload/...} 这种完整地址。
 *       两者都得认，所以按 {@code /profile/} 在串里的位置切，而不是判断前缀。</li>
 *   <li><b>地址来自请求体，也就是来自调用方。</b>定位后要校验结果仍在
 *       {@code ruoyi.profile} 目录内，否则一个 {@code /profile/../../} 开头的地址
 *       就能让服务端读到上传目录之外的任意文件。</li>
 * </ul>
 *
 * @author tianzhen
 */
public final class UploadedImage
{
    private static final Logger log = LoggerFactory.getLogger(UploadedImage.class);

    /** 上传文件的对外访问前缀，ResourceConfig 把 /profile/** 映射到 ruoyi.profile 目录 */
    private static final String PROFILE_PREFIX = Constants.RESOURCE_PREFIX;

    private UploadedImage()
    {
    }

    /**
     * 把访问地址还原成磁盘路径。
     *
     * @param imageUrl 形如 /profile/upload/2026/09/14/xxx.png，可以是带域名的完整地址
     * @return 磁盘路径；地址不合法、不在上传目录内或文件不存在时返回 null
     */
    public static Path resolveToFile(String imageUrl)
    {
        if (StringUtils.isBlank(imageUrl))
        {
            return null;
        }

        String url = imageUrl.trim().replace('\\', '/');
        int index = url.indexOf(PROFILE_PREFIX + "/");
        if (index < 0)
        {
            // 不是本站上传的文件（外链、手工填的地址等），无法定位到本地文件
            return null;
        }

        String relative = url.substring(index + PROFILE_PREFIX.length());
        while (relative.startsWith("/"))
        {
            relative = relative.substring(1);
        }
        // 去掉 ?a=1#x 这类尾巴，它们不属于文件名
        int cut = relative.indexOf('?');
        if (cut >= 0)
        {
            relative = relative.substring(0, cut);
        }
        cut = relative.indexOf('#');
        if (cut >= 0)
        {
            relative = relative.substring(0, cut);
        }
        if (relative.isEmpty())
        {
            return null;
        }

        String profile = RuoYiConfig.getProfile();
        if (StringUtils.isBlank(profile))
        {
            return null;
        }

        Path root = Paths.get(profile).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root))
        {
            log.warn("图片地址越出上传目录，已忽略：{}", imageUrl);
            return null;
        }
        if (!Files.isRegularFile(target))
        {
            log.debug("图片文件不存在：{}", target);
            return null;
        }
        return target;
    }

    /**
     * 把上传图片读成字节。
     *
     * <p>取不到就返回 null 而不是抛异常：读图失败只等于「这条路走不通」，
     * 后面还有预置样张、知识库降级几条路，不该让取图把整个请求打断。
     *
     * @param imageUrl 图片访问地址
     * @return 图片字节；无法定位或读取失败时返回 null
     */
    public static byte[] read(String imageUrl)
    {
        Path file = resolveToFile(imageUrl);
        if (file == null)
        {
            log.warn("巡田图片无法定位，本次按无图处理：{}", imageUrl);
            return null;
        }
        try
        {
            return Files.readAllBytes(file);
        }
        catch (Exception e)
        {
            log.warn("读取巡田图片失败，本次按无图处理：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 按扩展名猜图片 MIME 类型。
     *
     * <p>猜错会导致部分服务商拒收，但它们对不认识的类型一律按 image/jpeg 处理，
     * 所以兜底给 jpeg 而不是报错。
     */
    public static String mimeType(String imageUrl)
    {
        String lower = StringUtils.defaultString(imageUrl).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
