package com.chaos.core.digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 摘要工具；调用方决定参与计算的原始内容。
 */
public final class Sha256 {

    private Sha256() {
    }

    /**
     * 为流式上传等分段计算创建独立的摘要实例。
     *
     * @return 尚未接收数据的 SHA-256 摘要，不跨线程共享
     */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", ex);
        }
    }

    /**
     * 按 UTF-8 文本计算摘要。
     *
     * @param text 待计算文本
     * @return 64 位小写十六进制文本
     */
    public static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 按原始字节计算摘要。
     *
     * @param bytes 文件内容或已按业务规则序列化的字节
     * @return 64 位小写十六进制文本
     */
    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(newDigest().digest(bytes));
    }
}
