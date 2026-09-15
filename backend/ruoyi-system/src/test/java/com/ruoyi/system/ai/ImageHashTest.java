package com.ruoyi.system.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ruoyi.common.config.RuoYiConfig;

/**
 * 图片 MD5 计算。
 *
 * 这个类的价值全在边界上：地址为空、文件不存在、以及来自请求体的路径想要跳出上传目录时，
 * 都必须安静地返回 null，而不是抛异常或读到别处去。
 */
class ImageHashTest
{
    @TempDir
    Path uploadRoot;

    private String originalProfile;

    @BeforeEach
    void setUp()
    {
        originalProfile = RuoYiConfig.getProfile();
        new RuoYiConfig().setProfile(uploadRoot.toString());
    }

    @AfterEach
    void tearDown()
    {
        new RuoYiConfig().setProfile(originalProfile);
    }

    private String write(String relative, String content) throws IOException
    {
        Path file = uploadRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return "/profile/" + relative;
    }

    @Test
    @DisplayName("算出的确实是 MD5：用标准向量比对")
    void computesStandardMd5() throws IOException
    {
        // MD5("abc") 是公认标准值，用它确认算的是 MD5 而不是别的摘要、也不是换个编码
        String url = write("upload/2026/09/12/leaf.png", "abc");

        assertEquals("900150983cd24fb0d6963f7d28e17f72", ImageHash.ofUploadedFile(url),
                "算法、编码或大小写任一处理错了，这个值都对不上");
    }

    @Test
    @DisplayName("内容相同的两张图得到同一个 MD5，内容不同则不同")
    void hashesByContentNotByPath() throws IOException
    {
        String a = write("upload/a.png", "same-bytes");
        String b = write("upload/b.png", "same-bytes");
        String c = write("upload/c.png", "other-bytes");

        assertEquals(ImageHash.ofUploadedFile(a), ImageHash.ofUploadedFile(b),
                "同一张图换个文件名，仍然要能命中同一条预置结论");
        assertNotNull(ImageHash.ofUploadedFile(c));
        assertNotEquals(ImageHash.ofUploadedFile(a), ImageHash.ofUploadedFile(c));
    }

    @Test
    @DisplayName("地址为空或不是本站上传的地址时返回 null")
    void returnsNullForUnusableUrl() throws IOException
    {
        write("upload/leaf.png", "x");
        assertNull(ImageHash.ofUploadedFile(null));
        assertNull(ImageHash.ofUploadedFile(""));
        assertNull(ImageHash.ofUploadedFile("   "));
        // 外链图片定位不到本地文件，不该猜
        assertNull(ImageHash.ofUploadedFile("https://example.com/leaf.png"));
        assertNull(ImageHash.ofUploadedFile("/some/other/path/leaf.png"));
    }

    @Test
    @DisplayName("文件不存在时返回 null 而不是抛异常")
    void returnsNullWhenFileMissing()
    {
        assertNull(ImageHash.ofUploadedFile("/profile/upload/2026/09/12/not-there.png"));
    }

    @Test
    @DisplayName("带域名或查询串的地址也能正确定位")
    void toleratesFullUrlAndQueryString() throws IOException
    {
        String url = write("upload/leaf.png", "tianzhen");
        String plain = ImageHash.ofUploadedFile(url);

        assertEquals(plain, ImageHash.ofUploadedFile("http://127.0.0.1:18080" + url));
        assertEquals(plain, ImageHash.ofUploadedFile(url + "?v=2"));
        assertEquals(plain, ImageHash.ofUploadedFile(url + "#frag"));
    }

    @Test
    @DisplayName("想要跳出上传目录的地址被拒绝")
    void rejectsPathTraversal() throws IOException
    {
        // 上传目录之外放一个「机密文件」，确认它读不到
        Path outside = uploadRoot.getParent().resolve("outside-secret.txt");
        Files.write(outside, "secret".getBytes(StandardCharsets.UTF_8));
        try
        {
            assertNull(ImageHash.ofUploadedFile("/profile/../outside-secret.txt"),
                    "越出上传目录的地址必须被拒绝：imageUrl 来自请求体，不能拿来读任意文件");
            assertNull(ImageHash.ofUploadedFile("/profile/upload/../../outside-secret.txt"));
        }
        finally
        {
            Files.deleteIfExists(outside);
        }
    }
}
