package com.ruoyi.system.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上传图片的 MD5。
 *
 * <p>预置样张映射（路线 A 保底）靠它把「这张照片」和「登记过的标准结论」对上。
 * 算在服务端、而不是让浏览器算完传过来，有两个原因：
 * 一是客户端的值可以随便填，等于把「命中哪条结论」交给了调用方；
 * 二是换任何客户端上传都能自动登记，不必每个端各写一遍。
 *
 * <p>取不到文件时一律返回 null 而不是抛异常：算不出哈希只是「这条路走不通」，
 * 后面还有大模型和知识库两条路，不该让一条保底链路把整个诊断请求打断。
 *
 * <p>「访问地址 → 磁盘文件」这一步交给 {@link UploadedImage} 统一处理。
 * 曾经它在这里自己实现过一份，与读图那边各写一套 —— 两套一旦漂移，
 * 哈希对着 A 文件算、诊断读的是 B 文件，界面上完全看不出来。
 *
 * @author tianzhen
 */
public final class ImageHash
{
    private static final Logger log = LoggerFactory.getLogger(ImageHash.class);

    private static final int BUFFER_SIZE = 8192;

    private ImageHash()
    {
    }

    /**
     * 按上传文件的访问地址算 MD5。
     *
     * @param imageUrl 形如 /profile/upload/2026/09/12/xxx.png，可以是带域名的完整地址
     * @return 小写十六进制 MD5；地址为空、文件不存在或读取失败时返回 null
     */
    public static String ofUploadedFile(String imageUrl)
    {
        Path file = UploadedImage.resolveToFile(imageUrl);
        if (file == null)
        {
            return null;
        }
        try (InputStream in = Files.newInputStream(file))
        {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1)
            {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        }
        catch (IOException e)
        {
            log.warn("读取待诊断图片失败，本次不做预置样张匹配。文件={}，原因={}", file, e.getMessage());
            return null;
        }
        catch (NoSuchAlgorithmException e)
        {
            // JDK 必然带 MD5，走不到这里
            throw new IllegalStateException("当前 JDK 不支持 MD5", e);
        }
    }

    private static String toHex(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
